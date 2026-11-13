package coupons.api;

import coupons.promotions.DealTerms;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static coupons.api.Json.*;

/** Advertisement interpretation only. Existing scenario parsing/calculation owns all checkout math. */
final class WeeklyAdvertisementImport {
    private WeeklyAdvertisementImport() {}
    private record Staged(String owner,java.time.Instant expires,coupons.promotions.SourceArtifact source,boolean saved) {}
    private static final java.util.Map<UUID,Staged> STAGED=new java.util.LinkedHashMap<>();
    static {
        var cleaner=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"advertisement-preview-expiry");thread.setDaemon(true);return thread;});
        cleaner.scheduleAtFixedRate(()->{synchronized(WeeklyAdvertisementImport.class){STAGED.values().removeIf(s->s.expires().isBefore(java.time.Instant.now()));}},1,1,java.util.concurrent.TimeUnit.MINUTES);
    }
    static synchronized coupons.promotions.SourceArtifact source(String token,String owner) {
        STAGED.values().removeIf(s->s.expires().isBefore(java.time.Instant.now()));
        var staged=STAGED.get(UUID.fromString(token));
        if(staged==null||!staged.owner().equals(owner))throw new IllegalArgumentException("Advertisement preview expired. Read the file again; saved deals are preserved.");
        return staged.source();
    }
    static synchronized boolean savedSource(String token,String owner) {source(token,owner);return STAGED.get(UUID.fromString(token)).saved();}
    static synchronized void markSavedSource(String token,String owner) {source(token,owner);UUID id=UUID.fromString(token);var old=STAGED.get(id);STAGED.put(id,new Staged(old.owner(),old.expires(),old.source(),true));}
    static Map<String,Object> upload(byte[] bytes,String type,String name,Map<String,Object> options,String owner) throws Exception {
        if(bytes.length==0||bytes.length>300*1024*1024)throw new IllegalArgumentException("Choose an advertisement up to 300 MiB.");
        var result=interpret(extract(bytes,type,WeeklyAdvertisementImport::advertisementOcr),options,name);
        UUID token=UUID.randomUUID();
        synchronized(WeeklyAdvertisementImport.class){
            STAGED.values().removeIf(s->s.expires().isBefore(java.time.Instant.now())||s.owner().equals(owner));
            if(STAGED.size()>=2)throw new IllegalArgumentException("Other advertisement imports are being reviewed. Finish them before uploading another file.");
            STAGED.put(token,new Staged(owner,java.time.Instant.now().plusSeconds(1800),new coupons.promotions.SourceArtifact(token,name,type,null,java.time.Instant.now(),bytes),false));
        }
        result.put("sourcePreviewToken",token);return result;
    }
    static Map<String,Object> uploadMany(Map<String,Object> input,String owner)throws Exception{
        known(input,"files","retailer","validFrom","validThrough");var pages=new ArrayList<Page>();var originals=new ArrayList<Object>();int total=0,index=0;
        for(var file:ReceiptInput.rows(input,"files",10)){known(file,"name","mediaType","contentBase64");var bytes=Base64.getDecoder().decode(required(file,"contentBase64"));total+=bytes.length;if(total>20*1024*1024)throw new IllegalArgumentException("Use up to 20 MiB of screenshots per review session.");if(!Set.of("image/png","image/jpeg").contains(required(file,"mediaType")))throw new IllegalArgumentException("Multiple-file sessions support PNG and JPEG screenshots; upload a PDF separately.");var extracted=extract(bytes,required(file,"mediaType"),WeeklyAdvertisementImport::advertisementOcr);for(var page:extracted)pages.add(new Page(++index,page.text()));originals.add(file);}
        if(originals.isEmpty())throw new IllegalArgumentException("Select at least one screenshot.");
        var result=interpret(pages,input,"Screenshot session ("+originals.size()+" files)");
        return stage(result,Json.encode(Map.of("files",originals)).getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/json","Original advertisement screenshots",owner);
    }
    private static Map<String,Object> stage(Map<String,Object> result,byte[] bytes,String type,String name,String owner){
        UUID token=UUID.randomUUID();synchronized(WeeklyAdvertisementImport.class){STAGED.values().removeIf(s->s.expires().isBefore(java.time.Instant.now())||s.owner().equals(owner));if(STAGED.size()>=2)throw new IllegalArgumentException("Finish other advertisement previews first.");STAGED.put(token,new Staged(owner,java.time.Instant.now().plusSeconds(1800),new coupons.promotions.SourceArtifact(token,name,type,null,java.time.Instant.now(),bytes),false));}result.put("sourcePreviewToken",token);return result;
    }
    private static String advertisementOcr(byte[] bytes)throws Exception {
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IllegalArgumentException("Use a standard PNG or JPEG screenshot.");var reader=readers.next();try{reader.setInput(stream);if((long)reader.getWidth(0)*reader.getHeight(0)>25_000_000)throw new IllegalArgumentException("Resize screenshots below 25 megapixels.");}finally{reader.dispose();}}
        String executable=java.nio.file.Files.isExecutable(java.nio.file.Path.of("/opt/homebrew/bin/tesseract"))?"/opt/homebrew/bin/tesseract":"tesseract";
        var process=new ProcessBuilder(executable,"stdin","stdout","-l","eng","--psm","3").redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var output=new java.util.concurrent.atomic.AtomicReference<byte[]>(new byte[0]);var read=Thread.startVirtualThread(()->{try{output.set(process.getInputStream().readNBytes(200000));}catch(java.io.IOException ignored){}});
        var write=Thread.startVirtualThread(()->{try(var stream=process.getOutputStream()){stream.write(bytes);}catch(java.io.IOException ignored){}});
        try{if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalArgumentException("Screenshot reading timed out. Crop to fewer offers.");read.join(1000);write.join(1000);if(process.exitValue()!=0)throw new IllegalArgumentException("Screenshot could not be read. Use a clearer PNG or JPEG.");return new String(output.get(),java.nio.charset.StandardCharsets.UTF_8);}finally{process.destroyForcibly();}
    }
    static List<Page> extract(byte[] bytes,String type,Scan scan)throws Exception {
        var pages=new ArrayList<Page>();
        if(!"application/pdf".equals(type)) {
            if(!Set.of("image/png","image/jpeg").contains(type))throw new IllegalArgumentException("Import PDF, PNG or JPEG.");
            pages.add(new Page(1,scan.read(bytes)));return pages;
        }
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
            if(pdf.isEncrypted()||pdf.getNumberOfPages()>60)throw new IllegalArgumentException("Use an unlocked advertisement of at most 60 pages.");
            var columns=List.of(new StringBuilder(),new StringBuilder(),new StringBuilder());
            var starts=new int[3];var linked=new boolean[3];boolean web=false;float[] webBounds=null;
            var deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            for(int page=1;page<=pdf.getNumberOfPages();page++) {
                if(System.nanoTime()>deadline)throw new IllegalArgumentException("Advertisement reading timed out. Import fewer scanned pages.");
                var glyphs=new ArrayList<WalgreensCouponImport.Glyph>();var linkStarts=new ArrayList<Float>();
                var stripper=new org.apache.pdfbox.text.PDFTextStripper(){@Override protected void writeString(String value,List<org.apache.pdfbox.text.TextPosition> positions){if(!positions.isEmpty()&&value.trim().matches("(?i)Shop (?:now|products?)"))linkStarts.add(positions.getFirst().getXDirAdj());for(var p:positions)glyphs.add(new WalgreensCouponImport.Glyph(p.getXDirAdj(),p.getYDirAdj(),p.getWidthDirAdj(),p.getFontSizeInPt(),false,p.getUnicode()));}};
                stripper.setSortByPosition(true);stripper.setStartPage(page);stripper.setEndPage(page);stripper.getText(pdf);
                var ordinary=new org.apache.pdfbox.text.PDFTextStripper();ordinary.setSortByPosition(true);ordinary.setStartPage(page);ordinary.setEndPage(page);String text=ordinary.getText(pdf);
                if(page==1)web=text.contains("Weekly Ad")&&text.contains("myWalgreens")&&text.contains("Shop products");
                if(web&&!glyphs.isEmpty()) {
                    var lines=WalgreensCouponImport.lines(new WalgreensCouponImport.PageText(page,glyphs,text,false));
                    if(page==1){String header=text.substring(0,Math.max(0,text.indexOf("Search the Weekly Ad")));pages.add(new Page(1,"HEADER\n"+header));}
                    float width=pdf.getPage(page-1).getCropBox().getWidth();var measured=columnBounds(linkStarts,width);if(measured!=null)webBounds=measured;
                    var bounds=webBounds==null?new float[]{0,width/3,2*width/3,width}:webBounds;
                    for(int col=0;col<3;col++)for(var line:lines){
                        float left=bounds[col],right=bounds[col+1];
                        String value=line.text(left,right).trim();if(value.isBlank())continue;
                        boolean large=line.glyphs().stream().anyMatch(g->g.x()>=left&&g.x()<right&&g.height()>=19);
                        boolean heading=large&&Pattern.compile("(?i)^(?:buy|\\$|[0-9]+\\s*/|extra|[0-9]+\\s*%).*").matcher(value).matches();
                        boolean productStart=linked[col]&&!large&&line.glyphs().stream().anyMatch(g->g.x()>=left&&g.x()<right&&g.height()>=15)
                                &&value.matches("[A-Z][A-Za-z’'&].*")&&!value.matches("(?i)^(?:Shop (?:now|products?)\\b|Earn\\b|Cash rewards|In-store|FREE|with|when|Up to|Clip|Our |Get |Start |Must |Limit |More|Save |Use |Valid |Customer|Shipping|Returns|Help|Contact|California|Program|Pay |View |Top |Download).*" )
                                &&!value.toLowerCase(Locale.ROOT).contains("coupon");
                        if(value.toLowerCase(Locale.ROOT).contains("romotions and selection"))break;
                        if((heading||productStart)&&linked[col]&&!columns.get(col).isEmpty()){pages.add(new Page(starts[col],"WEB_OFFER\n"+columns.get(col)));columns.get(col).setLength(0);linked[col]=false;}
                        if(linked[col]&&!heading&&!productStart&&!value.toLowerCase(Locale.ROOT).contains("coupon")&&!value.matches("[◊*†∞]+"))continue;
                        if(columns.get(col).isEmpty()){if(!heading&&!productStart)continue;starts[col]=page;}
                        columns.get(col).append(value).append('\n');
                        if(value.matches("(?i)^(?:Shop products?|Shop now|Start clipping|View products).*$"))linked[col]=true;
                    }
                } else {
                    if(text.isBlank()){
                        var box=pdf.getPage(page-1).getCropBox();if((double)box.getWidth()*box.getHeight()*6.25>25_000_000)throw new IllegalArgumentException("Resize oversized scanned pages.");
                        var image=new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(page-1,150);
                        try(var png=new java.io.ByteArrayOutputStream()){javax.imageio.ImageIO.write(image,"png",png);text=scan.read(png.toByteArray());}finally{image.flush();}
                    }pages.add(new Page(page,text));
                }
            }
            for(int col=0;col<3;col++)if(!columns.get(col).isEmpty())pages.add(new Page(starts[col],"WEB_OFFER\n"+columns.get(col)));
        }return pages;
    }
    // Printed website scale varies. Anchor gutters to the repeated, positioned offer links.
    static float[] columnBounds(List<Float> starts,float width){
        var anchors=new ArrayList<Float>();for(float x:starts.stream().sorted().toList())if(anchors.isEmpty()||x-anchors.getLast()>25)anchors.add(x);
        if(anchors.size()!=3)return null;
        float gap=Math.min(anchors.get(1)-anchors.get(0),anchors.get(2)-anchors.get(1));
        return new float[]{0,anchors.get(1)-gap*.14f,anchors.get(2)-gap*.14f,width};
    }
    record Page(int number,String text) {}
    @FunctionalInterface interface Scan { String read(byte[] bytes) throws Exception; }
    static Map<String,Object> preview(Map<String,Object> input) throws Exception {
        return preview(input, WeeklyAdvertisementImport::advertisementOcr);
    }
    static Map<String,Object> preview(Map<String,Object> input,Scan scan) throws Exception {
        known(input,"text","mediaType","contentBase64","name","retailer","validFrom","validThrough");
        String name=Objects.toString(input.get("name"),"Pasted advertisement"), type=optional(input,"mediaType");
        var pages=new ArrayList<Page>();
        if(optional(input,"contentBase64")!=null) {
            byte[] bytes=Base64.getDecoder().decode(required(input,"contentBase64"));
            if(bytes.length==0||bytes.length>5*1024*1024)throw new IllegalArgumentException("Use an advertisement up to 5 MiB.");
            pages.addAll(extract(bytes,type,scan));
        } else pages.add(new Page(1,required(input,"text")));
        if(pages.stream().mapToInt(p->p.text().length()).sum()>150000)throw new IllegalArgumentException("Split large advertisements into smaller files.");
        return interpret(pages,input,name);
    }
    static Map<String,Object> interpret(List<Page> pages,Map<String,Object> input,String name) {
        String retailer=optional(input,"retailer");
        String all=pages.stream().map(Page::text).reduce("",(a,b)->a+"\n"+b);
        if(retailer==null)retailer=match(all,"(?i)\\b(Walgreens|CVS|Walmart|Target|Costco)\\b");
        String from=optional(input,"validFrom"),through=optional(input,"validThrough");
        var range=Pattern.compile("(?i)(?:valid|week of|advertised|sale dates)\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2})\\s*(?:through|to|–|—| - )\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(all);
        if(range.find()){if(from==null)from=range.group(1);if(through==null)through=range.group(2);}
        var printed=printedDates(all);if(printed!=null){if(from==null)from=printed[0].toString();if(through==null)through=printed[1].toString();}
        var offers=new ArrayList<Object>();var ids=new HashSet<UUID>();
        for(var page:pages) {
            if(page.text().startsWith("HEADER\n"))continue;
            boolean webOffer=page.text().startsWith("WEB_OFFER\n");
            String pageText=webOffer?page.text().substring(10):page.text();
            // Preserve benefit continuation paragraphs in the same offer. Ambiguous layouts stay editable.
            var blocks=new ArrayList<String>();
            for(String raw:pageText.split("\\n\\s*\\n|(?m)^\\s*[-=]{3,}\\s*$")) {
                String block=raw.trim();if(block.isBlank())continue;
                if(!blocks.isEmpty()&&!block.matches("(?s)^[^\\n]+\\$[0-9]+[^\\n]*\\n.*\\$.*")&&block.matches("(?is)^(?:buy\\b|bogo\\b|spend\\b|earn\\b|get\\b|with\\b|save\\b|\\$|excludes\\b|limit\\b|must\\b|select\\b|digital coupon\\b).*"))blocks.set(blocks.size()-1,blocks.getLast()+"\n"+block);
                else blocks.add(block);
            }
            for(String block:blocks) {
                if(!Pattern.compile("(?i)\\$\\s*\\d|\\d\\s*%|\\bbogo\\b|buy.+get").matcher(block).find())continue;
                if(offers.size()>=500)throw new IllegalArgumentException("Import at most 500 offers at a time.");
                boolean structuredAd=webOffer||Pattern.compile("(?is)^(?:\\$[0-9]+(?:[.][0-9]+)?(?:\\s+or\\s+\\$[0-9]+(?:[.][0-9]+)?)?\\s+[A-Za-z]|buy\\s+[0-9]+\\s+get)").matcher(block).find();
                String title=structuredAd?webTitle(block):block.lines().filter(l->!l.isBlank()).findFirst().orElse("Advertisement offer").trim();
                if(title.length()>160)title=title.substring(0,160);
                String identity=(retailer==null?"":retailer.toLowerCase(Locale.ROOT))+"|"+from+"|"+through+"|"+block.replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
                UUID id=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));if(!ids.add(id))continue;
                String normalized=block.replaceAll("(?i)Walgreens Cash", "WC").replaceAll("(?i)Register Rewards?", "RR").replaceAll("(?i)BOGO\\s*50\\s*%(?:\\s*off)?", "Buy 1 get 1 50% off");
                normalized=normalized.replaceAll("(?m)^([\\p{L}][^\\n$]*?)\\s+\\$([0-9]+(?:[.][0-9]+)?)\\s*$", "$1 \\$$2 x1");
                normalized=normalized.replaceAll("(?im)^Use\\s+(?=\\$[0-9]+\\s+off)", "");
                var parsed=ShoppingNoteInput.parse(normalized,null);
                // A generic notes parser has legacy defaults. Advertisement review must retain unknown rules.
                @SuppressWarnings("unchecked") var draft=Json.parseObject(Json.encode(fields("items",parsed.get("items"),"coupons",parsed.get("coupons"),"promotions",parsed.get("promotions"))));
                if(structuredAd)draft=webDraft(block,title);
                for(var item:ReceiptInput.rows(draft,"items",100))for(String key:List.of("regular","sale"))if(item.get(key)!=null)item.put(key,item.get(key).toString());
                for(var coupon:ReceiptInput.rows(draft,"coupons",100)) {
                    coupon.put("amount",coupon.get("amount").toString());
                    coupon.put("kind","UNKNOWN");coupon.put("pendingVerification",true);
                }
                for(var reward:ReceiptInput.rows(draft,"promotions",100)) {
                    for(String key:List.of("rewardAmount","requiredSpend"))reward.put(key,reward.get(key).toString());
                    reward.put("paymentRulesReviewed",false);reward.put("allowsRegisterRewards",null);
                    reward.put("allowsWalgreensCash",false);
                    reward.put("spendBasis","");
                }
                var fields=new LinkedHashMap<String,String>();fields.put("sourceName",name);fields.put("page",Integer.toString(page.number()));fields.put("originalText",block);
                fields.put("advertisedDateText",lines(all,"(?i)November [0-9]+ through|January [0-9]+ through|February [0-9]+ through|March [0-9]+ through|April [0-9]+ through|May [0-9]+ through|June [0-9]+ through|July [0-9]+ through|August [0-9]+ through|September [0-9]+ through|October [0-9]+ through|December [0-9]+ through"));
                fields.put("brand",match(block,"(?im)^Brand:\\s*(.+)$"));
                fields.put("sizes",match(block,"(?i)\\b(\\d+(?:\\.\\d+)?\\s*-?\\s*(?:fl\\.?\\s*oz|oz|ml|liters?|loads?|rolls?|ct|count|pack))\\b"));
                fields.put("variants",lines(block,"(?i)\\b(?:select|varieties|variety|variants|assorted)\\b"));
                fields.put("qualifyingProducts",title);
                fields.put("loyalty",lines(block,"(?i)mywalgreens|with card|members? only|loyalty"));
                fields.put("exclusions",lines(block,"(?i)exclud|except|not valid"));
                fields.put("restrictions",lines(block,"(?i)limit|cannot combine|not combin|one per|restrictions|stack"));
                fields.put("priceText",lines(block,"(?i)^\\$|^or \\$|^[0-9]+/|regular|sale price"));
                fields.put("couponText",lines(block,"(?i)coupon|promo code"));
                fields.put("promotionText",lines(block,"(?i)bogo|buy.+get|spend|earn|%|for\\s*\\$"));
                fields.put("rewardText",lines(block,"(?i)^Earn|Cash rewards|walgreens cash|in-store|when you|register rewards?|\\bRR\\b|\\bWC\\b|extrabucks").replaceAll("\\s+"," ").trim());
                String flat=adClean(block).replaceAll("\\s+"," ");
                var quantity=Pattern.compile("(?i)buy\\s+([0-9]+)\\s+get\\s+([0-9]+)\\s+(free|50%\\s+off)").matcher(flat);
                if(quantity.find()){fields.put("buyQuantity",quantity.group(1));fields.put("freeQuantity",quantity.group(3).equalsIgnoreCase("free")?quantity.group(2):"0");fields.put("promotionText","Buy "+quantity.group(1)+" Get "+quantity.group(2)+" "+(quantity.group(3).equalsIgnoreCase("free")?"Free":"50% Off"));}
                fields.put("loyalty",flat.toLowerCase(Locale.ROOT).contains("mywalgreens")?"myWalgreens":fields.get("loyalty"));

                var warnings=new ArrayList<String>();
                warnings.add("Review offer boundaries and qualifying products against page "+page.number()+"; automatic layout interpretation may combine or split advertisements.");
                if(from==null||through==null)warnings.add("Advertisement dates are missing; enter the printed dates.");
                if(printed!=null&&(!printed[0].toString().equals(from)||!printed[1].toString().equals(through)))warnings.add("Entered dates differ from the printed "+printed[0]+" through "+printed[1]+" range. Review against the original advertisement.");
                if(ReceiptInput.rows(draft,"items",100).isEmpty()||ReceiptInput.rows(draft,"items",100).stream().anyMatch(item->item.get("regular")==null&&item.get("sale")==null))warnings.add("No usable product price was read. Enter a price before adding this deal to a trip.");
                if(fields.get("priceText").matches("(?is).*\\$[0-9]+(?:[.][0-9]+)?\\s+or\\s+\\$[0-9]+.*"))warnings.add("Advertised prices vary by qualifying product. Enter each product-specific price; no price was selected automatically.");
                if(block.toLowerCase(Locale.ROOT).contains("in-store"))warnings.add("In-store reward wording does not establish the reward type. Review the source before recording RR or Walgreens Cash.");
                if(!fields.get("rewardText").isBlank())warnings.add("Reward payment and spend-basis rules require review; no retailer policies were assumed.");
                warnings.addAll(((List<?>)parsed.get("unresolved")).stream().map(Object::toString).filter(w->!w.contains("uses after-coupon spending")).toList());
                offers.add(fields("dealId",id,"title",title,"interpretedTerms",block,"retailer",retailer,"validFrom",from,"validThrough",through,"fields",fields,"warnings",warnings,"draft",draft));
            }
        }
        if(offers.isEmpty())throw new IllegalArgumentException("No priced promotions were identified. Paste one offer per paragraph or use clearer advertisement pages.");
        return fields("offers",offers,"notice","Review each offer and select only the deals to save. No coupons, purchases or rewards have been recorded.");
    }
    static DealTerms.WeeklyAdvertisement read(Object raw) {
        if(!(raw instanceof Map<?,?>))throw new IllegalArgumentException("Weekly advertisement details are required.");
        @SuppressWarnings("unchecked") var a=(Map<String,Object>)raw;known(a,"draftJson","fields","warnings");
        var draft=Json.parseObject(required(a,"draftJson"));known(draft,"items","coupons","promotions");
        if(!(a.get("fields") instanceof Map<?,?> fields)||fields.size()>30)throw new IllegalArgumentException("Invalid advertisement fields.");
        var terms=new TreeMap<String,String>();for(var entry:fields.entrySet()){String key=asText(entry.getKey()),value=asText(entry.getValue());if(value.length()>150000)throw new IllegalArgumentException("Advertisement field too large.");terms.put(key,value);}
        var warnings=new ArrayList<String>();if(a.get("warnings") instanceof List<?> list)for(var warning:list){if(warnings.size()>200)throw new IllegalArgumentException("Too many warnings.");warnings.add(asText(warning));}
        for(String key:List.of("items","coupons","promotions"))ReceiptInput.rows(draft,key,100);
        return new DealTerms.WeeklyAdvertisement(Json.encode(draft),terms,warnings);
    }
    private static Map<String,Object> webDraft(String block,String title) {
        UUID product=UUID.randomUUID();String clean=adClean(block).replaceAll("(?<=\\d)[.]\\s+(?=\\d)",".");
        String price=match(clean,"(?m)^\\$([0-9]+(?:[.][0-9]+)?)\\s*$");
        if(price.isBlank())price=match(clean,"(?im)^\\$([0-9]+(?:[.][0-9]+)?)\\s+(?!(?:or\\s+\\$|off\\b|in-store\\b|register rewards?\\b|walgreens cash\\b))(?=[A-Za-z]).*");
        String regular=match(clean,"(?m)^or \\$([0-9]+(?:[.][0-9]+)?) each");
        var item=fields("id",product,"name",title,"sku",null,"regular",regular.isBlank()?null:regular,"sale",price.isBlank()?null:price,"quantity",1,"priceRule",null);
        String rule=clean.replaceAll("\\s+"," ").matches("(?is).*buy 2 get 1\\s+FREE.*")?"BUY_TWO_GET_ONE":clean.replaceAll("\\s+"," ").matches("(?is).*buy 1 get 1\\s+50% off.*")?"BOGO_HALF":clean.replaceAll("\\s+"," ").matches("(?is).*buy 1 get 1\\s+FREE.*")?"BOGO_FREE":clean.replaceAll("\\s+"," ").matches("(?is).*buy 1 get\\s+2 FREE.*")?"BUY_ONE_GET_TWO":null;
        if(rule!=null){item.put("priceRule",fields("type",rule,"bundlePrice",null));item.put("quantity",rule.equals("BUY_ONE_GET_TWO")||rule.equals("BUY_TWO_GET_ONE")?3:2);}
        var buyGet=Pattern.compile("(?i)buy\\s+([0-9]+)\\s+get\\s+([0-9]+)\\s+free").matcher(clean.replaceAll("\\s+"," "));
        if(rule==null&&buyGet.find()){int buy=Integer.parseInt(buyGet.group(1)),free=Integer.parseInt(buyGet.group(2));if(buy>0&&buy<=1000&&free>0&&free<=1000){item.put("priceRule",fields("type","BUY_GET","bundlePrice",null,"buyQuantity",buy,"freeQuantity",free));item.put("quantity",buy+free);}}
        var bundle=Pattern.compile("(?m)^([0-9]+)/\\s*\\$([0-9]+(?:[.][0-9]+)?)\\s*$").matcher(clean);
        if(bundle.find()&&bundle.group(1).equals("2")){item.put("priceRule",fields("type","TWO_FOR","bundlePrice",bundle.group(2)));item.put("quantity",2);
            if(item.get("sale")==null){if(!regular.isBlank())item.put("sale",regular);else try{item.put("sale",new java.math.BigDecimal(bundle.group(2)).divide(new java.math.BigDecimal("2")).toPlainString());}catch(ArithmeticException unknownUnit){/* Keep the missing price visible. */}}
        }
        var rewards=new ArrayList<Object>();
        var reward=Pattern.compile("(?is)Earn\\s+\\$([0-9]+(?:[.][0-9]+)?)\\s+(In-store\\s+rewards|Walgreens\\s+Cash(?:\\s+rewards)?|W\\s*Cash|Register\\s+Rewards?).*?when you (buy|spend)\\s*\\$?([0-9]+(?:[.][0-9]+)?)").matcher(clean);
        if(reward.find()) {
            if(reward.group(3).equalsIgnoreCase("buy"))try{item.put("quantity",Math.max(((Number)item.get("quantity")).intValue(),Integer.parseInt(reward.group(4))));}catch(NumberFormatException uncertain){/* Quantity remains for review. */}
            // In-store rewards is source wording, not proof of RR classification.
            String type=reward.group(2).matches("(?is).*cash.*")?"WALGREENS_CASH":reward.group(2).matches("(?i)Register.*")?"REGISTER_REWARD":"";
            rewards.add(fields("id",UUID.randomUUID(),"description",reward.group().replaceAll("\\s+"," "),"rewardType",type,"rewardAmount",reward.group(1),"requiredQuantity",reward.group(3).equalsIgnoreCase("buy")?reward.group(4):"0","requiredSpend",reward.group(3).equalsIgnoreCase("spend")?reward.group(4):"0","spendBasis","","allowsWalgreensCash",false,"paymentRulesReviewed",false,"lineIds",List.of(product)));
        }
        var coupons=new ArrayList<Object>();var coupon=Pattern.compile("(?m)^\\$([0-9]+(?:[.][0-9]+)?) off(?: ([0-9]+))? online coupon").matcher(clean);
        if(coupon.find())coupons.add(fields("id",UUID.randomUUID(),"description",title+" — "+coupon.group(),"amount",coupon.group(1),"requiredQuantity",coupon.group(2)==null?"1":coupon.group(2),"lineIds",List.of(product),"savedCouponId",null,"kind","UNKNOWN","pendingVerification",true));
        return fields("items",List.of(item),"coupons",coupons,"promotions",rewards);
    }
    static LocalDate[] printedDates(String text){
        var range=Pattern.compile("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})\\s+through\\s+(?:(January|February|March|April|May|June|July|August|September|October|November|December)\\s+)?(\\d{1,2})(?:,?\\s+(20\\d{2}))?").matcher(text);
        if(!range.find())return null;Integer year=range.group(5)==null?null:Integer.valueOf(range.group(5));LocalDate captured=null;
        var stamp=Pattern.compile("(?m)(\\d{1,2})/(\\d{1,2})/(\\d{2,4}),\\s*\\d{1,2}:").matcher(text);
        try{if(stamp.find()){int y=Integer.parseInt(stamp.group(3));captured=LocalDate.of(y<100?2000+y:y,Integer.parseInt(stamp.group(1)),Integer.parseInt(stamp.group(2)));if(year==null)year=captured.getYear();}if(year==null)return null;
            var format=java.time.format.DateTimeFormatter.ofPattern("MMMM d uuuu",Locale.US);var start=LocalDate.parse(range.group(1)+" "+range.group(2)+" "+year,format);var end=LocalDate.parse((range.group(3)==null?range.group(1):range.group(3))+" "+range.group(4)+" "+year,format);if(end.isBefore(start))return null;
            if(range.group(5)==null&&(captured==null||captured.isBefore(start)||captured.isAfter(end)))return null;return new LocalDate[]{start,end};
        }catch(java.time.DateTimeException invalid){return null;}
    }
    private static String adClean(String text){return text.replaceAll("[®™]","").replaceAll("[∞†◊*]"," ").replaceAll("[ \\t]+"," ").trim();}
    private static String webTitle(String text) {
        String value=adClean(text).replaceFirst("(?is)^\\$[0-9]+(?:[.][0-9]+)?(?:\\s+or\\s+\\$[0-9]+(?:[.][0-9]+)?)?\\s+(?=[A-Za-z])","");
        value=value.replaceFirst("(?is)^buy\\s+[0-9]+\\s+get\\s+[0-9]+\\s+(?:free|50%\\s+off)\\s+","");
        var out=new ArrayList<String>();
        for(String raw:value.lines().toList()) {
            String line=raw.replaceFirst("(?i)\\bwith\\s+myWalgreens\\b.*","").replaceFirst("(?i)\\b(?:earn\\s+\\$|shop\\s+(?:now|products?)\\b).*","").trim();
            if(line.isBlank()){if(!out.isEmpty())break;continue;}
            if(line.matches("(?i)^(?:[0-9]+ FREE|bogo\\b|buy\\b|\\$|[0-9]+\\s*/|or \\$|[0-9]+%|FREE\\b|50%|extra\\b|with code|when |rewards|up to).*"))continue;
            if(line.codePoints().anyMatch(Character::isLetter))out.add(line);
        }
        return out.isEmpty()?"Product name needs review":String.join(" ",out).replaceAll("\\s+"," ").trim();
    }
    private static Map<String,Object> fields(Object... pairs) {var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    private static String match(String text,String regex){var m=Pattern.compile(regex).matcher(text);return m.find()?m.group(1).trim():"";}
    private static String lines(String text,String regex){var pattern=Pattern.compile(regex);return text.lines().filter(l->pattern.matcher(l).find()).reduce((a,b)->a+"\n"+b).orElse("");}
}
