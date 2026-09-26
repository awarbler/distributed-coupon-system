package edu.utexas.ece382n.coupons; // Declare the package that contains the coupon-system classes.

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one process in the distributed coupon system.
 *
 * <p>
 * Each process has its own process identifier and represents one participant in
 * the distributed computation.</p>
 */
public class DistributedProcess { // Represent one process Pi in the distributed system.

    private final int processId; // Store the numeric identifier for this process Pi.
    private final String processName; // Store the application-level name of this distributed process.
    private final List<DistributedEvent> events; // Store the list of events that occur at this distributed process.

    /**
     * Constructs a distributed process with a process identifier and
     * application-level name.
     *
     * <p>
     * In Dr. Garg's notation, the process identifier corresponds to the index
     * in Pi, while the process name identifies the process's role in the coupon
     * application.</p>
     *
     * @param processId the numeric identifier assigned to this process
     * @param processName the application-level name of this process
     */
    public DistributedProcess(int processId, String processName) { // Construct one process Pi with its process identifier and name.
        this.processId = processId; // Store the numeric identifier belonging to this process.
        this.processName = processName; // Store the application-level name of this distributed process.
        this.events = new ArrayList<>(); // Initialize the list of events for this distributed process.

    }

    /**
     * Adds an event to this process's local event sequence.
     *
     * @param event the distributed event that occurs at this process.
     *
     */
    public void addEvent(DistributedEvent event) {
        events.add(event); // append the event to this process local event seq

    }

    /**
     * Returns the events that have occurred at this process.
     *
     * @return the events belonging to this process
     */
    public List<DistributedEvent> getEvents() { // Provide read-only access to this process's event sequence.
        return List.copyOf(events); // Return an unmodifiable copy of this process's events.
    } // End the getEvents method.

    /**
     * Returns this process's numeric identifier.
     *
     * @return the numeric identifier assigned to this process
     */
    public int getProcessId() { // Provide read-only access to this process's numeric identifier.
        return processId; // Return the numeric identifier belonging to this process.
    } // End the getProcessId method.

    /**
     * Returns this process's application-level name.
     *
     * @return the application-level name assigned to this process
     */
    public String getProcessName() { // Provide read-only access to this process's application-level name.
        return processName; // Return the application-level name belonging to this distributed process.
    } // End the getProcessName method.

}
