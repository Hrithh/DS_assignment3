package au.edu.adelaide.ds.assignment3;

/**
 * Represents a message exchanged between Paxos nodes.
 * <p>
 * Each message carries information relevant to a specific phase of the Paxos protocol,
 * such as proposal numbers, values, and previous acceptor state.
 * </p>
 */
public class Message {

    /**
     * Enumeration of all Paxos message types.
     * <ul>
     *     <li>{@code PREPARE} — sent by proposer to initiate a round</li>
     *     <li>{@code PROMISE} — sent by acceptor in response to PREPARE</li>
     *     <li>{@code ACCEPT_REQUEST} — sent by proposer to request acceptance of a value</li>
     *     <li>{@code ACCEPTED} — sent by acceptor upon accepting a proposal</li>
     *     <li>{@code DECIDE} — optional final decision notification (unused in basic Paxos)</li>
     * </ul>
     */
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

    /**
     * Returns the type of this Paxos message.
     *
     * @return the message type (PREPARE, PROMISE, etc.)
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Sets the type of this Paxos message.
     *
     * @param type the message type (e.g., PREPARE, PROMISE)
     */
    public void setType(MessageType type) {
        this.type = type;
    }

    /**
     * Returns the proposal number associated with this message.
     *
     * @return the proposal number string (e.g., "2.4")
     */
    public String getProposalNumber() {
        return proposalNumber;
    }

    /**
     * Sets the proposal number for this message.
     *
     * @param proposalNumber the proposal number to assign
     */
    public void setProposalNumber(String proposalNumber) {
        this.proposalNumber = proposalNumber;
    }

    /**
     * Returns the ID of the node that sent this message.
     *
     * @return the sender’s ID (e.g., "M3")
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Sets the ID of the sender of this message.
     *
     * @param senderId the sending node's identifier
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    /**
     * Returns the value being proposed or accepted in this message.
     *
     * @return the proposal value (e.g., "LEADER_M5"), or {@code null} if not applicable
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value to be proposed or accepted in this message.
     *
     * @param value the proposal value
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the proposal number of the previously accepted value (if any).
     * <p>
     * Used in PROMISE messages to inform proposers of earlier acceptances.
     * </p>
     *
     * @return the previously accepted proposal number, or {@code null}
     */
    public String getPrevAcceptedN() {
        return prevAcceptedN;
    }

    /**
     * Sets the previously accepted proposal number (used in PROMISE messages).
     *
     * @param prevAcceptedN the prior accepted proposal number
     */
    public void setPrevAcceptedN(String prevAcceptedN) {
        this.prevAcceptedN = prevAcceptedN;
    }
}
