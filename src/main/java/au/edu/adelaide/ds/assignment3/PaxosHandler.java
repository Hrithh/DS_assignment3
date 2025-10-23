package au.edu.adelaide.ds.assignment3;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // For tracking majority
    private int quorumSize;

    public PaxosHandler(String memberId, NetworkConfig config, Profile profile) {
        this.memberId = memberId;
        this.config = config;
        this.profile = profile;
        this.quorumSize = (config.getAllMembers().size() / 2) + 1;
    }

    public void handleMessage(String rawJson) {
        profile.simulateNetworkDelay();
        if (profile.shouldDrop()) {
            System.out.printf("[%s] (DROP) Ignoring message due to failure profile%n", memberId);
            return;
        }

        Message msg = gson.fromJson(rawJson, Message.class);
        switch (msg.type) {
            case "PREPARE":
                handlePrepare(msg);
                break;
            case "PROMISE":
                handlePromise(msg);
                break;
            case "ACCEPT_REQUEST":
                handleAcceptRequest(msg);
                break;
            case "ACCEPTED":
                handleAccepted(msg);
                break;
            default:
                System.out.printf("[%s] Received unknown message type: %s%n", memberId, msg.type);
        }
    }

    private void handlePrepare(Message msg) {
        if (promisedN == null || compareProposal(msg.proposalNum, promisedN) > 0) {
            promisedN = msg.proposalNum;

            Message promise = new Message("PROMISE", memberId, msg.proposalNum, acceptedValue);
            promise.proposalNum = msg.proposalNum; // same proposalNum

            if (acceptedN != null && acceptedValue != null) {
                promise.value = acceptedValue;
            }

            sendTo(msg.sender, gson.toJson(promise));
            log("[ACCEPTOR][PROMISE] to=" + msg.sender + " n=" + msg.proposalNum);
        } else {
            log("[ACCEPTOR][IGNORE] n=" + msg.proposalNum + " < promisedN=" + promisedN);
        }
    }

    private void handlePromise(Message msg) {
        // TODO: For now, just print promise. We'll collect and act on it later.
        log("[PROPOSER][PROMISE RECEIVED] from=" + msg.sender + " n=" + msg.proposalNum + " v=" + msg.value);
    }

    private void handleAcceptRequest(Message msg) {
        if (promisedN == null || compareProposal(msg.proposalNum, promisedN) >= 0) {
            promisedN = msg.proposalNum;
            acceptedN = msg.proposalNum;
            acceptedValue = msg.value;

            Message accepted = new Message("ACCEPTED", memberId, acceptedN, acceptedValue);
            sendToAllExceptSelf(gson.toJson(accepted));

            log("[ACCEPTOR][ACCEPTED] value=" + acceptedValue + " n=" + acceptedN);
        } else {
            log("[ACCEPTOR][REJECTED] n=" + msg.proposalNum + " < promisedN=" + promisedN);
        }
    }

    private void handleAccepted(Message msg) {
        if (consensusReached) return;

        String val = msg.value;
        acceptedCounts.put(val, acceptedCounts.getOrDefault(val, 0) + 1);

        if (acceptedCounts.get(val) >= quorumSize) {
            consensusReached = true;
            System.out.printf("[%s][LEARNER][CONSENSUS] winner=%s proposal=%s%n", memberId, val, msg.proposalNum);
        }
    }

    private void sendTo(String targetMember, String messageJson) {
        try {
            int port = config.getPort(targetMember);
            Socket socket = new Socket("localhost", port);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            out.write(messageJson);
            out.newLine();
            out.flush();
            socket.close();
        } catch (Exception e) {
            System.err.printf("[%s] Failed to send to %s: %s%n", memberId, targetMember, e.getMessage());
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

    // Compares "3.1" vs "2.5"
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
