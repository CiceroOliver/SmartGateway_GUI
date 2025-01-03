package main.java;

import main.java.MessageOuterClass.Message;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;

import com.google.protobuf.InvalidProtocolBufferException;

public class Client {
    private final Scanner scanner;
    private SocketChannel clientChannel;
    private Thread listenerThread;

    // Estrutura para armazenar os sensores (IDs recebidos do Gateway)
    private Set<String> sensorIds = new HashSet<>();

    public Client() throws IOException {
        clientChannel = SocketChannel.open();
        clientChannel.connect(new InetSocketAddress("127.0.0.1", 4000));
        scanner = new Scanner(System.in);
    }

    public void start() throws IOException {
        System.out.println("Iniciando cliente...");

        // Buffer para leitura
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

        // Thread para ouvir mensagens do servidor
        listenerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    readBuffer.clear();

                    // Ler o tamanho da mensagem (4 bytes - int)
                    if (clientChannel.read(readBuffer) >= 4) {
                        readBuffer.flip();
                        int messageLength = readBuffer.getInt();

                        if (messageLength > readBuffer.remaining()) {
                            System.out.println("Mensagem incompleta recebida, aguardando mais dados...");
                            continue; // Espera mais dados chegarem
                        }

                        byte[] data = new byte[messageLength];
                        readBuffer.get(data); // Lê a mensagem completa

                        try {
                            Message message = Message.parseFrom(data);
                            System.out.println("\n");
                            System.out.println("ID: " + message.getSensorId());
                            System.out.println("Comando: " + message.getComando());
                            System.out.println("Status: " + message.getStatus());
                            System.out.println("Payload: " + message.getPayload());

                            // Verifica se o ID é "Gateway" e atualiza os sensores
                            if ("Gateway".equals(message.getSensorId())) {
                                // Limpa o conjunto de sensores antes de adicionar os novos
                                sensorIds.clear(); 

                                // Atualiza a estrutura de sensores com a nova lista de IDs
                                String[] ids = message.getPayload().split(","); // Divide os sensores
                                Collections.addAll(sensorIds, ids); // Adiciona todos os IDs recebidos

                                System.out.println("Sensores atualizados: " + sensorIds);
                            }

                        } catch (InvalidProtocolBufferException e) {
                            System.out.println("Erro ao desserializar mensagem: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão com o servidor encerrada: " + e.getMessage());
            }
        });
        listenerThread.start();

        // Loop principal para enviar mensagens ao servidor
        String msg;
        do {
            System.out.print("Digite a mensagem no formato id:comando:payload (ou sair para finalizar): ");
            msg = scanner.nextLine();

            if (!"sair".equalsIgnoreCase(msg)) {
                String[] parts = msg.split(":");
                if (parts.length == 3) {
                    String sensorId = parts[0];
                    String comando = parts[1];
                    String payload = parts[2];

                    Message message = Message.newBuilder()
                            .setSensorId(sensorId)
                            .setComando(comando)
                            .setStatus(false)
                            .setPayload(payload)
                            .build();

                    byte[] messageBytes = message.toByteArray();

                    // Aloca um buffer com o tamanho exato necessário
                    ByteBuffer writeBuffer = ByteBuffer.allocate(4 + messageBytes.length);
                    writeBuffer.putInt(messageBytes.length); // Prefixa o tamanho da mensagem
                    writeBuffer.put(messageBytes);
                    writeBuffer.flip();

                    // Envia a mensagem para o servidor
                    while (writeBuffer.hasRemaining()) {
                        clientChannel.write(writeBuffer);
                    }

                    System.out.println("Mensagem enviada para o servidor: " + message);
                } else {
                    System.out.println("Formato inválido! A mensagem deve ser no formato id:comando:payload.");
                }
            }
        } while (!"sair".equalsIgnoreCase(msg));

        listenerThread.interrupt();
    }

    public static void main(String[] args) {
        try {
            System.out.println("Iniciando cliente...");
            Client client = new Client();
            client.start();
        } catch (IOException e) {
            System.err.println("Erro ao inicializar cliente: " + e.getMessage());
        }
    }
}
