package coupons.api;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class DealImageConversion {
    static Map<String,Object> convert(String media,String encoded) throws Exception {
        if(!Set.of("image/heic","image/heif","image/tiff").contains(media))throw new IllegalArgumentException("Choose a HEIC, HEIF or TIFF photo.");
        var bytes=Base64.getDecoder().decode(encoded);if(bytes.length>5*1024*1024)throw new IllegalArgumentException("Photo must be at most 5 MiB.");
        var dir=Files.createTempDirectory("deal-photo-");
        try {
            var input=dir.resolve(media.equals("image/tiff")?"photo.tiff":"photo.heic");var output=dir.resolve("photo.jpg");Files.write(input,bytes);
            var process=new ProcessBuilder("sips","-s","format","jpeg",input.toString(),"--out",output.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {if(!process.waitFor(30,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException("Photo conversion failed. Export JPEG from Photos and retry.");}finally{if(process.isAlive())process.destroyForcibly();}
            var converted=Files.readAllBytes(output);if(converted.length>5*1024*1024)throw new IllegalArgumentException("Converted photo exceeds 5 MiB. Export a smaller JPEG.");
            return Map.of("mediaType","image/jpeg","contentBase64",Base64.getEncoder().encodeToString(converted));
        }finally{try(var files=Files.list(dir)){for(var file:files.toList())Files.deleteIfExists(file);}Files.deleteIfExists(dir);}
    }
}
