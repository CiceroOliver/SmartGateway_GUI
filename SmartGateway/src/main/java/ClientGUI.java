package main.java;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.protobuf.InvalidProtocolBufferException;
import main.java.MessageOuterClass.Message;

public class ClientGUI {
    private Map<String, Set<String>> sensorCommands = new HashMap<>(); // Armazena sensorId e seus comandos
    private SocketChannel clientChannel;
    private JTextArea receivedLogArea;
    private JTextArea sentMessagesLogArea; // Novo log para mensagens enviadas
    private JComboBox<String> sensorDropdown;
    private JComboBox<String> commandDropdown;
    
    //private JTextField commandField;
    private JTextField payloadField;
    private ExecutorService executorService;

    public ClientGUI() throws IOException {
        clientChannel = SocketChannel.open();
        clientChannel.connect(new java.net.InetSocketAddress("127.0.0.1", 4000));
        executorService = Executors.newFixedThreadPool(3); // 3 threads, uma para enviar e outra para receber
    }

    public static void main(String[] args) {
        try {
            new ClientGUI().createAndShowGUI();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Client GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 900);
    
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
    
        // Title
        JLabel titleLabel = new JLabel("Client Control Panel");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(titleLabel, gbc);
    
        // Button to list sensors
        JButton listButton = new JButton("Listar Sensores");
        listButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    listSensors();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(listButton, gbc);
    
        // Dropdown for selecting a sensor
        sensorDropdown = new JComboBox<>();
        sensorDropdown.setEditable(false);
        sensorDropdown.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedSensor = (String) sensorDropdown.getSelectedItem();
                if(selectedSensor!=null)
                    sentMessagesLogArea.append("Sensor selecionado: " + selectedSensor + "\n");
    
                // Solicita os comandos para o sensor selecionado
                if (selectedSensor != null && !selectedSensor.isEmpty()) {
                    try {
                        requestSensorCommands(selectedSensor);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                
            }
        });
        gbc.gridx = 1;
        panel.add(sensorDropdown, gbc);
    
        // Dropdown for selecting a command
        commandDropdown = new JComboBox<>();
        commandDropdown.setEditable(false);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Comando:"), gbc);
        gbc.gridx = 1;
        panel.add(commandDropdown, gbc);
    
        // Payload label and field
        JLabel payloadLabel = new JLabel("Payload:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(payloadLabel, gbc);
    
        payloadField = new JTextField(15);
        gbc.gridx = 1;
        panel.add(payloadField, gbc);
    
        // Send message button
        JButton sendButton = new JButton("Enviar Mensagem");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    sendMessage();
                    sensorDropdown.setSelectedItem(null);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(sendButton, gbc);
    
        // Log area
        receivedLogArea = new JTextArea(15, 40);
        receivedLogArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(receivedLogArea);
        gbc.gridx = 0;
        gbc.gridy = 6; // Aumentar o valor de y para deslocar o log do servidor para baixo
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(logScroll, gbc);
    
        // Clear log button for server messages
        JButton clearButton = new JButton("Limpar Log Server");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                receivedLogArea.setText(""); // Limpa o conteúdo do log
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 7; // Colocando o botão de limpeza abaixo do log
        panel.add(clearButton, gbc);
    
        // Log for received messages label
        JLabel receivedLogLabel = new JLabel("Log do Servidor:");
        gbc.gridx = 0;
        gbc.gridy = 5; // Aumentar o valor de y para deslocar o rótulo do log para baixo
        panel.add(receivedLogLabel, gbc);
    
        // Novo Log: Mensagens Enviadas
        JLabel sentLogLabel = new JLabel("Log de Mensagens Enviadas:");
        gbc.gridx = 1;
        gbc.gridy = 5; // Ajustado para manter o log lado a lado
        panel.add(sentLogLabel, gbc);
    
        // Sent messages log area
        sentMessagesLogArea = new JTextArea(15, 40);
        sentMessagesLogArea.setEditable(false);
        JScrollPane sentLogScroll = new JScrollPane(sentMessagesLogArea);
        gbc.gridx = 1;
        gbc.gridy = 6; // Aumentar o valor de y para deslocar o log das mensagens enviadas para baixo
        panel.add(sentLogScroll, gbc);
    
        // Clear sent messages log button
        JButton clearSentMessagesButton = new JButton("Limpar Log Enviado");
        clearSentMessagesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sentMessagesLogArea.setText(""); // Limpa o conteúdo do log das mensagens enviadas
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 7; // Colocando o botão de limpeza abaixo do log de mensagens enviadas
        panel.add(clearSentMessagesButton, gbc);
    
        frame.getContentPane().add(panel);
        frame.setVisible(true);
    
        // Start the listener thread
        startListenerThread();
    }
    
    


    private void listSensors() throws IOException {
        sendMessageToServer("Gateway", "listar", "null");
    }

    private void requestSensorCommands(String sensorId) throws IOException {
        sendMessageToServer(sensorId, "listar comandos", "null");
    }

    private void sendMessage() throws InvalidProtocolBufferException, IOException {
        String sensorId = (String) sensorDropdown.getSelectedItem();
        String comando = (String) commandDropdown.getSelectedItem();
        String payload = payloadField.getText();
        String dummy = payloadField.getText();

        if (sensorId != null && comando != null && !comando.isEmpty()) {
            // Se o payload estiver vazio, envia a string "nulo"
            
            if (payload.isEmpty()) {
                payload = "nulo";

            }

            Message message = Message.newBuilder()
                    .setSensorId(sensorId)
                    .setComando(comando)
                    .setStatus(false)
                    .setPayload(payload)
                    .build();

            byte[] messageBytes = message.toByteArray();
            ByteBuffer writeBuffer = ByteBuffer.allocate(4 + messageBytes.length);
            writeBuffer.putInt(messageBytes.length);
            writeBuffer.put(messageBytes);
            writeBuffer.flip();

            // Envia a mensagem para o servidor
            executorService.submit(() -> {
                try {
                    while (writeBuffer.hasRemaining()) {
                        clientChannel.write(writeBuffer);
                    }
                    if (dummy.isEmpty()) {
                        sentMessagesLogArea.append("Mensagem enviada para o servidor: \n" + message.getSensorId() + "\n" + message.getComando() + "\n");
                    }else{
                        sentMessagesLogArea.append("Mensagem enviada para o servidor: \n" + message.getSensorId() + "\n" + message.getComando() + "\n" + message.getPayload() + "\n");
                    }

                    payloadField.setText("");
                } catch (IOException e) {
                    sentMessagesLogArea.append("Erro ao enviar a mensagem: " + e.getMessage() + "\n");
                }
            });
        } else {
            sentMessagesLogArea.append("Por favor, preencha todos os campos antes de enviar.\n");
        }
    }

    private void startListenerThread() {
        executorService.submit(() -> {
            try {
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                while (!Thread.currentThread().isInterrupted()) {
                    readBuffer.clear();
                    if (clientChannel.read(readBuffer) >= 4) {
                        readBuffer.flip();
                        int messageLength = readBuffer.getInt();
                        byte[] data = new byte[messageLength];
                        readBuffer.get(data);

                        Message message = Message.parseFrom(data);
                        String comando = message.getComando();
                        String payload = message.getPayload();

                        switch (comando) {
                            case "listar":
                                handleSensorList(payload);
                                break;
                            case "listar comandos":
                                handleCommandList(payload);
                                break;
                            default:
                                // Exibe as outras mensagens quaisquer
                                receivedLogArea.append("Sensor ID: " + message.getSensorId() + "\n");
                                if(message.getStatus()){
                                    receivedLogArea.append("Status: Ligado" + "\n");
                                }else{
                                    receivedLogArea.append("Status: Desligado" + "\n");
                                }
                                //logArea.append("Comando: " + message.getComando() + "\n");
                                receivedLogArea.append("Payload: " + message.getPayload() + "\n");
                                receivedLogArea.append("-----------------------------------------------------------------------------------------------\n");
                                break;
                            
                        }
                    }
                }
            } catch (IOException e) {
                receivedLogArea.append("Erro na conexão: " + e.getMessage() + "\n");
            }
        });
    }

    // Atualiza o dropdown de sensores
    private void handleSensorList(String payload) {
        SwingUtilities.invokeLater(() -> {
            String[] sensors = payload.split(",");
            DefaultComboBoxModel<String> sensorModel = new DefaultComboBoxModel<>(sensors);
            sensorDropdown.setModel(sensorModel);
            //receivedLogArea.append("Sensores atualizados: " + Arrays.toString(sensors) + "\n");
        });
    }

    // Atualiza o dropdown de comandos
    private void handleCommandList(String payload) {
        SwingUtilities.invokeLater(() -> {
            String selectedSensor = (String) sensorDropdown.getSelectedItem();
            if (selectedSensor != null && !selectedSensor.isEmpty()) {
                // Atualiza os comandos específicos do sensor selecionado
                String[] commands = payload.split(",");
                sensorCommands.put(selectedSensor, new HashSet<>(Arrays.asList(commands)));
                // Atualiza o dropdown de comandos
                DefaultComboBoxModel<String> commandModel = new DefaultComboBoxModel<>(commands);
                commandDropdown.setModel(commandModel);

                //sentMessagesLogArea.append("Comandos atualizados para o sensor '" + selectedSensor + "': " + Arrays.toString(commands) + "\n");
            } else {
                sentMessagesLogArea.append("Nenhum sensor selecionado para atualizar comandos.\n");
            }
        });
    }

    // Método auxiliar para enviar mensagem para o servidor
    private void sendMessageToServer(String sensorId, String comando, String payload) throws IOException {
        Message message = Message.newBuilder()
                .setSensorId(sensorId)
                .setComando(comando)
                .setStatus(false) // Supondo que o status seja false
                .setPayload(payload)
                .build();

        byte[] messageBytes = message.toByteArray();
        ByteBuffer writeBuffer = ByteBuffer.allocate(4 + messageBytes.length);
        writeBuffer.putInt(messageBytes.length);
        writeBuffer.put(messageBytes);
        writeBuffer.flip();

        executorService.submit(() -> {
            try {
                while (writeBuffer.hasRemaining()) {
                    clientChannel.write(writeBuffer);
                }
                if(!(comando.equals("listar") || comando.equals("listar comandos"))){
                    sentMessagesLogArea.append("Mensagem '" + comando + "' enviada para o servidor.\n");
                }
            } catch (IOException e) {
                sentMessagesLogArea.append("Erro ao enviar a mensagem '" + comando + "': " + e.getMessage() + "\n");
            }
        });
    }
}
