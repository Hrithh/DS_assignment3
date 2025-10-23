package au.edu.adelaide.ds.assignment3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaxosNode {

    private final int port;
    private final String nodeId;
    private final ExecutorService executor;
    private final PaxosHandler handler;

    public PaxosNode(String nodeId, int port) {
        this.port = port;
        this.nodeId = nodeId;
        this.executor = Executors.newCachedThreadPool();
        this.handler = new PaxosHandler(nodeId);
    }

    public void start() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                System.out.println("[" + nodeId + "] Listening on port " + port);
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    executor.submit(() -> handlePacket(packet));
                }
            } catch (IOException e) {
                System.err.println("[" + nodeId + "] Socket error: " + e.getMessage());
            }
        });
    }

    private void handlePacket(DatagramPacket packet) {
        try {
            String msg = new String(packet.getData(), 0, packet.getLength());
            handler.handleMessage(msg);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to handle packet: " + e.getMessage());
        }
    }

    public void send(String targetIp, int targetPort, String message) {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName(targetIp),
                        targetPort
                );
                socket.send(packet);
                System.out.println("[" + nodeId + "] Sent to " + targetIp + ":" + targetPort + " => " + message);
            } catch (IOException e) {
                System.err.println("[" + nodeId + "] Failed to send: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
        System.out.println("[" + nodeId + "] Node shut down.");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java PaxosNode <nodeId> <port>");
            return;
        }

        String nodeId = args[0];
        int port = Integer.parseInt(args[1]);

        PaxosNode node = new PaxosNode(nodeId, port);
        node.start();
    }
}
