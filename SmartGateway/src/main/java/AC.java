package main.java;

import main.java.MessageOuterClass.Message;  // Importação da classe Message gerada pelo Protobuf
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;

public class AC {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 6000;
    private static final int PING_INTERVAL = 5000; // 5 segundos
  
    private String gatewayHost;
    private int gatewayPort;

    private SocketChannel socketChannel;

    private volatile boolean status = false;  // O status inicial é desligado
    private volatile int Temperatura = 25;
    private String ID = "Ar-condicionado";
    private String originalID = "Ar-condicionado";
    private volatile String modo = "auto";
    List<String> Modos = Arrays.asList("auto", "heat", "cool", "dry");
    private volatile boolean lista = false;
    private volatile boolean modeSelected = false;
    private volatile int FanSpeed = 1;


    private volatile boolean connected = false;
    
    private Thread receivingThread;
    private Thread pingThread;

    public AC() {
        this.gatewayHost = null;
        this.gatewayPort = -1;
    }

    public void ligar(){
        status = true;
    }

    public void desligar(){
        status = false;
    }

    public boolean setTemperatura(int valor){
        //min 16, max 32
        if (valor  <= 32 && valor >= 16) {
            Temperatura = valor;
            return true;
        }else{
            return false;
        }

    }

    public boolean setFanSpeed(int valor){
        if(valor < 6 && valor > 0){
            FanSpeed = valor;
            return true;
        }
        else{
            return false;
        }
    }

    public void setModo(String novo_modo){
         //COOL, HEAT, AUTO, DRY 
         modo = novo_modo;
    }

    public void isOff(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload = "DESLIGADO\n" + "Ultimo estado - " + "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan Speed:" + FanSpeed;
            String comando = "status, ligar, desligar, modo, temperatura"; 

            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();
            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    public void invalideMode(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload = "Modo inválido \n" + "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan speed:" + FanSpeed + "\n" +
            "Modos válidos: auto, dry, heat, cool" ;
            String comando = "status, ligar, desligar, modo, temperatura"; 

            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }
        
    }

    public void invalideTemperature(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload = "Temperatura inválida \n" + "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan speed:" + FanSpeed + "\n" +
            "Temperaturas válidas: 16 a 32" ;
            String comando = "status, ligar, desligar, modo, temperatura"; 

            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            // Serializa a mensagem
            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    public void invalideFanSpeed(){
        try {
            // Criar a mensagem no formato id:status:payload
            String payload = "Fan Speed inválida \n" + "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan speed:" + FanSpeed + "\n" +
            "Fan speed válidas: 1 a 5" ;
            String comando = "status, ligar, desligar, modo, temperatura, fan speed"; 

            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            // Serializa a mensagem
            sendMessage(message);
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }

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

            sendMessage(message);
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
                payload = "status,ligar,desligar,modo,fanspeed,temperatura";
                comando = "listar comandos";
            }
            else if(modeSelected){
                if(modo.equals("heat")){
                    Temperatura = 28;
                    FanSpeed = 3;
                } 
                else if(modo.equals("dry")){
                    Temperatura = 21;
                    FanSpeed = 1;
                }
                else if (modo.equals("cool")) {
                    Temperatura = 16;
                    FanSpeed = 3;
                }
                else if(modo.equals("auto")){
                    Temperatura = 25;
                    FanSpeed = 2;
                }
                payload = "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan Speed: " + FanSpeed;
                comando = "status, ligar, desligar, modo, fanspeed, temperatura";
            }
            else{
                payload = "Modo: " + modo + "; Temperatura: " + Temperatura + "; Fan Speed: " + FanSpeed;
                comando = "status, ligar, desligar, modo, fanspeed, temperatura";
            }
            
            lista = false;
            modeSelected = false;
            // Cria a mensagem Protobuf
            Message message = Message.newBuilder()
                    .setSensorId(ID)  // ID do Sensor
                    .setStatus(status)  // Status do Sensor
                    .setPayload(payload)  // Payload
                    .setComando(comando)
                    .build();

            sendMessage(message);
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
                        if ("renomear".equals(comando)) {
                            ID = message.getPayload();
                        }
                        else if ("listar".equalsIgnoreCase(comando)){
                            lista = true;
                            enviarAtualizacao();
                            
                            System.out.println("Entrou");
                        }

                        else if ("listar comandos".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                            lista = true;
                            enviarAtualizacao();
                        }

                        else if ("status".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            enviarAtualizacao();
                        }

                        else if ("ligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            status = true;
                            System.out.println("Sensor ligado.");
                            enviarAtualizacao();
                            
                        } 
                        else if ("desligar".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            status = false;
                            System.out.println("Sensor desligado.");
                            enviarAtualizacao();
                            
                        }
                        
                        else if("modo".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                            if(status){
                                boolean found = false;
                                for(String item : Modos){
                                    if(item.equals(message.getPayload())){
                                        setModo(message.getPayload());
                                        modeSelected = true;
                                        found = true;
                                        enviarAtualizacao();
                                    }
                                }
                                if (!found) {
                                    invalideMode();
                                }
                            }else{
                                isOff();
                            }
                        }

                        else if("temperatura".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                            if(status){
                                String payload = message.getPayload(); // Exemplo: "25"
                                boolean formatoValidoTemp;
                                try {
                                    formatoValidoTemp = setTemperatura(Integer.parseInt(payload.trim()));
                                    if (formatoValidoTemp) {
                                        Temperatura = Integer.parseInt(payload.trim());
                                        enviarAtualizacao();
                                    }
                                    else{
                                        invalideTemperature();
                                    }
                                } 
                                catch (NumberFormatException e) {
                                    System.err.println("Erro ao converter payload para int: " + e.getMessage());
                                    invalideTemperature();
                                }
                            } else{
                                isOff();
                            }
                        }

                        else if ("fanspeed".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))) {
                            if(status){
                                String payload = message.getPayload(); // Exemplo: "25"
                                boolean formatoValidoSpeed;
                                try {
                                    formatoValidoSpeed = setFanSpeed(Integer.parseInt(payload.trim()));
                                    if (formatoValidoSpeed) {
                                        FanSpeed = Integer.parseInt(payload.trim());
                                        enviarAtualizacao();
                                    }
                                    else{
                                        invalideFanSpeed();
                                    }
                                    System.out.println("FanSpeed: " + FanSpeed);
                                } 
                                catch (NumberFormatException e) {
                                    System.err.println("Erro ao converter payload para int: " + e.getMessage());
                                    invalideFanSpeed();
                                }
                            } else{
                                isOff();
                            }
                        }


                    } 
                    catch (InvalidProtocolBufferException e) {
                        System.err.println("Erro ao desserializar a mensagem: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem do Gateway: " + e.getMessage());
            connected = false;
        }
    }

    public void startCommunication() {
        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(gatewayHost, gatewayPort));
            socketChannel.configureBlocking(true);
            connected = true;

            System.out.println("Conectado ao Gateway: " + gatewayHost + ":" + gatewayPort);

            requestNewName();

            receivingThread = new Thread(() -> {
                startReceiving();
            });

            pingThread = new Thread(this::startPing);

            receivingThread.start();
            pingThread.start();

        } catch (IOException e) {
            System.err.println("Erro na comunicação com o Gateway: " + e.getMessage());
            connected = false;
        }
    }

    public void stopCommunication() {
        try {
            connected = false;
            ID = originalID;
            status = false;
            if (socketChannel != null && socketChannel.isOpen()) {
                socketChannel.close();
            }
            if (receivingThread != null) receivingThread.interrupt();
            if (pingThread != null) pingThread.interrupt();

        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
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

    public static void main(String[] args) {
        AC sensor = new AC();
    
        while (true) {
            if (!sensor.connected) {
                sensor.discoverGateway();
                sensor.startCommunication();
            }
    
            try {
                Thread.sleep(2000); // Espera para evitar reconexões muito rápidas
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
    
            if (!sensor.connected) {
                sensor.stopCommunication();
                System.out.println("Tentando reconectar...");
            }
        }
    }
    
}
