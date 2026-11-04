package coupons.distributed.learning; // Match the source directory's package.

public record SimEvent(String eventId, int processId, String description) { // Declare the event's data.

    public SimEvent { // Validate constructor arguments before the record is created.
        if (processId < 0) { // Check whether the supplied process ID is invalid.
            throw new IllegalArgumentException("processId must be nonnegative"); // Reject invalid construction.
        } // Finish the conditional check.

        if (eventId == null || eventId.isBlank()) { // Detect a missing, empty, or whitespace-only ID.
            throw new IllegalArgumentException("eventId must not be blank"); // Reject the invalid event.
        } // Finish the event-ID check.
    }
}
