package au.edu.adelaide.ds.assignment3;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;

public class ProposerClient {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java ProposerClient <ProposerID> <network.config path>");
            return;
        }

        String proposerId = args[0];
        String configPath = args[1];

        NetworkConfig config = NetworkConfig.load();
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();

        System.out.print("Enter proposal number (e.g. 1.0): ");
        String proposalNum = scanner.nextLine();

        System.out.print("Enter value to propose: ");
        String value = scanner.nextLine();

        Message prepare = new Message();
        prepare.setType(Message.MessageType.PREPARE);
        prepare.setProposalNumber(proposalNum);
        prepare.setSenderId(proposerId);
        prepare.setValue(value); // optional for PREPARE

        String msgJson = gson.toJson(prepare);

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
