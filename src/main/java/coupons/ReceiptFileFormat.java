package coupons;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Detect HEIF containers even when a photo has been renamed .jpeg. */
final class ReceiptFileFormat {
    private ReceiptFileFormat() {}
    static boolean isHeif(Path file) throws IOException {
        try(var in=Files.newInputStream(file)){return isHeif(in.readNBytes(64));}
    }
    static boolean isHeif(byte[] header) {
        if(header.length<12 || !new String(header,4,4,StandardCharsets.US_ASCII).equals("ftyp"))return false;
        var brands=Set.of("heic","heix","hevc","hevx","mif1","msf1");
        for(int i=8;i+4<=Math.min(header.length,64);i+=4) {
            if(i==12)continue; // minor version, not a brand
            if(brands.contains(new String(header,i,4,StandardCharsets.US_ASCII)))return true;
        }
        return false;
    }
}
