package au.edu.adelaide.ds.assignment3;

public class Message {
    public String type;
    public String sender;
    public String proposalNum;  // Format: "3.1"
    public String value;

    public Message(String type, String sender, String proposalNum, String value) {
        this.type = type;
        this.sender = sender;
        this.proposalNum = proposalNum;
        this.value = value;
    }
}
