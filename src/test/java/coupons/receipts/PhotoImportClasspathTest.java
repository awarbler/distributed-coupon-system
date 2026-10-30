package coupons.receipts;
import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;
class PhotoImportClasspathTest {
 @Test void imageImporterLoadsWithoutPdfLibraries() throws Exception {
  try(var loader=new URLClassLoader(new java.net.URL[]{Path.of("target/classes").toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
   assertNotNull(loader.loadClass("coupons.receipts.WalgreensImageImport").getConstructor().newInstance());
   var type=loader.loadClass("coupons.receipts.LocalReceiptOcr");var reader=type.getConstructor().newInstance();
   var failure=assertThrows(InvocationTargetException.class,()->type.getMethod("read",Path.class).invoke(reader,Path.of("missing-test-photo.jpeg")));
   assertInstanceOf(java.io.IOException.class,failure.getCause());
   var pdf=assertThrows(InvocationTargetException.class,()->type.getMethod("read",Path.class).invoke(reader,Path.of("missing-test.pdf")));
   assertInstanceOf(java.io.IOException.class,pdf.getCause());
   assertFalse(pdf.getCause() instanceof LinkageError);
  }
 }
}
