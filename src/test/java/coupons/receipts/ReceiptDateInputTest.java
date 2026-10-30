package coupons.receipts;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptDateInputTest {
    @Test
    void supportsRequestedDateFormsUsingMonthFirstForShortDates() {
        LocalDate october4 = LocalDate.of(2026, 10, 4);
        for (String input : List.of("10/4/26", "10/04/2026", "26/10/4", "10-4-26", "October 4, 2026", "2026-10-04")) {
            assertEquals(List.of(october4), ReceiptDateInput.candidates(input), input);
        }
        assertEquals(List.of(LocalDate.of(2026, 10, 5)), ReceiptDateInput.candidates("10-5-2026"));
    }

    @Test
    void rejectsInvalidCalendarDatesWithoutReinterpretingThem() {
        assertTrue(ReceiptDateInput.candidates("2/30/2026").isEmpty());
        assertTrue(ReceiptDateInput.candidates("2/29/2025").isEmpty());
        assertTrue(ReceiptDateInput.candidates("not a date").isEmpty());
        assertEquals(List.of(LocalDate.of(2026, 10, 4)), ReceiptDateInput.candidates("10/4/26"));
    }
}
