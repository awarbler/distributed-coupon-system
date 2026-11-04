package coupons.distributed.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class SimulationTest {
    @Test
    void validEventKeepsItsFields(){
        //  label a, owner 0, description Purchase recorded.
        SimEvent event = new SimEvent("a", 0, "Purchase recorded");
        assertEquals("a", event.eventId());
        assertEquals(0, event.processId());
        assertEquals("Purchase recorded", event.description());
    }

    @Test 
    void negativeProcessIdIsRejected(){
        assertThrows(IllegalArgumentException.class, () -> new SimEvent(
            "a", -1, "Purchase recorded"));
        }
    @Test 
    void emptyEventIdIsRejected(){
        assertThrows(IllegalArgumentException.class, () -> new SimEvent(
            "", 0, "Purchase recorded"));
    }
    @Test // Register the whitespace-only case separately.
    void whitespaceEventIdIsRejected() { // Spaces alone must not identify an event.
        assertThrows(IllegalArgumentException.class, // Require the same exception type.
            () -> new SimEvent("   ", 0, "Purchase recorded")); // Supply three spaces rather than an empty string.
    } // Finish the whitespace-ID test.

    @Test // Register the missing-value case separately.
    void nullEventIdIsRejected() { // Distinguish a missing string from an empty one.
        assertThrows(IllegalArgumentException.class, // Require deliberate input rejection.
            () -> new SimEvent(null, 0, "Purchase recorded")); // Supply no event-ID string.
    } // Finish the null-ID test.

}
