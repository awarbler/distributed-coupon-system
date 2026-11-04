package coupons.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptPhotoMergeTest {
 @Test void stitchesOverlapInEitherUploadOrderWithoutDroppingRepeatedCoupons(){
  String top="Walgreens\nCREST\n03077206185 10.00 SALE\n2 @ 5.00\nREGULAR PRICE 6.29";
  String bottom="CREST\n03077206185 10.00 SALE\n2 @ 5.00\nREGULAR PRICE 6.29\nMFG CPN -2.00\nMFG CPN -2.00\nTOTAL 6.00";
  String expected=top+"\nMFG CPN -2.00\nMFG CPN -2.00\nTOTAL 6.00";
  assertEquals(expected,ReceiptPhotoMerge.join(top,bottom));assertEquals(expected,ReceiptPhotoMerge.join(bottom,top));
 }
 @Test void combinedPreviewParsesTheOverlapOnlyOnce(){
  String top="Walgreens\nCREST\n03077206185 10.00 SALE\n2 @ 5.00\nREGULAR PRICE 6.29";
  String bottom="CREST\n03077206185 10.00 SALE\n2 @ 5.00\nREGULAR PRICE 6.29\nMFG CPN -2.00\nMFG CPN -2.00\nTOTAL 6.00";
  var merged=ReceiptPhotoMerge.combine(java.util.List.of(ReceiptImport.parse("top",top,true),ReceiptImport.parse("bottom",bottom,true)));
  assertEquals(1,merged.size());assertEquals(1,((java.util.List<?>)merged.getFirst().get("items")).size());
  assertEquals(2,((java.util.List<?>)merged.getFirst().get("deductions")).size());
 }
 @Test void doesNotMergeUnrelatedReceiptsWithTheSameStoreHeader(){
  assertNull(ReceiptPhotoMerge.join("Walgreens\n5011 SLAUGHTER LN\n512-292-1340\nCREST", "Walgreens\n5011 SLAUGHTER LN\n512-292-1340\nTIDE"));
 }
 @Test void doesNotGuessWhenOverlappingPricesDisagree(){
  assertNull(ReceiptPhotoMerge.join("CREST\n03077206185 10.00 SALE\n2 @ 5.00", "CREST\n03077206185 19.00 SALE\n2 @ 5.00"));
 }
}
