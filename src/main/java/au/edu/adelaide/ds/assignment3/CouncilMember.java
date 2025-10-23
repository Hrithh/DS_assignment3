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
        // Args:
        //   <MemberID> --profile=<reliable|standard|latent|failure> [--propose=<VALUE>]
        if (args.length < 2 || !args[1].startsWith("--profile=")) {
            System.out.println("Usage: java CouncilMember <MemberID> --profile=<reliable|standard|latent|failure> [--propose=<VALUE>]");
            return;
        }

        memberId = args[0].trim();
        String profileArg = args[1].substring("--profile=".length()).trim();

        // Optional: --propose=VALUE
        String proposeArg = null;
        for (String a : args) {
            if (a != null && a.startsWith("--propose=")) {
                proposeArg = a.substring("--propose=".length()).trim();
            }
        }

        profile = Profile.fromString(profileArg);

        try {
            config = NetworkConfig.load();
            port = config.getPort(memberId);
            System.out.printf("[%s] Starting on port %d with profile: %s%n", memberId, port, profileArg);

            ServerSocket serverSocket = new ServerSocket(port);
            PaxosHandler paxos = new PaxosHandler(memberId, config, profile);

            // Fire a proposal if requested; otherwise default M1 proposes its own ID as leader.
            if (proposeArg != null && !proposeArg.isEmpty()) {
                final String v = proposeArg;
                new Thread(() -> {
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                    System.out.printf("[%s] Scheduling proposal via CLI: %s%n", memberId, v);
                    paxos.propose(v);
                }).start();
            } else if ("M1".equals(memberId)) {
                new Thread(() -> {
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                    paxos.propose("LEADER_" + memberId);
                }).start();
            }

            // Allow typing values in the console to start proposals dynamically
            startInteractiveProposer(paxos);

            // Main loop to accept incoming socket connections
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

        } catch (Exception e) {
            System.err.println("Startup error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------------
    // Interactive proposer (System.in)
    // -------------------------------
    private static void startInteractiveProposer(PaxosHandler paxos) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.printf("[%s] Type a value to propose (ex: LEADER_M5). Commands: /help, /q%n", memberId);
                String line;
                while ((line = br.readLine()) != null) {
                    String v = line.trim();
                    if (v.isEmpty()) continue;
                    if (v.equalsIgnoreCase("/help")) {
                        System.out.println("Enter a value to propose (e.g., LEADER_M3). Commands: /q to stop input on this node.");
                        continue;
                    }
                    if (v.equalsIgnoreCase("/q") || v.equalsIgnoreCase("exit")) {
                        System.out.println("Stopping interactive proposer input for this node.");
                        break;
                    }
                    paxos.propose(v);
                }
            } catch (IOException e) {
                System.err.println("stdin-proposer error: " + e.getMessage());
            }
        }, "stdin-proposer");

        t.setDaemon(true);  // won’t block process exit
        t.start();
    }
}
