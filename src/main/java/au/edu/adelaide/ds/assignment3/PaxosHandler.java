package au.edu.adelaide.ds.assignment3;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles all Paxos protocol logic for a single CouncilMember node.
 * <p>
 * This class encapsulates the roles of proposer, acceptor, and learner
 * within the Paxos algorithm. It manages message parsing, proposal
 * generation, state tracking, and quorum-based decision making.
 * </p>
 */
public class PaxosHandler {
    private final String memberId;
    private final NetworkConfig config;
    private final Profile profile;
    private final Gson gson = new Gson();

    //Acceptor state
    private String promisedN = null;
    private String acceptedN = null;
    private String acceptedValue = null;

    //Learner state
    private final Map<String, Integer> acceptedCounts = new HashMap<>();
    private boolean consensusReached = false;

    //Majority tracking
    private final int quorumSize;

    //Proposer state
    private String currentProposalN = null;
    private String myProposedValue = null;
    private int promises = 0;
    private String highestAcceptedNSeen = null;
    private String valueSuggestedByAcceptors = null;
    private int localRound = 0;

    /**
     * Constructs a new PaxosHandler for a CouncilMember.
     *
     * @param memberId unique identifier for this node
     * @param config   network configuration containing all peers and ports
     * @param profile  reliability/latency behavior profile for network simulation
     */
    public PaxosHandler(String memberId, NetworkConfig config, Profile profile) {
        this.memberId = memberId;
        this.config = config;
        this.profile = profile;
        this.quorumSize = (config.getAllMembers().size() / 2) + 1;
    }

    // -----------------------------
    // Local logging with timestamp
    // -----------------------------
    /**
     * Prints a timestamped, node-prefixed log message for this PaxosHandler.
     *
     * @param fmt  the message format string
     * @param args arguments for message formatting
     */
    private void log(String fmt, Object... args) {
        long t = System.currentTimeMillis() - CouncilMember.T0;
        String prefix = String.format("[%s][%dms] ", memberId, t);
        System.out.printf(prefix + fmt + "%n", args);
    }

    // -----------------------------
    // Message handling entry point
    // -----------------------------
    /**
     * Entry point for handling an incoming Paxos message.
     * <p>
     * Deserializes the JSON, applies simulated delay/drop based on profile,
     * and delegates to the appropriate role handler.
     * </p>
     *
     * @param rawJson raw JSON string received from a peer node
     */
    public void handleMessage(String rawJson) {
        profile.simulateNetworkDelay();
        if (profile.shouldDrop()) {
            log("(DROP) Ignoring message due to failure profile");
            return;
        }

        Message msg = gson.fromJson(rawJson, Message.class);
        Message.MessageType type = msg.getType();

        switch (type) {
            case PREPARE:
                handlePrepare(msg);
                break;
            case PROMISE:
                handlePromise(msg);
                break;
            case ACCEPT_REQUEST:
                handleAcceptRequest(msg);
                break;
            case ACCEPTED:
                handleAccepted(msg);
                break;
            default:
                log("Unknown message type: %s", type);
        }
    }

    // -----------------------------
    // Proposer logic
    // -----------------------------
    /**
     * Extracts the numeric component of this member's ID ("M4" → 4).
     *
     * @return integer ID component of this member
     */
    private int myNumericId() {
        return Integer.parseInt(memberId.replaceAll("\\D+", ""));
    }

    /**
     * Generates the next unique proposal number for this proposer.
     * <p>
     * Follows the format {@code round.memberId} ("2.4").
     * </p>
     *
     * @return proposal number string
     */
    private String nextProposalNumber() {
        localRound++;
        return localRound + "." + myNumericId();
    }

    /**
     * Initiates a new proposal round for the given value.
     * <p>
     * Sends PREPARE messages to all peers and waits for quorum PROMISE responses.
     * Automatically retries with a higher proposal number if no quorum is reached
     * within a timeout window.
     * </p>
     *
     * @param value the proposed value ("LEADER_M5")
     */
    public synchronized void propose(String value) {
        if (consensusReached) {
            log("[PROPOSER] Consensus already reached; ignoring new proposal.");
            return;
        }
        this.myProposedValue = value;
        this.currentProposalN = nextProposalNumber();
        this.promises = 0;
        this.highestAcceptedNSeen = null;
        this.valueSuggestedByAcceptors = null;

        Message m = new Message();
        m.setType(Message.MessageType.PREPARE);
        m.setSenderId(memberId);
        m.setProposalNumber(currentProposalN);
        sendToAllExceptSelf(gson.toJson(m));
        log("[PROPOSER][PREPARE] n=%s v=%s", currentProposalN, myProposedValue);

        // timeout & re-propose with higher n if no quorum in time
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            synchronized (PaxosHandler.this) {
                if (!consensusReached && currentProposalN != null && promises < quorumSize) {
                    log("[PROPOSER] Timeout waiting for quorum; re-proposing with higher n");
                    propose(this.myProposedValue);
                }
            }
        }, "proposer-timeout").start();
    }

    /**
     * Handles a PROMISE message received in response to a PREPARE.
     * <p>
     * Tracks received promises and determines whether to proceed with
     * an ACCEPT_REQUEST once quorum is achieved.
     * </p>
     *
     * @param msg the PROMISE message received from an acceptor
     */
    private synchronized void handlePromise(Message msg) {
        if (currentProposalN == null || !currentProposalN.equals(msg.getProposalNumber())) {
            log("[PROPOSER][PROMISE] ignoring: for different proposal n=%s", msg.getProposalNumber());
            return;
        }
        promises++;

        String prevN = msg.getPrevAcceptedN();
        String prevV = msg.getValue();
        if (prevN != null) {
            if (highestAcceptedNSeen == null || compareProposal(prevN, highestAcceptedNSeen) > 0) {
                highestAcceptedNSeen = prevN;
                valueSuggestedByAcceptors = prevV;
            }
        }

        log("[PROPOSER][PROMISE RECEIVED] from=%s count=%d/%d%s",
                msg.getSenderId(), promises, quorumSize,
                (prevN != null ? (" prev=(" + prevN + "," + prevV + ")") : ""));

        if (promises >= quorumSize) {
            String valueToPropose = (valueSuggestedByAcceptors != null)
                    ? valueSuggestedByAcceptors
                    : myProposedValue;

            Message acc = new Message();
            acc.setType(Message.MessageType.ACCEPT_REQUEST);
            acc.setSenderId(memberId);
            acc.setProposalNumber(currentProposalN);
            acc.setValue(valueToPropose);

            sendToAllExceptSelf(gson.toJson(acc));
            log("[PROPOSER][ACCEPT_REQUEST] n=%s v=%s", currentProposalN, valueToPropose);
        }
    }

    // -----------------------------
    // Acceptor logic
    // -----------------------------
    /**
     * Handles a PREPARE message received from a proposer.
     * <p>
     * Replies with a PROMISE if this proposal number is higher than any previously
     * promised number; otherwise, the message is ignored.
     * </p>
     *
     * @param msg the PREPARE message received
     */
    private synchronized void handlePrepare(Message msg) {
        String proposalNum = msg.getProposalNumber();
        String sender = msg.getSenderId();

        if (promisedN == null || compareProposal(proposalNum, promisedN) > 0) {
            promisedN = proposalNum;

            Message promise = new Message();
            promise.setType(Message.MessageType.PROMISE);
            promise.setSenderId(memberId);
            promise.setProposalNumber(proposalNum);
            promise.setValue(acceptedValue);
            promise.setPrevAcceptedN(acceptedN);

            sendTo(sender, gson.toJson(promise));
            log("[ACCEPTOR][PROMISE] to=%s n=%s%s", sender, proposalNum,
                    (acceptedN != null ? (" prev=(" + acceptedN + "," + acceptedValue + ")") : ""));
        } else {
            log("[ACCEPTOR][IGNORE] n=%s < promisedN=%s", proposalNum, promisedN);
        }
    }

    /**
     * Handles an ACCEPT_REQUEST message from a proposer.
     * <p>
     * If the proposal number is at least as large as any previously promised number,
     * the value is accepted and an ACCEPTED message is broadcast to all peers.
     * Otherwise, the request is rejected.
     * </p>
     *
     * @param msg the ACCEPT_REQUEST message received
     */
    private synchronized void handleAcceptRequest(Message msg) {
        String proposalNum = msg.getProposalNumber();
        String value = msg.getValue();

        if (promisedN == null || compareProposal(proposalNum, promisedN) >= 0) {
            promisedN = proposalNum;
            acceptedN = proposalNum;
            acceptedValue = value;

            Message accepted = new Message();
            accepted.setType(Message.MessageType.ACCEPTED);
            accepted.setSenderId(memberId);
            accepted.setProposalNumber(acceptedN);
            accepted.setValue(acceptedValue);

            sendToAllExceptSelf(gson.toJson(accepted));
            log("[ACCEPTOR][ACCEPTED] value=%s n=%s", acceptedValue, acceptedN);
        } else {
            log("[ACCEPTOR][REJECTED] n=%s < promisedN=%s", proposalNum, promisedN);
        }
    }

    // -----------------------------
    // Learner logic
    // -----------------------------
    /**
     * Handles an ACCEPTED message from another node.
     * <p>
     * Tracks how many nodes have accepted each value and declares
     * consensus once a quorum agrees on the same value.
     * </p>
     *
     * @param msg the ACCEPTED message received from a peer
     */
    private synchronized void handleAccepted(Message msg) {
        if (consensusReached) return;

        String value = msg.getValue();
        acceptedCounts.put(value, acceptedCounts.getOrDefault(value, 0) + 1);

        if (acceptedCounts.get(value) >= quorumSize) {
            consensusReached = true;
            log("CONSENSUS: %s has been elected Council President.", value);
            log("[LEARNER][CONSENSUS] value=%s proposal=%s", value, msg.getProposalNumber());
        }
    }

    // -----------------------------
    // Network utilities
    // -----------------------------
    /**
     * Sends a JSON-encoded message to a specific target member, retrying several times on failure.
     *
     * @param targetMember the member ID to send the message to
     * @param messageJson  serialized JSON message payload
     */
    private void sendTo(String targetMember, String messageJson) {
        String host = config.getHost(targetMember);
        int port = config.getPort(targetMember);

        int attempts = 5;
        for (int i = 1; i <= attempts; i++) {
            try (Socket socket = new Socket(host, port);
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                out.write(messageJson);
                out.newLine();
                out.flush();
                return; // success
            } catch (Exception e) {
                if (i == attempts) {
                    System.err.printf("[%s] Failed to send to %s after %d tries: %s%n",
                            memberId, targetMember, attempts, e.getMessage());
                } else {
                    try { Thread.sleep(200L * i); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /**
     * Sends a message to all peers in the configuration except this node.
     *
     * @param messageJson serialized JSON message payload
     */
    private void sendToAllExceptSelf(String messageJson) {
        for (String target : config.getAllMembers()) {
            if (!target.equals(memberId)) {
                sendTo(target, messageJson);
            }
        }
    }

    /**
     * Compares two proposal numbers of the form "round.memberId".
     *
     * @param a first proposal number string
     * @param b second proposal number string
     * @return positive if {@code a > b}, negative if {@code a < b}, or zero if equal
     */
    private int compareProposal(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int roundA = Integer.parseInt(aParts[0]);
        int idA = Integer.parseInt(aParts[1]);
        int roundB = Integer.parseInt(bParts[0]);
        int idB = Integer.parseInt(bParts[1]);

        if (roundA != roundB) return Integer.compare(roundA, roundB);
        return Integer.compare(idA, idB);
    }
}
