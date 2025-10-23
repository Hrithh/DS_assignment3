package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.Socket;

/**
 * A simple test client that connects to a Paxos CouncilMember and sends a static PREPARE message.
 * <p>
 * This is primarily intended for manual testing or debugging. It can be used to
 * directly send Paxos messages (in JSON format) to a running node and observe its output.
 * </p>
 */
public class PaxosClient {

    /**
     * Entry point for the client program.
     * <p>
     * Connects to a specified CouncilMember node (defaults to {@code M1} if no argument is provided),
     * sends a static {@code PREPARE} Paxos message, and prints any responses received.
     * </p>
     *
     * @param args optional: first argument specifies the target member ID (e.g., {@code M4})
     * @throws IOException if socket communication fails
     */
    public static void main(String[] args) throws IOException {
        //which member to poke (default M1)
        String targetMember = (args.length > 0) ? args[0] : "M1";

        //load port from classpath resource
        NetworkConfig cfg = NetworkConfig.load();
        int port = cfg.getPort(targetMember);

        System.out.println("[CLIENT] Connecting to " + targetMember + " on port " + port);

        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            //Match your Message.java fields: type, proposalNumber, value, senderId
            String msg = "{\"type\":\"PREPARE\",\"proposalNumber\":1,\"value\":null,\"senderId\":\"CLIENT\"}";
            out.println(msg);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[CLIENT] Received: " + line);
            }
        }
    }
}
