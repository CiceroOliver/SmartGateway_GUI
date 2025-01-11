package main.java;

import main.java.MessageOuterClass.Message;
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
    private static final int PING_INTERVAL = 5000; // 5 segundos

    private String gatewayHost;
    private int gatewayPort;

    private SocketChannel socketChannel;
    private Thread sendingThread;
    private Thread receivingThread;
    private Thread pingThread;

    private volatile boolean status = false;
    private volatile boolean lista = false;
    private volatile double Umidade = 35;
    private String ID = "Sensor Umidade";
    private String originalID = "Sensor Umidade";

    private volatile boolean connected = false; // Estado da conexão

    public SensorUmidade() {
        this.gatewayHost = null;
        this.gatewayPort = -1;
    }

    /**
     * Descobre o Gateway através de multicast.
     */
    @SuppressWarnings("deprecation")
    public void discoverGateway() {
        try (MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket.joinGroup(group);

            System.out.println("Aguardando mensagens multicast...");

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            multicastSocket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Mensagem multicast recebida: " + received);

            String[] addressParts = received.split(":");

            if (addressParts.length == 2) {
                gatewayHost = addressParts[0];
                gatewayPort = Integer.parseInt(addressParts[1]);
                System.out.println("Gateway descoberto: " + gatewayHost + ":" + gatewayPort);
            } else {
                System.err.println("Mensagem multicast inválida.");
            }

            multicastSocket.leaveGroup(group);
        } catch (IOException e) {
            System.err.println("Erro ao descobrir Gateway: " + e.getMessage());
        }
    }

    /**
     * Inicia a comunicação com o Gateway.
     */
    public void startCommunication() {
        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(gatewayHost, gatewayPort));
            socketChannel.configureBlocking(true);
            connected = true;

            System.out.println("Conectado ao Gateway: " + gatewayHost + ":" + gatewayPort);

            requestNewName();

            sendingThread = new Thread(this::startSending);
            receivingThread = new Thread(() -> {
                try {
                    startReceiving();
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            });

            pingThread = new Thread(this::startPing);

            sendingThread.start();
            receivingThread.start();
            pingThread.start();

        } catch (IOException e) {
            System.err.println("Erro na comunicação com o Gateway: " + e.getMessage());
            connected = false;
        }
    }

    /**
     * Finaliza a comunicação com o Gateway.
     */
    public void stopCommunication() {
        try {
            connected = false;
            ID = originalID;
            status = false;
            if (socketChannel != null && socketChannel.isOpen()) {
                socketChannel.close();
            }
            if (sendingThread != null) sendingThread.interrupt();
            if (receivingThread != null) receivingThread.interrupt();
            if (pingThread != null) pingThread.interrupt();

        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public void requestNewName() {
        try {
            String payload = ID + " requisitando novo nome";
            String comando = "renomear";

            Message message = Message.newBuilder()
                    .setSensorId(ID)
                    .setStatus(status)
                    .setPayload(payload)
                    .setComando(comando)
                    .build();

            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    public void sendUpdate() {
        try {
            String payload;
            String comando;
            if (lista) {
                payload = "status,ligar,desligar";
                comando = "listar comandos";
            }
            else{
                payload = String.format("Umidade: %.2f%%", Umidade);
                comando = "status, ligar, desligar";
            }

            lista = false;

            Message message = Message.newBuilder()
                    .setSensorId(ID)
                    .setStatus(status)
                    .setPayload(payload)
                    .setComando(comando)
                    .build();

            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar atualização: " + e.getMessage());
        }
    }

    private void sendMessage(Message message) throws IOException {
        byte[] messageBytes = message.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(4 + messageBytes.length);
        buffer.putInt(messageBytes.length);
        buffer.put(messageBytes);
        buffer.flip();

        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }

    public void startSending() {
        Random random = new Random();
        while (connected) {
            if (status) {
                try {
                    double variacao = random.nextDouble() * 0.8 * (random.nextBoolean() ? -1 : 1);
                    Umidade += variacao;
                    sendUpdate();
                } catch (Exception e) {
                    System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                    connected = false;
                    break;
                }
            }
            try {
                Thread.sleep(STATUS_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public void startReceiving() throws InvalidProtocolBufferException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (connected) {
            try {
                buffer.clear();
                int bytesRead = socketChannel.read(buffer);
                if (bytesRead == -1) throw new IOException("Conexão perdida");

                buffer.flip();
                int messageLength = buffer.getInt();
                byte[] data = new byte[messageLength];
                buffer.get(data);

                Message message = Message.parseFrom(data);

                System.out.println("Comando recebido: " + message.getComando());

                // Processamento das mensagens
                String comando = message.getComando();
                
                if ("ligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                    status = true;
                    sendUpdate();
                } 
                else if ("desligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                    status = false;
                    sendUpdate();
                }

                else if ("listar".equalsIgnoreCase(comando)){
                    lista = true;
                    sendUpdate();
                }
                else if ("listar comandos".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                    lista = true;
                    sendUpdate();
                }

                else if ("status".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                    sendUpdate();
                }
                else if("renomear".equals(comando)){
                    ID = message.getPayload();
                }


            } catch (IOException e) {
                System.err.println("Erro ao receber mensagem: " + e.getMessage());
                connected = false;
                break;
            }
        }
    }

    public void startPing() {
        while (connected) {
            try {
                Message ping = Message.newBuilder()
                        .setSensorId(ID)
                        .setStatus(true)
                        .setPayload("ping")
                        .setComando("ping")
                        .build();

                sendMessage(ping);
                Thread.sleep(PING_INTERVAL);
            } catch (IOException | InterruptedException e) {
                System.err.println("ping falhou: " + e.getMessage());
                connected = false;
                break;
            }
        }
    }

    public static void main(String[] args) {
        SensorUmidade sensor = new SensorUmidade();

        while (true) {
            sensor.discoverGateway();
            sensor.startCommunication();

            while (sensor.connected) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            sensor.stopCommunication();
            System.out.println("Tentando reconectar...");
        }
    }
}

