package coupons;

import java.io.IOException;
import java.nio.file.*;

/** Loaded only for PDF imports; photo OCR has no PDFBox dependency. */
public final class LocalPdfReceiptReader {
    public static LocalReceiptOcr.Recognition read(Path source) throws IOException {
        try(var document=org.apache.pdfbox.Loader.loadPDF(source.toFile())) {
            if(document.getNumberOfPages()==0 || document.getNumberOfPages()>30) throw new IOException("Choose a PDF containing 1 to 30 pages for one receipt.");
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(document);
            var text=new StringBuilder(); var words=new StringBuilder();
            for(int page=0;page<document.getNumberOfPages();page++) {
                var stripper=new org.apache.pdfbox.text.PDFTextStripper();stripper.setSortByPosition(true);stripper.setStartPage(page+1);stripper.setEndPage(page+1);
                String extracted=stripper.getText(document);
                if(extracted.isBlank()) {
                    var box=document.getPage(page).getCropBox();
                    double pixels=box.getWidth()*box.getHeight()*Math.pow(200.0/72,2);
                    if(!Double.isFinite(pixels)||pixels>40_000_000)throw new IOException("PDF page is too large. Export smaller receipt pages.");
                    Path image=Files.createTempFile("receipt-pdf-page-",".png").toRealPath();
                    try {
                        javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page,200,org.apache.pdfbox.rendering.ImageType.RGB),"png",image.toFile());
                        var result=new LocalReceiptOcr().readDetailed(image); extracted=result.text();words.append(result.words()).append("\n");
                    } finally {Files.deleteIfExists(image);}
                }
                text.append(extracted).append("\n");
            }
            return new LocalReceiptOcr.Recognition(text.toString(),words.toString());
        } catch(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException encrypted) {
            throw new IOException("This PDF is password protected. Import an unlocked copy.");
        }
    }
}
