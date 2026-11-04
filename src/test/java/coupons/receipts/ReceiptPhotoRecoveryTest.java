package coupons.receipts;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptPhotoRecoveryTest {
    @Test void detectsAndEnlargesPaperWithoutHardcodedPhotoCoordinates() {
        var photo=new BufferedImage(300,500,BufferedImage.TYPE_INT_RGB);
        var g=photo.createGraphics();
        g.setColor(new Color(95,65,40));g.fillRect(0,0,300,500);
        g.setColor(Color.WHITE);g.fillPolygon(new int[]{110,200,215,95},new int[]{40,40,499,499},4);
        g.setColor(Color.BLACK);g.fillRect(130,100,40,4);g.dispose();
        var prepared=ReceiptPhotoRecovery.prepare(photo);
        assertTrue(prepared.getHeight()>photo.getHeight());
        assertTrue((double)prepared.getWidth()/prepared.getHeight()<.4,"Crop should remove the desk");
        assertTrue((prepared.getRGB(prepared.getWidth()/2,prepared.getHeight()/2)&255)>240);
    }

    @Test void cropsLargePhotosWithoutReducingNativeReceiptDetail() {
        var photo=new BufferedImage(2400,3000,BufferedImage.TYPE_INT_RGB);
        var g=photo.createGraphics();g.setColor(new Color(70,45,30));g.fillRect(0,0,2400,3000);
        g.setColor(Color.WHITE);g.fillRect(700,100,1000,2800);g.setColor(Color.BLACK);g.fillRect(1000,1200,10,100);g.dispose();
        var prepared=ReceiptPhotoRecovery.prepare(photo);
        assertTrue(prepared.getWidth()<1500,"Desk should be cropped even above four megapixels");
        assertTrue(prepared.getWidth()>=1000,"Native receipt width must be preserved");
        assertTrue(prepared.getHeight()>=2800,"Native receipt height must be preserved");
    }

    @Test void handlesPhotosWithoutAnIdentifiablePaperRegion() {
        var photo=new BufferedImage(100,200,BufferedImage.TYPE_INT_RGB);
        var prepared=ReceiptPhotoRecovery.prepare(photo);
        assertEquals(400,prepared.getWidth());assertEquals(800,prepared.getHeight());
        assertEquals(0,prepared.getRGB(200,400)&0xffffff);
    }

    @Test void reconstructsRowsAndKeepsPricesBesideTheirLabels() {
        var recognized=ReceiptPhotoRecovery.fromObservations(List.of(
                "0.8\t0.103\t0.1\t0.02\t19.99",
                "0.1\t0.1\t0.4\t0.02\tSUBTOTAL",
                "0.8\t0.153\t0.1\t0.02\t0.99",
                "0.1\t0.15\t0.4\t0.02\tSALES TAX"));
        assertEquals("SUBTOTAL 19.99\nSALES TAX 0.99\n",recognized.text());
        assertTrue(recognized.words().contains("\t19.99\n"));
    }

    @Test void selectsReceiptEvidenceOverGarbledText() {
        assertTrue(ReceiptPhotoRecovery.score("Walgreens\nSUBTOTAL 19.99\nTOTAL 20.98")>
                ReceiptPhotoRecovery.score("sion aa\nen \"GD x"));
    }
}
