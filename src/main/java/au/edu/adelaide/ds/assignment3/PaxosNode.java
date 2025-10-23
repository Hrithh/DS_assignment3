package au.edu.adelaide.ds.assignment3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents a Paxos node that communicates over UDP and handles incoming Paxos messages.
 * <p>
 * This node delegates logic to a {@link PaxosHandler} while managing its own network sockets,
 * message delivery, and concurrency via a thread pool.
 * </p>
 */
public class PaxosNode {

    //UDP port this node listens on.
    private final int port;

    //Unique node identifier
    private final String nodeId;

    //Executor service for handling incoming and outgoing messages concurrently.
    private final ExecutorService executor;

    //Paxos logic handler shared by proposer, acceptor, and learner roles.
    private final PaxosHandler handler;

    /**
     * Constructs a PaxosNode instance with the given identity, port, and behavior configuration.
     *
     * @param nodeId  the unique ID of this node
     * @param port    the port this node should listen on
     * @param config  the network configuration (e.g., peer mapping)
     * @param profile the profile controlling latency/failure simulation
     */
    public PaxosNode(String nodeId, int port, NetworkConfig config, Profile profile) {
        this.port = port;
        this.nodeId = nodeId;
        this.executor = Executors.newCachedThreadPool();
        this.handler = new PaxosHandler(nodeId, config, profile);
    }

    /**
     * Starts the PaxosNode by binding a UDP socket on the given port and
     * continuously receiving and dispatching packets for processing.
     */
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

    /**
     * Handles a received UDP packet by converting it into a string and passing
     * it to the PaxosHandler for processing.
     *
     * @param packet the incoming UDP packet
     */
    private void handlePacket(DatagramPacket packet) {
        try {
            String msg = new String(packet.getData(), 0, packet.getLength());
            handler.handleMessage(msg);
        } catch (Exception e) {
            System.err.println("[" + nodeId + "] Failed to handle packet: " + e.getMessage());
        }
    }

    /**
     * Sends a message to a target IP address and port using UDP.
     * <p>
     * This method runs asynchronously in a thread from the executor pool.
     * </p>
     *
     * @param targetIp   destination IP
     * @param targetPort destination UDP port
     * @param message    the message string to send
     */
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

    /**
     * Shuts down the node by forcefully terminating all background tasks.
     */
    public void shutdown() {
        executor.shutdownNow();
        System.out.println("[" + nodeId + "] Node shut down.");
    }

    /**
     * Main method to launch a PaxosNode from the command line.
     * <p>
     * Expects arguments:
     * <ul>
     *     <li>{@code <nodeId>} — the identifier for this node</li>
     *     <li>{@code <port>} — the UDP port this node should bind to</li>
     *     <li>{@code --profile=<type>} — one of reliable, standard, latent, or failure</li>
     * </ul>
     * </p>
     *
     * @param args command line arguments as described above
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java PaxosNode <nodeId> <port> --profile=<reliable|standard|latent|failure>");
            return;
        }

        try {
            String nodeId = args[0];
            int port = Integer.parseInt(args[1]);

            // Extract profile from --profile=VALUE
            String profileArg = args[2];
            if (!profileArg.startsWith("--profile=")) {
                throw new IllegalArgumentException("Missing --profile argument");
            }
            String profileStr = profileArg.substring("--profile=".length());
            Profile profile = Profile.fromString(profileStr);

            // Load network config
            NetworkConfig config = NetworkConfig.load();

            // Start the node
            PaxosNode node = new PaxosNode(nodeId, port, config, profile);
            node.start();

        } catch (Exception e) {
            System.err.println("[ERROR] Failed to start PaxosNode: " + e.getMessage());
        }
    }
}
