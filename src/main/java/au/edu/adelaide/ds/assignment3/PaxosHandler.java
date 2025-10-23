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

    private void handlePrepare(Message msg) {
        String proposalNum = msg.getProposalNumber();
        String sender = msg.getSenderId();

        if (promisedN == null || compareProposal(proposalNum, promisedN) > 0) {
            promisedN = proposalNum;

            Message promise = new Message();
            promise.setType(Message.MessageType.PROMISE);
            promise.setSenderId(memberId);
            promise.setProposalNumber(proposalNum);
            promise.setValue(acceptedValue);

            sendTo(sender, gson.toJson(promise));
            log("[ACCEPTOR][PROMISE] to=" + sender + " n=" + proposalNum);
        } else {
            log("[ACCEPTOR][IGNORE] n=" + proposalNum + " < promisedN=" + promisedN);
        }
    }

    private void handlePromise(Message msg) {
        log("[PROPOSER][PROMISE RECEIVED] from=" + msg.getSenderId()
                + " n=" + msg.getProposalNumber()
                + " v=" + msg.getValue());
        // TODO: Future step: collect promises, then send ACCEPT_REQUEST
    }

    private void handleAcceptRequest(Message msg) {
        String proposalNum = msg.getProposalNumber();
        String sender = msg.getSenderId();
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

    private void handleAccepted(Message msg) {
        if (consensusReached) return;

        String value = msg.getValue();
        acceptedCounts.put(value, acceptedCounts.getOrDefault(value, 0) + 1);

        if (acceptedCounts.get(value) >= quorumSize) {
            consensusReached = true;
            System.out.printf("[%s][LEARNER][CONSENSUS] value=%s proposal=%s%n",
                    memberId, value, msg.getProposalNumber());
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
