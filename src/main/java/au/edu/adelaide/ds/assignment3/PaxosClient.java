package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.Socket;

public class PaxosClient {
    public static void main(String[] args) throws IOException {
        // which member to poke (default M1)
        String targetMember = (args.length > 0) ? args[0] : "M1";

        // load port from classpath resource
        NetworkConfig cfg = NetworkConfig.load();
        int port = cfg.getPort(targetMember);

        System.out.println("[CLIENT] Connecting to " + targetMember + " on port " + port);

        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Match your Message.java fields: type, proposalNumber, value, senderId
            String msg = "{\"type\":\"PREPARE\",\"proposalNumber\":1,\"value\":null,\"senderId\":\"CLIENT\"}";
            out.println(msg);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[CLIENT] Received: " + line);
            }
        }
    }
}
