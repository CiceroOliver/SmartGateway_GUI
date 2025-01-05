package main.java;

import main.java.MessageOuterClass.Message;  // Importação da classe Message gerada pelo Protobuf
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Random;
import com.google.protobuf.InvalidProtocolBufferException;

public class SensorUmidade {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 6000;
    private static final int STATUS_INTERVAL = 10000; // 10 segundos

    private String gatewayHost;
    private int gatewayPort;

    private SocketChannel socketChannel;

    private volatile boolean status = false;  // O status inicial é desligado
    private volatile boolean lista = false;
    private volatile double Umidade = 35;
    private String ID = "Sensor Umidade";

    public SensorUmidade() {
        this.gatewayHost = null;
        this.gatewayPort = -1;
    }

    public boolean ligar(){
        return true;
    }

    public boolean desligar(){
        return false;
    }

    public void requestNewName(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload, comando;
            payload = ID + " requistando novo nome";
            comando = "renomear";

            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            // Serializa a mensagem
            byte[] messageBytes = message.toByteArray();

            // Envia a mensagem
            ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length); // Tamanho + mensagem
            buffer.putInt(messageBytes.length); // Adiciona o tamanho da mensagem
            buffer.put(messageBytes); // Coloca os dados da mensagem
            buffer.flip(); // Prepara o buffer para escrita
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer); // Envia a mensagem
            }

            // Exibe a mensagem enviada para depuração
            System.out.println("Mensagem enviada: ID: " + message.getSensorId() +
                    ", Status: " + message.getStatus() +
                    ", Payload: " + message.getPayload());
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }   
    }

    public void enviarAtualizacao(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload, comando;

            // Criar a mensagem no formato id:status:payload
            if(lista){
                payload = "status,ligar,desligar";
                comando = "listar comandos";
            }
            else{
                payload = String.format("Umidade: %.2f", (Umidade));
                payload = payload + "%";
                comando = "status, ligar, desligar";
            }
            
            lista = false;
            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            // Serializa a mensagem
            byte[] messageBytes = message.toByteArray();

            // Envia a mensagem
            ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length); // Tamanho + mensagem
            buffer.putInt(messageBytes.length); // Adiciona o tamanho da mensagem
            buffer.put(messageBytes); // Coloca os dados da mensagem
            buffer.flip(); // Prepara o buffer para escrita
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer); // Envia a mensagem
            }

            // Exibe a mensagem enviada para depuração
            System.out.println("Mensagem enviada: ID: " + message.getSensorId() +
                    ", Status: " + message.getStatus() +
                    ", Payload: " + message.getPayload());
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }   
    }
        

    @SuppressWarnings("deprecation")
    public void discoverGateway() {
        try (MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket.joinGroup(group);
    
            System.out.println("Sensor aguardando mensagens multicast...");
    
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
            multicastSocket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Mensagem multicast recebida: " + received);
    
            // Agora a mensagem é esperada no formato "127.0.0.1:4000"
            String[] addressParts = received.split(":");
    
            if (addressParts.length == 2) {
                gatewayHost = addressParts[0]; // O host (IP)
                gatewayPort = Integer.parseInt(addressParts[1]); // A porta
    
                System.out.println("Gateway descoberto: " + gatewayHost + ":" + gatewayPort);
            } else {
                System.err.println("Mensagem multicast recebida em formato inválido.");
            }
    
            multicastSocket.leaveGroup(group);
        } catch (IOException e) {
            System.err.println("Erro ao descobrir o Gateway: " + e.getMessage());
        }
    }

    // Thread responsável pelo envio de mensagens periodicamente
    public void startSending() {
        Random random = new Random();
        while (true) {
            if (status) {            
                try {
                    double variacao = random.nextDouble() * 0.8;
    
                    if (random.nextBoolean()) {
                        variacao *= -1;
                    }
    
                    String payload = String.format("Umidade: %.2f", (Umidade = (Umidade + variacao)));
                    payload = payload + "%";
    
                    Message message = Message.newBuilder()
                            .setSensorId(ID)
                            .setStatus(status)
                            .setPayload(payload)
                            .setComando("nulo")
                            .build();
    
                    byte[] messageBytes = message.toByteArray();
    
                    ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
                    buffer.putInt(messageBytes.length);
                    buffer.put(messageBytes);
                    buffer.flip();
    
                    while (buffer.hasRemaining()) {
                        socketChannel.write(buffer);
                    }
    
                    System.out.println("Mensagem enviada: ID: " + message.getSensorId() +
                            ", Status: " + message.getStatus() +
                            ", Payload: " + message.getPayload());
    
                } catch (IOException e) {
                    System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                }
            }
    
            try {
                Thread.sleep(STATUS_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida durante o envio.");
                break;
            }
        }
    }
    
    // Thread para receber e processar comandos
    public void startReceiving() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (true) {
                buffer.clear(); // Limpa o buffer
                int bytesRead = socketChannel.read(buffer); // Lê os dados do servidor
                if (bytesRead == -1) {
                    break;  // Se o servidor fechar a conexão
                }

                buffer.flip();  // Prepara o buffer para leitura
                if (buffer.remaining() >= 4) {
                    int messageLength = buffer.getInt();  // Lê o tamanho da mensagem
                    if (buffer.remaining() < messageLength) {
                        // Se a mensagem não estiver completa, espera mais dados
                        buffer.position(buffer.limit());
                        continue;
                    }

                    byte[] data = new byte[messageLength];
                    buffer.get(data);  // Lê a mensagem completa

                    try {
                        Message message = Message.parseFrom(data);  // Desserializa a mensagem

                        // Exibe os valores dos campos da mensagem
                        System.out.println("Mensagem recebida do Gateway:");
                        System.out.println("ID do Sensor: " + message.getSensorId());
                        System.out.println("Status do Sensor: " + message.getStatus());
                        System.out.println("Payload: " + message.getPayload());
                        System.out.println("Comando: " + message.getComando());

                        String comando = message.getComando();
                        if("renomear".equals(comando)){
                            ID = message.getPayload();
                        }
                        else if ("ligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            status = true;
                            enviarAtualizacao();

                        } 
                        else if ("desligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            status = false;
                            enviarAtualizacao();
                        }

                        else if ("listar".equalsIgnoreCase(comando)){
                            lista = true;
                            enviarAtualizacao();
                        }

                        else if ("listar comandos".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                            lista = true;
                            enviarAtualizacao();
                        }

                        else if ("status".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            enviarAtualizacao();
                        }

                    } catch (InvalidProtocolBufferException e) {
                        System.err.println("Erro ao desserializar a mensagem: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem do Gateway: " + e.getMessage());
        }
    }

    public void startCommunication() {
        if (gatewayHost == null || gatewayPort == -1) {
            System.out.println("Gateway não descoberto. Não é possível iniciar a comunicação TCP.");
            return;
        }

        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(gatewayHost, gatewayPort));
            System.out.println("Conectado ao Gateway em " + gatewayHost + ":" + gatewayPort);
            //seIdentificar();
            requestNewName();
            // Inicia as threads para envio e recepção de mensagens
            new Thread(this::startSending).start();
            new Thread(this::startReceiving).start();
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o Gateway: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SensorUmidade sensor = new SensorUmidade();
        sensor.discoverGateway();
        sensor.startCommunication();
    }
}
