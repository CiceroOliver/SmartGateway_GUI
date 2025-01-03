package main.java;

import main.java.MessageOuterClass.Message;  // Importação da classe Message gerada pelo Protobuf
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import com.google.protobuf.InvalidProtocolBufferException;

public class AC {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 6000;
  
    private String gatewayHost;
    private int gatewayPort;

    private SocketChannel socketChannel;

    private volatile boolean status = false;  // O status inicial é desligado
    private volatile int Temperatura = 25;
    private String ID = "Ar-condicionado";
    private volatile String modo = "auto";
    private volatile boolean lista = false;
    //COOL, HEAT, AUTO, DRY 
    private volatile int FanSpeed = 0;
    
    public AC() {
        this.gatewayHost = null;
        this.gatewayPort = -1;
    }

    public boolean ligar(){
        return true;
    }

    public boolean desligar(){
        return false;
    }

    public void setTemperatura(int valor){
        //min 16, max 32
        if (valor  <= 32 && valor >= 16) {
            Temperatura = valor;
        }
    }

    public void setModo(String novo_modo){
         //COOL, HEAT, AUTO, DRY 
         modo = novo_modo;
    }

    public void setFanSpeed(int valor){
        if(valor > 0 && valor < 6){
            FanSpeed = valor;
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
            else{
                payload = "Modo: " + modo + "; Temperatura: " + Temperatura ;
                comando = "status, ligar, desligar, modo, fanspeed, temperatura";
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

                        if ("listar".equalsIgnoreCase(comando)){
                            enviarAtualizacao();
                            lista = true;
                            System.out.println("Entrou");
                        }

                        else if ("listar comandos".equalsIgnoreCase(comando) && (message.getSensorId().equals(ID))){
                            enviarAtualizacao();
                            lista = true;
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
                        
                        else if("modo".equalsIgnoreCase(comando) && status && (message.getSensorId().equals(ID))){
                            setModo(message.getPayload()); 
                            enviarAtualizacao();
                        }

                        else if("temperatura".equalsIgnoreCase(comando) && status && (message.getSensorId().equals(ID))){
                            String payload = message.getPayload(); // Exemplo: "25"
                            try {
                                Temperatura = Integer.parseInt(payload.trim());
                                System.out.println("Temperatura: " + Temperatura);
                                enviarAtualizacao();
                            } 
                            catch (NumberFormatException e) {
                                System.err.println("Erro ao converter payload para int: " + e.getMessage());
                            }
                        }

                        else if ("fan speed".equalsIgnoreCase(comando) && status && (message.getSensorId().equals(ID))) {
                            String payload = message.getPayload(); // Exemplo: "25"
                            try {
                                FanSpeed = Integer.parseInt(payload.trim());
                                System.out.println("FanSpeed: " + FanSpeed);
                                enviarAtualizacao();
                            } 
                            catch (NumberFormatException e) {
                                System.err.println("Erro ao converter payload para int: " + e.getMessage());
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
            enviarAtualizacao();
            new Thread(this::startReceiving).start();
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o Gateway: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        AC sensor = new AC();
        sensor.discoverGateway();
        sensor.startCommunication();
    }
}
