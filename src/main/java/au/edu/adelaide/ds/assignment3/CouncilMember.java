package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.*;
import java.util.*;

public class CouncilMember {

    // --- Timing helper (for timestamped logs) ---
    static final long T0 = System.currentTimeMillis();

    public static void log(String fmt, Object... args) {
        long t = System.currentTimeMillis() - T0;
        String prefix = String.format("[%s][%dms] ", memberId, t);
        System.out.printf(prefix + fmt + "%n", args);
    }

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

        String proposeArg = null;           // e.g., --propose=LEADER_M5
        Long triggerAfterMs = null;         // e.g., --trigger-after=3000

        for (String a : args) {
            if (a != null && a.startsWith("--propose=")) {
                proposeArg = a.substring("--propose=".length()).trim();
            } else if (a != null && a.startsWith("--trigger-after=")) {
                try {
                    triggerAfterMs = Long.parseLong(a.substring("--trigger-after=".length()).trim());
                } catch (NumberFormatException ignore) { /* leave null */ }
            }
        }

        profile = Profile.fromString(profileArg);

        try {
            config = NetworkConfig.load();
            port = config.getPort(memberId);
            log("Starting on port %d with profile: %s", port, profileArg);

            ServerSocket serverSocket = new ServerSocket(port);
            PaxosHandler paxos = new PaxosHandler(memberId, config, profile);

            // Schedule a proposal AFTER startup (no second process, satisfies "launch then trigger")
            if (proposeArg != null && triggerAfterMs != null && triggerAfterMs >= 0) {
                final String v = proposeArg;
                final long delay = triggerAfterMs;
                new Thread(() -> {
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                    log("Triggering scheduled proposal after %d ms: %s", delay, v);
                    paxos.propose(v);
                }, "scheduled-proposer").start();
            }

            // Keep your interactive stdin thread if you want, that’s fine:
            startInteractiveProposer(paxos);

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
                log("Type a value to propose (ex: LEADER_M5). Commands: /help, /q");
                String line;
                while ((line = br.readLine()) != null) {
                    String v = line.trim();
                    if (v.isEmpty()) continue;
                    if (v.equalsIgnoreCase("/help")) {
                        log("Enter a value to propose (e.g., LEADER_M3). Commands: /q to stop input on this node.");
                        continue;
                    }
                    if (v.equalsIgnoreCase("/q") || v.equalsIgnoreCase("exit")) {
                        log("Stopping interactive proposer input for this node.");
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
