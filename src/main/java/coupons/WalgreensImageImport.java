package coupons;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Conservative Walgreens parser. Writes a recoverable extraction, never a saved receipt. */
public final class WalgreensImageImport {
    private static final Pattern MONEY=Pattern.compile("(?<![\\d.])(-?\\d+[.,:]\\d{2})(?!\\d)");
    private static final Pattern SKU=Pattern.compile("(?<!\\d)(\\d{10,14})(?!\\d)");
    private static final Pattern DATE=Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)",Pattern.CASE_INSENSITIVE);
    private static final Pattern RFN=Pattern.compile("(?:RFN|REN|RFN#)\\W*([0-9 -]{24,32})",Pattern.CASE_INSENSITIVE);
    private final LocalReceiptOcr ocr=new LocalReceiptOcr();

    public Path prepare(List<Path> photos,Path root) throws IOException {
        if(photos.isEmpty())throw new IOException("Select at least one image.");
        var hashes=new ArrayList<String>();
        for(Path photo:photos) {
            if(Files.size(photo)>30_000_000L)throw new IOException("Choose photos smaller than 30 MB each.");
            if(!Files.isRegularFile(photo))throw new IOException("A selected image is unavailable.");
            try {hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(photo))));}
            catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        if(new HashSet<>(hashes).size()!=hashes.size())throw new IOException("The same photo was selected twice. Select each image once.");
        UUID id=UUID.nameUUIDFromBytes(String.join("|",hashes.stream().sorted().toList()).getBytes(StandardCharsets.UTF_8));
        // Reuse an already prepared extraction for these exact original images.
        if(Files.isDirectory(root))try(var existing=Files.walk(root,5)) {
            for(Path candidate:existing.filter(f->f.getFileName().toString().equals("receipt.properties")).sorted().toList()) {
                try {if(ReceiptImportReader.read(candidate).importId().equals(id))return candidate;}
                catch(IOException | IllegalArgumentException invalid){ /* Leave unreadable files untouched. */ }
            }
        }
        Path folder=root.resolve(id.toString());Files.createDirectories(folder);
        Path manifest=folder.resolve("receipt.properties");
        if(Files.exists(manifest))return manifest; // Reuse the extraction; no OCR or financial activity repeated.
        var pages=new ArrayList<String>();var originals=new ArrayList<Path>();
        for(int i=0;i<photos.size();i++) {
            String suffix=photos.get(i).getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_");
            Path original=folder.resolve(hashes.get(i)+"-"+suffix);
            if(!Files.exists(original))Files.copy(photos.get(i),original);
            Path text=folder.resolve(hashes.get(i)+".txt");
            if(!Files.exists(text)) {
                var recognized=ocr.readDetailed(original);
                atomicWrite(folder.resolve(hashes.get(i)+".tsv"),recognized.words());
                atomicWrite(text,recognized.text());
            }
            pages.add(Files.readString(text,StandardCharsets.UTF_8));originals.add(original.toAbsolutePath());
        }
        Properties fields;
        try {fields=parse(pages);}
        catch(IOException failure) {
            if(!failure.getMessage().startsWith("No product lines"))throw failure;
            // Earlier OCR may have cached a sideways image. Retry with current orientation handling.
            pages.clear();
            for(int i=0;i<originals.size();i++) {
                var recognized=ocr.readDetailed(originals.get(i));
                Path text=folder.resolve(hashes.get(i)+".txt"), backup=folder.resolve(hashes.get(i)+".previous.txt");
                if(Files.exists(text)&&!Files.exists(backup))Files.copy(text,backup);
                atomicWrite(text,recognized.text());atomicWrite(folder.resolve(hashes.get(i)+".tsv"),recognized.words());
                pages.add(recognized.text());
            }
            fields=parse(pages);
        }
        fields.setProperty("format","receipt-import-v1");fields.setProperty("receipt.id",id.toString());
        fields.setProperty("image.count",Integer.toString(originals.size()));
        for(int i=0;i<originals.size();i++)fields.setProperty("image."+(i+1)+".path",originals.get(i).toString());
        StringWriter encoded=new StringWriter();fields.store(encoded,"Local OCR extraction — unapproved receipt");
        atomicWrite(manifest,encoded.toString());
        return manifest;
    }
    private static void atomicWrite(Path target,String text) throws IOException {
        Path temp=Files.createTempFile(target.getParent(),"extraction-",".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    private static String money(String line) {
        Matcher m=MONEY.matcher(line);String result=null;
        while(m.find())result=m.group(1).replace(',','.').replace(':','.');
        return result;
    }
    private static String normalized(String line){return line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    private static void put(Properties p,String key,String value){if(value!=null)p.setProperty(key,value);}

    /** Package-visible for tests using OCR text without requiring a native executable. */
    static Properties parse(List<String> pages) throws IOException {
        Properties p=new Properties();
        String all=String.join("\n",pages).toUpperCase(Locale.ROOT);
        if(all.contains("WALGREENS") || all.contains("MYHALGREENS"))p.setProperty("receipt.retailer","Walgreens");
        var numbers=new LinkedHashSet<String>();var dates=new LinkedHashSet<String>();
        for(String page:pages) {
            Matcher r=RFN.matcher(page);while(r.find()) {
                String digits=r.group(1).replaceAll("\\D","");
                // Standard Walgreens RFN is 4-4-4-4-4 (20 digits).
                if(digits.length()==20)numbers.add(digits.substring(0,4)+"-"+digits.substring(4,8)+"-"+digits.substring(8,12)+"-"+digits.substring(12,16)+"-"+digits.substring(16));
            }
            Matcher d=DATE.matcher(page);while(d.find()) {
                try {dates.add(LocalDate.of(Integer.parseInt(d.group(3)),Integer.parseInt(d.group(1)),Integer.parseInt(d.group(2))).toString());put(p,"receipt.time",d.group(4));}
                catch(RuntimeException invalid){ /* Ask for unreadable dates in existing editor. */ }
            }
        }
        if(numbers.size()>1 || dates.size()>1)throw new IOException("Photos appear to contain different receipts. Import one receipt at a time.");
        numbers.stream().findFirst().ifPresent(n->p.setProperty("receipt.number",n));
        dates.stream().findFirst().ifPresent(d->p.setProperty("receipt.date",d));
        var products=new ArrayList<Map<String,String>>();var coupons=new ArrayList<Map<String,String>>();
        var issues=new ArrayList<String>();
        String priorDescription=null;Map<String,String> current=null;
        boolean stopped=false;var previousPageItems=new HashSet<String>();
        for(int pageIndex=0;pageIndex<pages.size();pageIndex++) {
            String[] lines=pages.get(pageIndex).split("\\R");
            boolean repeatedCheckout=pageIndex>0 && p.containsKey("seen.subtotal") && Arrays.stream(lines).anyMatch(l->normalized(l).startsWith("SUBTOTAL"));
            boolean skipBody=repeatedCheckout;
            for(int i=0;i<lines.length;i++) {
                String line=lines[i].trim().replaceFirst("(?i)^.{0,12}?(?=(?:SUBTOTAL|SALES TAX|TOTAL\\s+\\$?\\d|MFG (?:ECPN|CPN)|MYWALGREENS SAVINGS|MYHALGREENS SAVINGS|RETURN VALUE|REGULAR PRICE)\\b)",""), key=normalized(line), value=money(line);
                if(line.isEmpty())continue;
                if(key.startsWith("SUBTOTAL")){put(p,"seen.subtotal",value);stopped=true;current=null;skipBody=false;}
                if(key.startsWith("SALESTAX"))put(p,"receipt.tax",value);
                if(line.matches("(?i)^TOTAL\\s*:?[ \t]+\\$?-?\\d.*"))put(p,"receipt.total",value);
                if(key.startsWith("COINROUNDING"))put(p,"seen.rounding",value);
                if(key.startsWith("CASH") && !key.contains("REWARDS"))put(p,"seen.cash",value);
                if(key.startsWith("CHANGE"))put(p,"seen.change",value);
                if(key.startsWith("OPENINGBALANCE"))put(p,"footer.start",value);
                if(key.startsWith("EARNEDTHISVISIT"))put(p,"footer.earned",value);
                if(key.startsWith("CLOSINGBALANCE"))put(p,"footer.end",value);
                if(key.startsWith("TOTALSAVINGS"))put(p,"footer.totalSavings",value);
                if(key.startsWith("SAVINGSVALUE")) {
                    Matcher percent=Pattern.compile("(\\d{1,3})\\s*%").matcher(line);if(percent.find())put(p,"footer.percent",percent.group(1));
                }
                if(stopped && (key.startsWith("MY") && key.contains("SAVING")))put(p,"footer.myWalgreensSavings",value);
                if(stopped && (key.startsWith("MFGCOUPON")||key.startsWith("MFGCPNSAVINGS")))put(p,"footer.manufacturerSavings",value);
                if(skipBody)continue;
                if(!stopped && (key.startsWith("MFGECPN")||key.startsWith("MEGECPN")||key.startsWith("MFGCPN")||key.startsWith("MEGCPN"))) {
                    if(value!=null)coupons.add(new HashMap<>(Map.of("description",key.contains("ECPN")?"Manufacturer digital coupon":"Manufacturer coupon","kind","MANUFACTURER","amount",new BigDecimal(value).abs().toPlainString(),"lines","")));
                    current=null;continue;
                }
                if(stopped)continue;
                Matcher sku=SKU.matcher(line);
                if(sku.find()) {
                    // Only an anchored SKU with a preceding product description forms a product.
                    String charged=value;
                    Matcher qty=Pattern.compile("QTY\\s*[:=]?\\s*(\\d+)",Pattern.CASE_INSENSITIVE).matcher(line);
                    int quantity=1;
                    if(qty.find())try {quantity=Integer.parseInt(qty.group(1));}catch(NumberFormatException invalid){quantity=0;}
                    if(quantity<=0)throw new IOException("Product quantity could not be read. Review the original image.");
                    if(charged==null && priorDescription!=null)charged=money(priorDescription);
                    if(priorDescription!=null && charged!=null) {
                        String identity=sku.group(1)+"|"+charged;
                        if(pageIndex>0 && previousPageItems.contains(identity)) {issues.add("Overlapping product occurrence needs confirmation; retained instead of guessing whether it is a second purchase.");}
                        current=new HashMap<>();current.put("description",priorDescription.replaceFirst("\\s+\\$?\\d+[.,:]\\d{2}\\s*$",""));current.put("sku",sku.group(1));
                        try {current.put("enteredUnitPrice",new BigDecimal(charged).divide(BigDecimal.valueOf(quantity)).toPlainString());}
                        catch(ArithmeticException ambiguous){throw new IOException("An extended product price cannot be divided exactly. Use manual entry to verify it.");}
                        current.put("quantity",Integer.toString(quantity));products.add(current);previousPageItems.add(identity);
                    }
                    continue;
                }
                if(current!=null && key.contains("REGULARPRICE")) {putMap(current,"regularUnitPrice",value);continue;}
                if(current!=null && (key.startsWith("MY") && key.contains("SAVING"))) {putMap(current,"promotion",value);continue;}
                if(key.startsWith("RETURNVALUE"))continue;
                if(line.matches(".*[A-Za-z].*") && !key.contains("BUY1GET1") && !key.contains("REGULARPRICE") && !key.contains("SAVINGS") && !key.startsWith("MFG"))priorDescription=line;
            }
            // The next image may continue products if checkout has not yet appeared.
            priorDescription=null;current=null;
        }
        if(products.isEmpty())throw new IOException("No product lines could be read. Try a clearer Walgreens photo or use manual entry.");
        for(int i=0;i<products.size();i++) {
            var row=products.get(i);String regular=row.get("regularUnitPrice"),sale=row.get("enteredUnitPrice"),promotion=row.remove("promotion");
            if(regular!=null && promotion!=null && new BigDecimal(regular).multiply(new BigDecimal(row.get("quantity"))).subtract(new BigDecimal(promotion)).compareTo(new BigDecimal(sale).multiply(new BigDecimal(row.get("quantity"))))==0) {
                row.put("enteredUnitPrice",regular);
                coupons.add(new HashMap<>(Map.of("description","MyWalgreens savings — store promotion","kind","SALE_PROMOTION","amount",promotion,"lines",Integer.toString(i+1))));
            } else if(promotion!=null)issues.add("Check sale price and MyWalgreens savings for line "+(i+1)+"; they do not reconcile to the regular price.");
            final int index=i+1;row.forEach((k,v)->p.setProperty("item."+index+"."+k,v));
        }
        p.setProperty("item.count",Integer.toString(products.size()));p.setProperty("coupon.count",Integer.toString(coupons.size()));
        for(int i=0;i<coupons.size();i++){final int n=i+1;coupons.get(i).forEach((k,v)->p.setProperty("coupon."+n+"."+k,v));}
        String cash=p.getProperty("seen.cash"),change=p.getProperty("seen.change");
        if(cash!=null && change!=null) {
            BigDecimal actual=new BigDecimal(cash).subtract(new BigDecimal(change));
            if(actual.signum()>=0){p.setProperty("payment.count","1");p.setProperty("payment.1.type","CASH");p.setProperty("payment.1.amount",actual.toPlainString());p.setProperty("payment.1.notes","Cash "+cash+"; change "+change+"; rounding "+p.getProperty("seen.rounding","not read"));}
        }
        if(p.containsKey("footer.earned")) {
            p.setProperty("earning.count","1");p.setProperty("earning.1.kind","WALGREENS_CASH");p.setProperty("earning.1.provider","Walgreens");p.setProperty("earning.1.amount",p.getProperty("footer.earned"));p.setProperty("earning.1.status","CREDITED");p.setProperty("earning.1.scope","RECEIPT");
        }
        p.setProperty("receipt.footer",p.containsKey("footer.end")?"PRINTED":"UNKNOWN");
        p.stringPropertyNames().stream().filter(k->k.startsWith("seen.")).toList().forEach(p::remove);
        issues.add("Local OCR draft: verify product quantities, prices, tax, printed total, payment and coupon links before SAVE. Missing values are not zero.");
        p.setProperty("review.count",Integer.toString(issues.size()));
        p.setProperty("review."+issues.size()+".source",String.join("\n--- NEXT PHOTO ---\n",pages));
        for(int i=0;i<issues.size();i++){p.setProperty("review."+(i+1)+".field","Local image reading");p.setProperty("review."+(i+1)+".reason",issues.get(i));}
        return p;
    }
    private static void putMap(Map<String,String> map,String key,String value){if(value!=null)map.put(key,value);}
}
