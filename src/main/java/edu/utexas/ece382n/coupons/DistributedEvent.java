package edu.utexas.ece382n.coupons; // Declare the package containing the distributed coupon-system classes.

/**
 * Represent one event that occurs during a distributed computation.
 * <p>
 * An event belongs to a distributed process and represents an action that
 * occurs at that process.</p>
 */
public class DistributedEvent { // REpresents on event in the distributed computation
    // stores the label used to identify this event a distributed exectuition

    private final String eventId;
    // store teh identifier of the process piwhere this event occures
    private final int processId;
    // stor the application levelaction represented by this event
    private final String description;

    /**
     * Constructs an event belonging to a distributed process.
     *
     * @param eventId the label identifying this event
     * @param processId the identifier of the process where this event occurs
     * @param description the application-level action represented by this event
     */
    public DistributedEvent( // contruct one event occurring at process pi
            String eventId, // rec the label identifying this event
            int processId, // rec the identifier for the process where this event occures
            String description) { // begin the constructor body 
        this.eventId = eventId;
        this.processId = processId;
        this.description = description;
    }

    /**
     * Return this events identifying label.@interface
     *
     * @return the identifying label of this event
     *
     */
    public String getEventId() { // Provide read-only access to the event label.
        return eventId; // Return this event's identifying label.
    }

    /**
     *
     * Return the identifier of the process wher this event occurs,
     *
     * @return the identifier of the process where this event process
     */
    public int getProcessId() {
        return processId; // Return the identifier of the process where this event occurs.
    }

    /**
     * Returns the application-level description of this event.
     *
     * @return the description of the event
     */
    public String getDescription() { // Provide read-only access to the event description.
        return description; // Return the application-level action represented by this event.
    }

}
