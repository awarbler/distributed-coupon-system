package coupons.api;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class DealImageConversionTest {
    @Test void convertsPhotosTiffIntoReadableJpegLocally() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("mac"));
        var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,"tiff",bytes));
        var result=DealImageConversion.convert("image/tiff",Base64.getEncoder().encodeToString(bytes.toByteArray()));
        assertEquals("image/jpeg",result.get("mediaType"));var converted=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.get("contentBase64").toString())));assertNotNull(converted);assertEquals(8,converted.getWidth());assertEquals(8,converted.getHeight());
    }
}
