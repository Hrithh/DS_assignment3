package au.edu.adelaide.ds.assignment3;

public class Message {

    public enum MessageType {
        PREPARE,
        PROMISE,
        ACCEPT_REQUEST,
        ACCEPTED,
        DECIDE
    }

    private MessageType type;
    private String proposalNumber;
    private String senderId;
    private String value;
    private String prevAcceptedN;

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getProposalNumber() {
        return proposalNumber;
    }

    public void setProposalNumber(String proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getPrevAcceptedN() {
        return prevAcceptedN;
    }

    public void setPrevAcceptedN(String prevAcceptedN) {
        this.prevAcceptedN = prevAcceptedN;
    }
}
