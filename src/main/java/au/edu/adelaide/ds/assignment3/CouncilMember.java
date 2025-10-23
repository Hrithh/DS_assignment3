package au.edu.adelaide.ds.assignment3;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * The {@code CouncilMember} class represents one node in the distributed Paxos consensus system.
 * <p>
 * Each CouncilMember listens for incoming Paxos messages from peers,
 * participates as a proposer, acceptor, and learner, and can optionally
 * initiate proposals either interactively or automatically after a delay.
 * </p>
 */
public class CouncilMember {

    // --- Timing helper (for timestamped logs) ---
    static final long T0 = System.currentTimeMillis();

    /**
     * Logs a formatted message with the current member ID and milliseconds since startup.
     *
     * @param fmt  the message format string, as accepted by {@link String#format(String, Object...)}
     * @param args the arguments to substitute into the format string
     */
    public static void log(String fmt, Object... args) {
        long t = System.currentTimeMillis() - T0;
        String prefix = String.format("[%s][%dms] ", memberId, t);
        System.out.printf(prefix + fmt + "%n", args);
    }

    //Unique identifier of this CouncilMember
    public static String memberId;

    //Profile controlling simulated reliability, latency, and failure behavior.
    public static Profile profile;

    //Listening port for this node, loaded from {@code network.config}.
    public static int port;

    //Shared configuration describing all nodes in the network.
    public static NetworkConfig config;

    /**
     * Entry point for a CouncilMember process.
     * <p>
     * Initializes the node based on command‑line parameters, creates its server socket,
     * and spawns threads to handle incoming Paxos messages. It may also schedule a
     * proposal automatically after startup or allow manual proposals via standard input.
     * </p>
     *
     * @param args command‑line arguments:
     *             <ul>
     *                 <li>{@code <MemberID>} — e.g. {@code M4}</li>
     *                 <li>{@code --profile=<reliable|standard|latent|failure>}</li>
     *                 <li>(optional) {@code --propose=<VALUE>} — initial value to propose</li>
     *                 <li>(optional) {@code --trigger-after=<ms>} — delay before automatic proposal</li>
     *             </ul>
     */
    public static void main(String[] args) {
        // Args:
        //   <MemberID> --profile=<reliable|standard|latent|failure> [--propose=<VALUE>]
        if (args.length < 2 || !args[1].startsWith("--profile=")) {
            System.out.println("Usage: java CouncilMember <MemberID> --profile=<reliable|standard|latent|failure> [--propose=<VALUE>]");
            return;
        }

        memberId = args[0].trim();
        String profileArg = args[1].substring("--profile=".length()).trim();

        String proposeArg = null;           //--propose=LEADER_M5
        Long triggerAfterMs = null;         //--trigger-after=3000

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

            //Schedule a proposal AFTER startup (no second process, satisfies "launch then trigger")
            if (proposeArg != null && triggerAfterMs != null && triggerAfterMs >= 0) {
                final String v = proposeArg;
                final long delay = triggerAfterMs;
                new Thread(() -> {
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                    log("Triggering scheduled proposal after %d ms: %s", delay, v);
                    paxos.propose(v);
                }, "scheduled-proposer").start();
            }

            //Keep your interactive stdin thread if you want, that’s fine:
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
    /**
     * Starts a daemon thread that reads commands from standard input and allows
     * a user to manually propose values during runtime.
     * <p>
     * Supported commands:
     * <ul>
     *   <li>Type any non-empty string (e.g., {@code LEADER_M3}) to initiate a proposal.</li>
     *   <li>{@code /help} — display brief usage information.</li>
     *   <li>{@code /q} or {@code exit} — stop reading further input.</li>
     * </ul>
     * </p>
     *
     * @param paxos the {@link PaxosHandler} instance to which manual proposals are submitted
     */
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

        t.setDaemon(true);  //won’t block process exit
        t.start();
    }
}
