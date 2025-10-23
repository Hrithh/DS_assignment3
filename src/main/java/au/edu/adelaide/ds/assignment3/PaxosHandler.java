package au.edu.adelaide.ds.assignment3;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class PaxosHandler {
    private final String memberId;
    private final NetworkConfig config;
    private final Profile profile;
    private final Gson gson = new Gson();

    // Acceptor state
    private String promisedN = null;
    private String acceptedN = null;
    private String acceptedValue = null;

    // Learner state
    private final Map<String, Integer> acceptedCounts = new HashMap<>();
    private boolean consensusReached = false;

    // Majority tracking
    private final int quorumSize;

    // Proposer state
    private String currentProposalN = null;
    private String myProposedValue = null;
    private int promises = 0;
    private String highestAcceptedNSeen = null;
    private String valueSuggestedByAcceptors = null;
    private int localRound = 0;

    public PaxosHandler(String memberId, NetworkConfig config, Profile profile) {
        this.memberId = memberId;
        this.config = config;
        this.profile = profile;
        this.quorumSize = (config.getAllMembers().size() / 2) + 1;
    }

    // -----------------------------
    // Message handling entry point
    // -----------------------------
    public void handleMessage(String rawJson) {
        profile.simulateNetworkDelay();
        if (profile.shouldDrop()) {
            System.out.printf("[%s] (DROP) Ignoring message due to failure profile%n", memberId);
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
                System.out.printf("[%s] Unknown message type: %s%n", memberId, type);
        }
    }

    // -----------------------------
    // Proposer logic
    // -----------------------------
    private int myNumericId() {
        return Integer.parseInt(memberId.replaceAll("\\D+", ""));
    }

    private String nextProposalNumber() {
        localRound++;
        return localRound + "." + myNumericId();
    }

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
        log("[PROPOSER][PREPARE] n=" + currentProposalN + " v=" + myProposedValue);

        // timeout & re-propose with higher n if no quorum in time
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            synchronized (PaxosHandler.this) {
                if (!consensusReached && currentProposalN != null && promises < quorumSize) {
                    log("[PROPOSER] Timeout waiting for quorum; re-proposing with higher n");
                    // Re-use the same value; increment round automatically
                    propose(this.myProposedValue);
                }
            }
        }, "proposer-timeout").start();
    }

    private synchronized void handlePromise(Message msg) {
        if (currentProposalN == null || !currentProposalN.equals(msg.getProposalNumber())) {
            log("[PROPOSER][PROMISE] ignoring: for different proposal n=" + msg.getProposalNumber());
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

        log("[PROPOSER][PROMISE RECEIVED] from=" + msg.getSenderId() +
                " count=" + promises + "/" + quorumSize +
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
            log("[PROPOSER][ACCEPT_REQUEST] n=" + currentProposalN + " v=" + valueToPropose);
        }
    }

    // -----------------------------
    // Acceptor logic
    // -----------------------------
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
            log("[ACCEPTOR][PROMISE] to=" + sender + " n=" + proposalNum +
                    (acceptedN != null ? (" prev=(" + acceptedN + "," + acceptedValue + ")") : ""));
        } else {
            log("[ACCEPTOR][IGNORE] n=" + proposalNum + " < promisedN=" + promisedN);
        }
    }

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
            log("[ACCEPTOR][ACCEPTED] value=" + acceptedValue + " n=" + acceptedN);
        } else {
            log("[ACCEPTOR][REJECTED] n=" + proposalNum + " < promisedN=" + promisedN);
        }
    }

    // -----------------------------
    // Learner logic
    // -----------------------------
    private synchronized void handleAccepted(Message msg) {
        if (consensusReached) return;

        String value = msg.getValue();
        acceptedCounts.put(value, acceptedCounts.getOrDefault(value, 0) + 1);

        if (acceptedCounts.get(value) >= quorumSize) {
            consensusReached = true;
            // Assignment-style output plus detailed line
            System.out.printf("CONSENSUS: %s has been elected Council President.%n", value);
            System.out.printf("[%s][LEARNER][CONSENSUS] value=%s proposal=%s%n",
                    memberId, value, msg.getProposalNumber());
        }
    }

    // -----------------------------
    // Network utilities
    // -----------------------------
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

    private void sendToAllExceptSelf(String messageJson) {
        for (String target : config.getAllMembers()) {
            if (!target.equals(memberId)) {
                sendTo(target, messageJson);
            }
        }
    }

    private void log(String msg) {
        System.out.printf("[%s] %s%n", memberId, msg);
    }

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
