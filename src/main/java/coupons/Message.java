package coupons; // Declare the package containing the distributed coupon-system classes.

/**
 * Represents a message sent from one distributed process to another.
 *
 * <p>
 * This class models communication from process Pi to process Pj. A message
 * records its source process, destination process, message type, and
 * application-level content.</p>
 */
// Pi --------------------> Pj
// Message
public class Message { // Represent one message exchanged between distributed processes.

    private final int sourceProcessId; // store the id of the sending process pi
    private final int destinationProcessId; // store the id of the receiving process pj
    private final String messageType; // store the type of the message
    private final String content; // store the application-level content of the message
    private final DistributedEvent sendEvent; // store the event at which process pi sends this message
    private final DistributedEvent receiveEvent; // store the event at which process pj receives this message

    /**
     * Constructs a message sent from one distributed process to another.
     *
     * @param sourceProcessId the identifier of the sending process Pi
     * @param destinationProcessId the identifier of the receiving process Pj
     * @param messageType the tag identifying the message type
     * @param content the application-level information carried by the message
     */
    public Message( // Construct one message sent from process Pi to process Pj.
            int sourceProcessId, // Receive the identifier of the sending process Pi.
            int destinationProcessId, // Receive the identifier of the receiving process Pj.
            String messageType, // Receive the tag identifying the message type.
            String content, // Receive the application-level information carried by the message.
            DistributedEvent sendEvent, // Receive the event at which Pi sends this message.
            DistributedEvent receiveEvent // Receive the event at which Pj receives this message.
    ) { // Begin the Message constructor.
        this.sourceProcessId = sourceProcessId; // Store the identifier of the sending process.
        this.destinationProcessId = destinationProcessId; // Store the identifier of the receiving process.
        this.messageType = messageType; // Store the message type.
        this.content = content; // Store the application-level message content.
        this.sendEvent = sendEvent; // Store the event at which process Pi sends this message.
        this.receiveEvent = receiveEvent; // Store the event at which process Pj receives this message.
    } // End the Message constructor.

    /**
     * Returns the identifier of the process that sent this message.
     *
     * @return the source process identifier
     */
    public int getSourceProcessId() { // Provide read-only access to the source process identifier.
        return sourceProcessId; // Return the identifier of the sending process Pi.
    } // End the getSourceProcessId method.

    /**
     * Returns the identifier of the process receiving this message.
     *
     * @return the destination process identifier
     */
    public int getDestinationProcessId() { // Provide read-only access to the destination process identifier.
        return destinationProcessId; // Return the identifier of the receiving process Pj.
    } // End the getDestinationProcessId method.

    /**
     * Returns the message type.
     *
     * @return the tag identifying this message type
     */
    public String getMessageType() { // Provide read-only access to the message type.
        return messageType; // Return the tag identifying this message.
    } // End the getMessageType method.

    /**
     * Returns the application-level content carried by this message.
     *
     * @return the content carried by this message
     */
    public String getContent() { // Provide read-only access to the message content.
        return content; // Return the application-level information carried by this message.
    } // End the getContent method.

    /**
     * Returns the event at which the source process sends this message
     *
     * @return the send event for this message
     */
    public DistributedEvent getSendEvent() { // Provide read-only access to this message's send event.
        return sendEvent; // Return the event at which process Pi sends this message.
    } // End the getSendEvent method.

    /**
     * Returns the event at which the destination process receives this message.
     *
     * @return the receive event for this message
     */
    public DistributedEvent getReceiveEvent() { // Provide read-only access to this message's receive event.
        return receiveEvent; // Return the event at which process Pj receives this message.
    } // End the getReceiveEvent method.

} // End the Message class.
