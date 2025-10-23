package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.*;
import java.util.*;

public class CouncilMember {
    public static String memberId;
    public static Profile profile;
    public static int port;
    public static NetworkConfig config;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java CouncilMember <MemberID> --profile <reliable|standard|latent|failure>");
            return;
        }

        memberId = args[0];
        String profileArg = args[2];
        profile = Profile.fromString(profileArg);

        try {
            config = NetworkConfig.load();
            port = config.getPort(memberId);

            System.out.printf("[%s] Starting on port %d with profile: %s%n", memberId, port, profileArg);

            ServerSocket serverSocket = new ServerSocket(port);
            PaxosHandler paxos = new PaxosHandler(memberId, config, profile);

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        String line;
                        while ((line = in.readLine()) != null) {
                            paxos.handleMessage(line);
                        }
                    } catch (IOException e) {
                        System.err.println("Error handling socket: " + e.getMessage());
                    }
                }).start();
            }

        } catch (IOException e) {
            System.err.println("Startup error: " + e.getMessage());
        }
    }
}
