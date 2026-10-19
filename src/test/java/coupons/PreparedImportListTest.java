package coupons;
import coupons.repository.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PreparedImportListTest {
 @Test void savedReceiptHidesAllMatchingExtractions(@TempDir Path dir)throws Exception {
  var repo=new InMemoryTransactionRepository();
  var first=dir.resolve("first.properties");var second=dir.resolve("second.properties");
  Files.writeString(first,"receipt.number=SavedReceipt\n");Files.copy(first,second);
  assertTrue(PreparedImportList.visible(first,repo));
  var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
  repo.save(UUID.randomUUID(),tx,new ReceiptMeta("Walgreens","SavedReceipt",null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("Owner"));
  assertFalse(PreparedImportList.visible(first,repo));assertFalse(PreparedImportList.visible(second,repo));
  assertTrue(Files.exists(first));assertTrue(Files.exists(second));
 }
 @Test void removeOnlyHidesEntryAndRetainsOriginals(@TempDir Path dir)throws Exception {
  var manifest=dir.resolve("receipt.properties");Files.writeString(manifest,"receipt.number=Unsaved\n");
  var image=dir.resolve("photo.jpeg");Files.write(image,new byte[]{1,2,3});
  var repo=new InMemoryTransactionRepository();assertTrue(PreparedImportList.visible(manifest,repo));
  PreparedImportList.remove(manifest);
  assertFalse(PreparedImportList.visible(manifest,repo));
  assertEquals("receipt.number=Unsaved\n",Files.readString(manifest));assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(image));
  assertEquals(0,repo.creationHistory().size());
 }
}
