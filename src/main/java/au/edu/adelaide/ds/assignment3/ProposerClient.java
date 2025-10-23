package au.edu.adelaide.ds.assignment3;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * A simple standalone client that manually triggers the PREPARE phase of Paxos
 * by sending a PREPARE message to all other council members in the network.
 * <p>
 * Usage: {@code java ProposerClient <ProposerID> <network.config path>}
 * <br>
 * Example: {@code java ProposerClient M1 network.config}
 * </p>
 */
public class ProposerClient {

    /**
     * Entry point for the proposer client.
     * Prompts user to enter a proposal number and value, then sends
     * a PREPARE message to all council members (excluding self).
     *
     * @param args Command-line arguments: proposer ID and config file path
     * @throws Exception if network or config loading fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java ProposerClient <ProposerID> <network.config path>");
            return;
        }

        String proposerId = args[0];
        String configPath = args[1];

        //Load network configuration
        NetworkConfig config = NetworkConfig.load();
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();

        //Collect user input for proposal number and value
        System.out.print("Enter proposal number (e.g. 1.0): ");
        String proposalNum = scanner.nextLine();

        System.out.print("Enter value to propose: ");
        String value = scanner.nextLine();

        //Construct PREPARE message
        Message prepare = new Message();
        prepare.setType(Message.MessageType.PREPARE);
        prepare.setProposalNumber(proposalNum);
        prepare.setSenderId(proposerId);
        prepare.setValue(value); // optional for PREPARE

        String msgJson = gson.toJson(prepare);

        //Send PREPARE message to all members except self
        for (String target : config.getAllMembers()) {
            if (!target.equals(proposerId)) {
                int port = config.getPort(target);
                try (Socket socket = new Socket("localhost", port)) {
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    out.write(msgJson);
                    out.newLine();
                    out.flush();
                    System.out.printf("Sent PREPARE to %s (%d)\n", target, port);
                } catch (Exception e) {
                    System.err.printf("Failed to send to %s: %s\n", target, e.getMessage());
                }
            }
        }

        System.out.println("Prepare phase initiated.");
    }
}
