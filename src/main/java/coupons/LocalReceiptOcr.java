package coupons;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Local subprocess only. Receipt bytes are never sent to a network service. */
public final class LocalReceiptOcr {
    public record Recognition(String text,String words) {}
    public String read(Path image) throws IOException { return readDetailed(image).text(); }
    public Recognition readDetailed(Path image) throws IOException {
        if(image.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) return readPdf(image);
        Path work=Files.createTempDirectory("receipt-ocr-").toRealPath();
        try {
            Path readable=image.toRealPath();
            String name=image.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if(ReceiptFileFormat.isHeif(image) || name.endsWith(".heic") || name.endsWith(".heif")) {
                readable=work.resolve("converted.png");
                run(new ProcessBuilder("sips","-s","format","png",image.toRealPath().toString(),"--out",readable.toString()),work.resolve("conversion.log"));
            }
            var best=recognize(readable,work,"recognition");
            if(orientationScore(best.text())==0 && System.getProperty("os.name","").toLowerCase().contains("mac")) {
                for(int degrees:new int[]{90,180,270}) {
                    Path rotated=work.resolve("rotated-"+degrees+".png");
                    run(new ProcessBuilder("sips","-r",Integer.toString(degrees),readable.toString(),"--out",rotated.toString()),work.resolve("rotate.log"));
                    var candidate=recognize(rotated,work,"recognition-"+degrees);
                    if(orientationScore(candidate.text())>orientationScore(best.text()))best=candidate;
                    if(orientationScore(best.text())>=2)break;
                }
            }
            return best;
        } finally {
            try(var files=Files.list(work)) { for(Path file:files.toList())Files.deleteIfExists(file); }
            Files.deleteIfExists(work);
        }
    }
    private Recognition recognize(Path image,Path work,String name) throws IOException {
        Path base=work.resolve(name);
        run(new ProcessBuilder("tesseract",image.toString(),base.toString(),"-l","eng","--psm","6","txt","tsv"),work.resolve("process.log"));
        return new Recognition(Files.readString(work.resolve(name+".txt"),StandardCharsets.UTF_8),Files.readString(work.resolve(name+".tsv"),StandardCharsets.UTF_8));
    }
    static int orientationScore(String text) {
        return (int)java.util.regex.Pattern.compile("(?<!\\d)\\d{10,14}(?!\\d)").matcher(text).results().count();
    }
    private Recognition readPdf(Path source) throws IOException {
        try {
            return LocalPdfReceiptReader.read(source);
        } catch(LinkageError missing) {
            return readPdfWithBundledLibraries(source);

        }
    }
    /** Keep the familiar classes-only CLI command working with optional local PDF libraries. */
    private Recognition readPdfWithBundledLibraries(Path source) throws IOException {
        try {
            var classes=Path.of(LocalReceiptOcr.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var libraries=classes.resolveSibling("dependency");
            var urls=new java.util.ArrayList<java.net.URL>();urls.add(classes.toUri().toURL());
            if(Files.isDirectory(libraries))try(var jars=Files.list(libraries)) {
                for(var jar:jars.filter(p->p.getFileName().toString().endsWith(".jar")).sorted().toList())urls.add(jar.toUri().toURL());
            }
            try(var loader=new java.net.URLClassLoader(urls.toArray(java.net.URL[]::new),LocalReceiptOcr.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                    if(!name.equals("coupons.LocalPdfReceiptReader"))return super.loadClass(name,resolve);
                    synchronized(getClassLoadingLock(name)) {
                        var type=findLoadedClass(name);if(type==null)type=findClass(name);
                        if(resolve)resolveClass(type);return type;
                    }
                }
            }) {
                return (Recognition)loader.loadClass("coupons.LocalPdfReceiptReader").getMethod("read",Path.class).invoke(null,source);
            }
        } catch(java.lang.reflect.InvocationTargetException failure) {
            if(failure.getCause() instanceof IOException io)throw io;
            throw new IOException("Could not read the PDF locally. Verify the PDF and rebuild with mvn package.",failure.getCause());
        } catch(ReflectiveOperationException | java.net.URISyntaxException | LinkageError failure) {
            throw new IOException("PDF libraries are unavailable. Run mvn package, then restart the CLI.",failure);
        }
    }
    private void run(ProcessBuilder builder,Path output) throws IOException {
        Process process;
        try {process=builder.redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();}
        catch(IOException failure){throw new IOException("Local image reader unavailable. Install Tesseract; HEIC also needs macOS image conversion.");}
        try {
            if(!process.waitFor(60,TimeUnit.SECONDS))throw new IOException("Image reading timed out. Try a clearer photo.");
            if(process.exitValue()!=0)throw new IOException("Could not read the image locally. Try JPEG or PNG.");
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Image reading interrupted.");}
        finally {if(process.isAlive())process.destroyForcibly();}
    }
}
