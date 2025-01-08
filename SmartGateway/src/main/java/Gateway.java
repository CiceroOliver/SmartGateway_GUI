package main.java;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.protobuf.InvalidProtocolBufferException;

import main.java.MessageOuterClass.Message; // Importação da classe Message gerada pelo Protobuf

public class Gateway {
    public static final int CLIENT_PORT = 4000; // Porta para o Client
    public static final int SENSOR_PORT = 5000; // Porta para os Sensores
    public static final String HOSTNAME = "0.0.0.0";
    public static final String MULTICAST_GROUP = "230.0.0.0";
    public static final int MULTICAST_PORT = 6000; // Porta para a comunicação multicast
    private volatile int sufix = 1;

    private ServerSocketChannel sensorServerChannel;
    private ServerSocketChannel clientServerChannel;

    private List<SocketChannel> sensors = Collections.synchronizedList(new ArrayList<>());
    private List<SocketChannel> clients = Collections.synchronizedList(new ArrayList<>());
    private Map<String, SocketChannel> sensorMap = new ConcurrentHashMap<>(); // Mapa de sensores sincronizado

    private ExecutorService sensorThreadPool = Executors.newFixedThreadPool(10); // Até 10 sensores simultâneos

    public Gateway() throws IOException {
        // Configura o servidor para sensores
        sensorServerChannel = ServerSocketChannel.open();
        sensorServerChannel.configureBlocking(true);
        sensorServerChannel.bind(new InetSocketAddress(HOSTNAME, SENSOR_PORT));
        System.out.println("Servidor Sensor TCP iniciado no endereço " + HOSTNAME + " na porta " + SENSOR_PORT);

        // Configura o servidor para clientes
        clientServerChannel = ServerSocketChannel.open();
        clientServerChannel.configureBlocking(true);
        clientServerChannel.bind(new InetSocketAddress(HOSTNAME, CLIENT_PORT));
        System.out.println("Servidor Client TCP iniciado no endereço " + HOSTNAME + " na porta " + CLIENT_PORT);
    }

    public void start() throws IOException {
        new Thread(this::sendMulticast).start(); // Inicia multicast
        new Thread(this::acceptSensors).start(); // Aceita conexões de sensores
        new Thread(this::acceptClients).start(); // Aceita conexões de clientes
    }

    private void sendMulticast() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            String message = HOSTNAME + ":" + SENSOR_PORT;

            while (true) {
                DatagramPacket packet = new DatagramPacket(
                        message.getBytes(),
                        message.length(),
                        group,
                        MULTICAST_PORT
                );
                socket.send(packet);
                System.out.println("Mensagem de multicast enviada: " + message);
                Thread.sleep(5000);
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Erro ao enviar multicast: " + e.getMessage());
        }
    }

    private void acceptSensors() {
        try {
            while (true) {
                SocketChannel sensorChannel = sensorServerChannel.accept();
                System.out.println("Sensor TCP " + sensorChannel.getRemoteAddress() + " conectado.");
                sensors.add(sensorChannel);
                sensorThreadPool.submit(() -> handleSensorMessage(sensorChannel));
            }
        } catch (IOException e) {
            System.out.println("Erro ao aceitar conexão de Sensor: " + e.getMessage());
        }
    }

    private void handleSensorMessage(SocketChannel sensorChannel) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 2);
            while (true) {
                buffer.clear();
                int bytesRead = sensorChannel.read(buffer);
                if (bytesRead == -1) {
                    break;
                }

                buffer.flip();
                if (buffer.remaining() >= 4) {
                    int messageLength = buffer.getInt();
                    if (buffer.remaining() < messageLength) {
                        buffer.position(buffer.limit());
                        continue;
                    }

                    byte[] data = new byte[messageLength];
                    buffer.get(data);

                    try {
                        Message message = Message.parseFrom(data);
                        String sensorId = message.getSensorId();
                        String comando = message.getComando();
                        
                        System.out.println(sensorId);
                        if (comando.equals("ping")) {
                            System.out.println("ping: " + message.getSensorId());
                        }
                        else if (comando.equals("renomear")) {
                            sensorId = sensorId + "_" + sensorChannel.getRemoteAddress();
                            //sufix = sufix + 1;
                            feedbackToSensor(sensorId, sensorChannel);
                            sensorMap.putIfAbsent(sensorId, sensorChannel);

                        }else{
                            forwardToClients(message);
                        }

                    } catch (InvalidProtocolBufferException e) {
                        System.out.println("Erro ao desserializar mensagem do Sensor: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o Sensor: " + e.getMessage());
        } finally {
            try {
                // Remover o sensor do mapa ao desconectar
                sensorMap.values().removeIf(channel -> channel.equals(sensorChannel));
                sensors.remove(sensorChannel);
                sensorChannel.close();
                System.out.println("Sensor desconectado e removido do mapa.");
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão com o Sensor: " + e.getMessage());
            }
        }
    }

    private void acceptClients() {
        try {
            while (true) {
                SocketChannel clientChannel = clientServerChannel.accept();
                System.out.println("Client TCP " + clientChannel.getRemoteAddress() + " conectado.");
                clients.add(clientChannel);
                new Thread(() -> handleClientMessage(clientChannel)).start();
            }
        } catch (IOException e) {
            System.out.println("Erro ao aceitar conexão de Client: " + e.getMessage());
        }
    }

    //Aqui que a mensagem do cliente é lidada e enviada aos sensores    
    
    private void handleClientMessage(SocketChannel clientChannel) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 2);
            while (true) {
                buffer.clear();
                int bytesRead = clientChannel.read(buffer);
                if (bytesRead == -1) {
                    break;
                }

                buffer.flip();
                if (buffer.remaining() >= 4) {
                    int messageLength = buffer.getInt();
                    if (buffer.remaining() < messageLength) {
                        buffer.position(buffer.limit());
                        continue;
                    }

                    byte[] data = new byte[messageLength];
                    buffer.get(data);

                    try {
                        Message message = Message.parseFrom(data);
                        //Ideia é fazer uma mensagem dummy para colocar la no hash,
                        // pq aqui é só enviar o comando listar para os sensores responderem
                        if(message.getComando().equals("listar")){
                            forwardToSensors(message);
                            //Aqui deve ser chamado o listar
                            listSensors(clientChannel);
                        }
                        else if(message.getComando().equals("ping")){
                            System.out.println("ping do client \n");
                        }
                        else {
                            sendMessageToSensor(message.getSensorId(), message); // Envio direto ao sensor pelo ID
                        }

                    } catch (InvalidProtocolBufferException e) {
                        System.out.println("Erro ao desserializar mensagem do Client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o Client: " + e.getMessage());
        } finally {
            try {
                clients.remove(clientChannel);
                clientChannel.close();
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão com o Client: " + e.getMessage());
            }
        }
    }
    
    //Envia uma mensagem dummy para ter todos os sensores no map
    private void forwardToSensors(Message message) {
        synchronized (sensors) {
            if (sensors.isEmpty()) {
                System.out.println("Nenhum sensor conectado. Não é possível encaminhar a mensagem.");
            }
            for (SocketChannel sensorChannel : sensors) {
                try {
                    System.out.println("Tentando repassar mensagem para o Sensor: " + sensorChannel.getRemoteAddress());
                    byte[] messageBytes = message.toByteArray();
                    ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
                    buffer.putInt(messageBytes.length); // Tamanho da mensagem
                    buffer.put(messageBytes); // Mensagem Protobuf
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        sensorChannel.write(buffer);  // Envia a mensagem Protobuf para o Sensor
                    }
                    System.out.println("Mensagem do Gateway repassada para o Sensor: " + message);
                } catch (IOException e) {
                    System.out.println("Erro ao repassar mensagem para o Sensor: " + e.getMessage());
                }
            }
        }
    }

     
    public void sendMessageToSensor(String sensorId, Message message) {
        SocketChannel sensorChannel = sensorMap.get(sensorId);
        if (sensorChannel != null && sensorChannel.isConnected()) {
            try {
                byte[] messageBytes = message.toByteArray();
                ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
                buffer.putInt(messageBytes.length); // Tamanho da mensagem
                buffer.put(messageBytes); // Mensagem Protobuf
                buffer.flip();
                while (buffer.hasRemaining()) {
                    sensorChannel.write(buffer); // Envia mensagem para o sensor específico
                }
                System.out.println("Mensagem enviada ao Sensor ID: " + sensorId);
            } catch (IOException e) {
                System.out.println("Erro ao enviar mensagem para o Sensor ID " + sensorId + ": " + e.getMessage());
            }
        } else {
            System.out.println("Sensor com ID " + sensorId + " não encontrado ou desconectado.");
            sensorDisconnectedMessage(message.getSensorId());
        }
    }

    public void sensorDisconnectedMessage(String Id){
        synchronized (clients){
            Message responseMessage = Message.newBuilder()
                .setSensorId(Id)  // ID do Sensor
                .setStatus(false)  // Status do Sensor
                .setComando("nulo") // Define o comando
                .setPayload("Desconectado, por favor atualize a lista de sensores") // Adiciona a lista de IDs no payload
                .build();
    
        // Serializa a mensagem para enviar ao sensor
            byte[] responseBytes = responseMessage.toByteArray();
            for(SocketChannel clientChannel: clients){
                try {
                    // Envia a mensagem para o cliente solicitante
                    ByteBuffer buffer = ByteBuffer.allocate(4 + responseBytes.length);
                    buffer.putInt(responseBytes.length); // Tamanho da mensagem
                    buffer.put(responseBytes); // Dados da mensagem
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        clientChannel.write(buffer); // Envia a mensagem ao cliente
                    }
                    System.out.println("Informação de sensor desconectado enviado a: " + clientChannel.getRemoteAddress());
                } catch (IOException e) {
                    System.out.println("Erro ao enviar pacote ao cliente: " + e.getMessage());
                }
            }
        }
    }


    private void forwardToClients(Message message) {
        synchronized (clients) {
            for (SocketChannel clientChannel : clients) {
                try {
                    byte[] messageBytes = message.toByteArray();
                    ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
                    buffer.putInt(messageBytes.length);
                    buffer.put(messageBytes);
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        clientChannel.write(buffer);
                    }
                } catch (IOException e) {
                    System.out.println("Erro ao enviar mensagem para Cliente: " + e.getMessage());
                }
            }
        }
    }

    private void feedbackToSensor(String sensorId, SocketChannel sensorChannel) {
        Message responseMessage = Message.newBuilder()
                .setSensorId("Gateway")  // ID do Sensor
                .setStatus(true)  // Status do Sensor
                .setComando("renomear") // Define o comando
                .setPayload(sensorId) // Adiciona a lista de IDs no payload
                .build();
    
        // Serializa a mensagem para enviar ao sensor
        byte[] responseBytes = responseMessage.toByteArray();
    
        try {
            // Envia a mensagem para o cliente solicitante
            ByteBuffer buffer = ByteBuffer.allocate(4 + responseBytes.length);
            buffer.putInt(responseBytes.length); // Tamanho da mensagem
            buffer.put(responseBytes); // Dados da mensagem
            buffer.flip();
            while (buffer.hasRemaining()) {
                sensorChannel.write(buffer); // Envia a mensagem ao cliente
            }
            System.out.println("Novo nome atribuido ao sensor: " + sensorChannel.getRemoteAddress());
        } catch (IOException e) {
            System.out.println("Erro ao enviar pacote ao sensor: " + e.getMessage());
        }
    }

    private void listSensors(SocketChannel clientChannel) {
        StringBuilder sensorIds = new StringBuilder();
   
        synchronized (sensorMap) {
            for (String sensorId : sensorMap.keySet()) {
                if (sensorIds.length() > 0) {
                    sensorIds.append(","); // Se não for o primeiro, separa com vírgula
                }
                sensorIds.append(sensorId); // Adiciona o ID do sensor à string
            }
        }
   
        // Debug: Print dos sensores antes de enviar
        System.out.println("Sensores listados: " + sensorIds.toString());
    
        // Criando a mensagem com os IDs dos sensores no campo 'payload'
        Message responseMessage = Message.newBuilder()
                .setSensorId("Gateway")  // ID do Sensor
                .setStatus(true)  // Status do Sensor
                .setComando("listar") // Define o comando
                .setPayload(sensorIds.toString()) // Adiciona a lista de IDs no payload
                .build();
    
        // Serializa a mensagem para enviar ao cliente
        byte[] responseBytes = responseMessage.toByteArray();
    
        try {
            // Envia a mensagem para o cliente solicitante
            ByteBuffer buffer = ByteBuffer.allocate(4 + responseBytes.length);
            buffer.putInt(responseBytes.length); // Tamanho da mensagem
            buffer.put(responseBytes); // Dados da mensagem
            buffer.flip();
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer); // Envia a mensagem ao cliente
            }
            System.out.println("Lista de sensores enviada para " + clientChannel.getRemoteAddress());
        } catch (IOException e) {
            System.out.println("Erro ao enviar lista de sensores para o cliente: " + e.getMessage());
        }
    }
   

    public static void main(String[] args) {
        try {
            Gateway server = new Gateway();
            server.start();
        } catch (IOException e) {
            System.err.println("Erro ao inicializar servidor: " + e.getMessage());
        }
    }
}
