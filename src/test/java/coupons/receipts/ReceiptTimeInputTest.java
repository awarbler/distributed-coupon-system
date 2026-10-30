package coupons.receipts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptTimeInputTest {
    @Test void normalizesCompactAndColonTimesIncludingNoonAndMidnight() {
        assertEquals("9:40 AM", ReceiptTimeInput.display("940 am"));
        assertEquals("9:40 AM", ReceiptTimeInput.display("0940 AM "));
        assertEquals("9:40 PM", ReceiptTimeInput.display("940pm"));
        assertEquals("9:40 PM", ReceiptTimeInput.display("21:40"));
        assertEquals("12:00 AM", ReceiptTimeInput.display("12 am"));
        assertEquals("12:00 PM", ReceiptTimeInput.display("12 pm"));
        assertTrue(ReceiptTimeInput.parse("25:99").isEmpty());
        assertTrue(ReceiptTimeInput.parse("13 am").isEmpty());
    }
}
