package coupons.cli;

import coupons.receipts.LocalReceiptOcr;
import coupons.receipts.WalgreensImageImport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class PdfReceiptImportTest {
 @Test void readsPagesInOrderAndPreservesOriginalOnImport(@TempDir Path dir)throws Exception {
  Path pdf=dir.resolve("receipt.pdf");
  try(var doc=new PDDocument()) {
   for(String line:new String[]{"Walgreens\n10/08/2026 9:34 AM\nSOAP\n04740069058 A 12.34","SALES TAX 0.00\nTOTAL 12.34"}) {
    var page=new PDPage();doc.addPage(page);
    try(var stream=new PDPageContentStream(doc,page)) {stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);for(String part:line.split("\n")){stream.showText(part);stream.newLineAtOffset(0,-18);}stream.endText();}
   }
   doc.save(pdf.toFile());
  }
  try(var loader=new java.net.URLClassLoader(new java.net.URL[]{Path.of("target/classes").toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
   var type=loader.loadClass("coupons.receipts.LocalReceiptOcr");
   String isolated=(String)type.getMethod("read",Path.class).invoke(type.getConstructor().newInstance(),pdf);
   assertTrue(isolated.contains("TOTAL 12.34"));
  }
  var before=Files.readAllBytes(pdf);
  String text=new LocalReceiptOcr().read(pdf);
  assertTrue(text.indexOf("Walgreens")<text.indexOf("TOTAL 12.34"));
  var importer=new WalgreensImageImport();var manifest=importer.prepare(java.util.List.of(pdf),dir.resolve("imports"));
  var output=new java.io.ByteArrayOutputStream();
  var repository=new coupons.repository.InMemoryTransactionRepository();
  new ReceiptEntryApp(new java.util.Scanner("9\n"+manifest.toAbsolutePath()+"\n"),new java.io.PrintStream(output),repository).run();
  assertTrue(output.toString().contains("REVIEW — all changes are still unsaved"),output.toString());
  assertFalse(output.toString().contains("Operation not saved"),output.toString());
  assertEquals(0,repository.creationHistory().size());
  assertEquals(manifest,importer.prepare(java.util.List.of(pdf),dir.resolve("imports")));
  assertArrayEquals(before,Files.readAllBytes(pdf));
  try(var files=Files.walk(manifest.getParent())) {assertTrue(files.anyMatch(p->p.getFileName().toString().endsWith(".pdf")));}
 }
 @Test void rejectsEmptyPdf(@TempDir Path dir)throws Exception {
  var pdf=dir.resolve("empty.pdf");try(var doc=new PDDocument()){doc.save(pdf.toFile());}
  assertThrows(java.io.IOException.class,()->new LocalReceiptOcr().read(pdf));
 }
}
