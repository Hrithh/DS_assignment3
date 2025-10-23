package au.edu.adelaide.ds.assignment3;

/**
 * Represents a Paxos message that will be serialized and sent between nodes via TCP.
 */
public class Message {
    public enum MessageType {
        PREPARE,
        PROMISE,
        ACCEPT_REQUEST,
        ACCEPTED,
        DECIDE,
        NACK
    }

    private MessageType type;
    private int proposalNumber;
    private String value;
    private String senderId;

    public Message() {}

    public Message(MessageType type, int proposalNumber, String value, String senderId) {
        this.type = type;
        this.proposalNumber = proposalNumber;
        this.value = value;
        this.senderId = senderId;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public int getProposalNumber() {
        return proposalNumber;
    }

    public void setProposalNumber(int proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", proposalNumber=" + proposalNumber +
                ", value='" + value + '\'' +
                ", senderId='" + senderId + '\'' +
                '}';
    }
}
