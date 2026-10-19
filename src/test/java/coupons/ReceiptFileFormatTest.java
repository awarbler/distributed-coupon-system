package coupons;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptFileFormatTest {
 @Test void detectsHeicWithJpegExtension(@TempDir Path dir)throws Exception {
  byte[] bytes=new byte[32];System.arraycopy("ftypheic".getBytes(StandardCharsets.US_ASCII),0,bytes,4,8);
  var file=dir.resolve("photo.HEIC.jpeg");Files.write(file,bytes);
  assertTrue(ReceiptFileFormat.isHeif(file));assertTrue(ReceiptFileFormat.isHeif(bytes));
 }
 @Test void ordinaryJpegAndShortDataAreNotHeif() {
  assertFalse(ReceiptFileFormat.isHeif(new byte[]{(byte)255,(byte)216,(byte)255}));
  assertFalse(ReceiptFileFormat.isHeif(new byte[32]));
 }
}
