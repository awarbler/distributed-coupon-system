package coupons;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class CalendarWeeksTest {
 @Test void octoberWeeksAreClippedToMonthAndEndSaturday() {
  int[] starts={1,4,11,18,25}; int[] ends={3,10,17,24,31};
  for(int i=0;i<5;i++) for(int d=starts[i];d<=ends[i];d++) {
   var day=LocalDate.of(2026,10,d);
   assertEquals(LocalDate.of(2026,10,starts[i]),ReceiptWorksheetReport.weekStart(day));
   assertEquals(LocalDate.of(2026,10,ends[i]),ReceiptWorksheetReport.weekEnd(day));
   assertEquals("WEEK "+(i+1),ReceiptWorksheetReport.weekLabel(day));
  }
 }
}
