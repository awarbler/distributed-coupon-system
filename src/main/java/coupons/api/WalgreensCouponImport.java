package coupons.api;

import coupons.promotions.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.text.*;

/** Layout adapter into the existing coupon library. It never posts deductions or earned benefits. */
final class WalgreensCouponImport {
    private WalgreensCouponImport() {}
    record Glyph(float x,float y,float width,float height,boolean bold,String text) {}
    record PageText(int page,List<Glyph> glyphs,String text,boolean scanned) {}
    record Line(float y,List<Glyph> glyphs) {
        String text(float left,float right){return join(glyphs.stream().filter(g->g.x()>=left&&g.x()<right).toList());}
        String bold(float left,float right){return join(glyphs.stream().filter(g->g.x()>=left&&g.x()<right&&g.bold()).toList());}
    }
    @FunctionalInterface interface Scan {String read(byte[] image)throws Exception;}
    static boolean walgreens(ClippedCoupon c) {
        var t=c.coupon().getTerms();return t!=null&&t.source().name().startsWith("DIGITAL")&&("Walgreens".equalsIgnoreCase(t.storeRestriction())||"Walgreens".equalsIgnoreCase(c.provider()));
    }
    static String hash(String text) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static String join(List<Glyph> glyphs) {
        var out=new StringBuilder();float end=-1;
        for(var g:glyphs.stream().sorted(Comparator.comparing(Glyph::x)).toList()) {
            if(end>=0&&g.x()-end>Math.max(1.5,g.height()*.22)&&!out.toString().endsWith(" "))out.append(' ');
            out.append(g.text());end=g.x()+g.width();
        }
        return out.toString().replace('\u00a0',' ').strip();
    }
    static List<Line> lines(PageText page) {
        var result=new ArrayList<Line>();
        for(var g:page.glyphs().stream().sorted(Comparator.comparing(Glyph::y).thenComparing(Glyph::x)).toList()) {
            if(result.isEmpty()||Math.abs(result.getLast().y()-g.y())>2)result.add(new Line(g.y(),new ArrayList<>()));
            result.getLast().glyphs().add(g);
        }
        return result;
    }
    static List<PageText> extract(byte[] pdf,Scan scan)throws java.io.IOException {
        if(pdf.length>8*1024*1024)throw new IllegalArgumentException("Choose a PDF no larger than 8 MB.");
        var pages=new ArrayList<PageText>();
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf)) {
            if(document.getNumberOfPages()<1||document.getNumberOfPages()>60)throw new IllegalArgumentException("Choose a PDF with 1 to 60 pages.");
            for(int page=1;page<=document.getNumberOfPages();page++) {
                var glyphs=new ArrayList<Glyph>();
                var stripper=new PDFTextStripper(){@Override protected void writeString(String value,List<TextPosition> positions){for(var p:positions)glyphs.add(new Glyph(p.getXDirAdj(),p.getYDirAdj(),p.getWidthDirAdj(),p.getHeightDir(),p.getFont().getName().toLowerCase(Locale.ROOT).contains("bold"),p.getUnicode()));}};
                stripper.setSortByPosition(true);stripper.setStartPage(page);stripper.setEndPage(page);
                // writeString captures geometry; the ordinary stripper supplies the readable page text.
                stripper.getText(document);var ordinary=new PDFTextStripper();ordinary.setSortByPosition(true);ordinary.setStartPage(page);ordinary.setEndPage(page);String text=ordinary.getText(document);
                boolean unreadable=text.codePoints().filter(Character::isLetterOrDigit).count()<8||text.chars().filter(c->c=='\ufffd').count()>text.length()/10;
                if(unreadable) {
                    var box=document.getPage(page-1).getCropBox();if((double)box.getWidth()*box.getHeight()*6.25>25_000_000)throw new IllegalArgumentException("PDF page is too large to scan safely.");
                    var image=new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(page-1,150);var png=new java.io.ByteArrayOutputStream();
                    try{javax.imageio.ImageIO.write(image,"png",png);text=scan.read(png.toByteArray());}catch(Exception ex){throw new IllegalArgumentException("Page "+page+" requires local OCR, which could not read it. No coupons were saved. "+ex.getMessage());}finally{image.flush();}
                    glyphs.clear();
                }
                pages.add(new PageText(page,glyphs,text,unreadable));
            }
        }
        return pages;
    }
    static Map<String,Object> preview(byte[] pdf,String source,LocalDate day)throws java.io.IOException {
        return preview(pdf,source,day,ReceiptImport::ocr);
    }
    static Map<String,Object> preview(byte[] pdf,String source,LocalDate day,Scan scan)throws java.io.IOException {
        return preview(pdf,source,day,"AUTO",scan);
    }
    static Map<String,Object> preview(byte[] pdf,String source,LocalDate day,String requestedFormat,Scan scan)throws java.io.IOException {
        if(!Set.of("AUTO","AVAILABLE","CLIPPED","PRINTABLE","WEB_CARDS").contains(requestedFormat))throw new IllegalArgumentException("Unsupported PDF format. Only Walgreens clipped printouts and website exports are supported.");
        var pages=extract(pdf,scan);String text=pages.stream().map(PageText::text).reduce("",(a,b)->a+"\n"+b);
        boolean walgreens=Pattern.compile("(?i)walgreens").matcher(text).find();
        boolean clippedHeading=Pattern.compile("(?i)\\b\\d+\\s*clipped coupons").matcher(text).find();
        String layout=walgreens&&clippedHeading&&text.contains("These digital coupons are automatically applied")?"PRINTABLE":walgreens&&Pattern.compile("(?i)view\\s*details").matcher(text).find()?"WEB_CARDS":"UNSUPPORTED";
        if(layout.equals("UNSUPPORTED"))throw new IllegalArgumentException("Unsupported PDF layout. Choose a Walgreens clipped-coupon printout or Walgreens website offer export. Paper, CVS, Target and Costco PDFs are not supported.");
        if((requestedFormat.equals("PRINTABLE")&&!layout.equals("PRINTABLE"))||(requestedFormat.equals("WEB_CARDS")&&!layout.equals("WEB_CARDS")))throw new IllegalArgumentException("The selected PDF format does not match the document. Use automatic detection or choose the matching Walgreens format.");
        // A website's clipped balance/category filters do not declare the number of Available cards.
        Integer declared=null;if(layout.equals("PRINTABLE")){var count=Pattern.compile("(\\d+)\\s*clipped coupons",Pattern.CASE_INSENSITIVE).matcher(text);if(count.find())declared=Integer.valueOf(count.group(1));}
        var result=structured(pages,source,day,layout,declared);
        if(requestedFormat.equals("AVAILABLE")&&!"AVAILABLE".equals(result.get("documentKind"))||requestedFormat.equals("CLIPPED")&&!"CLIPPED".equals(result.get("documentKind")))throw new IllegalArgumentException("The selected Available/Clipped format does not match the document's verified controls.");
        String documentHash=HexFormat.of().formatHex(digest(pdf));result.put("documentHash",documentHash);
        @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)result.get("entries");for(var row:rows){@SuppressWarnings("unchecked")var details=(Map<String,String>)row.get("offerDetails");details.put("sourceDocumentHash",documentHash);var limit=Pattern.compile("(?i)limit of\\s+(\\d+)").matcher(text);if(limit.find())details.put("recordedClipLimit",limit.group(1));}return result;
    }
    private static byte[] digest(byte[] bytes){try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static Map<String,Object> structured(List<PageText> pages,String source,LocalDate day,String layout,Integer declared) {
        var entries=new ArrayList<Map<String,Object>>();var warnings=new ArrayList<String>();String category="";
        if(layout.equals("PRINTABLE")) {
            for(var page:pages) {
                if(page.scanned()){fallback(page,source,day,entries,warnings,layout);continue;}
                var lines=lines(page);var anchors=lines.stream().filter(l->l.text(430,550).equals("Expires")).toList();
                for(int i=0;i<anchors.size();i++) {
                    var anchor=anchors.get(i);float start=anchor.y()-3,end=i+1<anchors.size()?anchors.get(i+1).y()-3:780;
                    for(var l:lines)if(l.y()<anchor.y()&&categoryLine(l))category=l.text(60,550);
                    for(var l:lines)if(l.y()>anchor.y()&&l.y()<end&&categoryLine(l)){end=l.y()-3;break;}
                    String label=region(lines,start,end,120,232),body=region(lines,start,end,232,430),meta=region(lines,start,end,430,550);
                    String title=lines.stream().filter(l->l.y()>=start&&l.y()<anchor.y()+55).map(l->l.bold(232,430)).filter(s->!s.isBlank()).reduce((a,b)->a+" "+b).orElse(body.lines().findFirst().orElse("Unrecognized offer"));
                    String products=body.startsWith(title)?body.substring(title.length()).strip():body.lines().skip(title.split(" ").length>15?0:1).reduce("",(a,b)->a+(a.isBlank()?"":"\n")+b);
                    // Bold headings may wrap; removing their exact lines preserves every remaining product term.
                    var bodyLines=new ArrayList<String>();for(var l:lines)if(l.y()>=start&&l.y()<end&&!l.text(232,430).isBlank()&&l.bold(232,430).isBlank())bodyLines.add(l.text(232,430));products=String.join("\n",bodyLines);
                    var record=offer(label,title,products,meta,category,page.page(),source,day,"PRINTABLE",false);
                    @SuppressWarnings("unchecked")var details=(Map<String,String>)record.get("offerDetails");details.put("statusEvidence","CLIPPED_LIST_HEADING");entries.add(record);
                }
                for(var l:lines)if(categoryLine(l))category=l.text(60,550);
            }
        } else if(layout.equals("WEB_CARDS")) {
            // Three independent columns; cards can continue onto the next PDF page.
            for(int column=0;column<3;column++) {
                float left=column==0?20:column==1?185:350,right=column==0?185:column==1?350:520;
                var card=new ArrayList<String>();int startPage=0;boolean active=false;
                for(var page:pages) {
                    if(page.scanned()){if(column==0)fallback(page,source,day,entries,warnings,layout);continue;}
                    for(var line:lines(page)) {
                        String s=line.text(left,right);if(s.isBlank()||s.equals("Today")||s.equals("Coupons"))continue;
                        s=s.replaceFirst("^Today\\s+","");
                        if(s.contains("Expires "))s=s.substring(s.indexOf("Expires "));
                        boolean anchor=s.matches("(?i)Expires\\s+\\d{1,2}/\\d{1,2}/\\d{2,4}.*")||s.matches("(?i)Valid\\s+\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\s*[-–]\\s*\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?.*");
                        if(anchor) {
                            if(active&&card.size()>1)entries.add(webOffer(card,startPage,source,day));
                            card.clear();
                            var expiry=Pattern.compile("(?i)^Expires\\s+\\d{1,2}/\\d{1,2}/\\d{2,4}").matcher(s);
                            card.add(expiry.find()?expiry.group():s);startPage=page.page();active=true;
                        }else if(active) {
                            card.add(s);
                            if(s.matches("(?:Remove|Clip)(?:\\s+Shop)?")){entries.add(webOffer(card,startPage,source,day));card.clear();active=false;}
                        }
                    }
                }
                if(active){entries.add(webOffer(card,startPage,source,day));warnings.add("A website card has no recorded clip/remove control; its account state needs review.");}
            }
        } else for(var page:pages) {
            if(page.scanned())fallback(page,source,day,entries,warnings,layout);else entries.addAll(legacy(page.text(),source,day));
        }
        var seen=new HashMap<String,Integer>();
        for(var row:entries) {
            @SuppressWarnings("unchecked") var details=(Map<String,String>)row.get("offerDetails");
            if(details==null){details=new LinkedHashMap<>();row.put("offerDetails",details);}
            String key=details.getOrDefault("importKey",hash(String.valueOf(row.get("originalOfferText"))));int occurrence=seen.merge(key,1,Integer::sum);
            if(details.containsKey("previousImportKey"))details.put("previousImportKey",details.get("previousImportKey")+":"+occurrence);
            details.put("importKey",key+":"+occurrence);details.put("lastSeenInImport",day.toString());
            row.put("couponId",UUID.nameUUIDFromBytes(("walgreens-evidence:"+key+":"+occurrence).getBytes(StandardCharsets.UTF_8)));
            if(occurrence>1){for(var prior:entries){@SuppressWarnings("unchecked")var pd=(Map<String,String>)prior.get("offerDetails");if(pd!=null&&pd.getOrDefault("importKey","").startsWith(key+":"))pd.put("reviewStatus","NEEDS_REVIEW");}warnings.add("Repeated identical offer evidence requires review; separate entries were retained.");}
        }
        if(entries.isEmpty())throw new IllegalArgumentException("No readable coupon records found. No coupons were saved.");
        if(entries.size()>300)throw new IllegalArgumentException("Import at most 300 offer records at a time.");
        if(declared!=null&&declared!=entries.size())warnings.add("Document reports "+declared+" offers; extracted "+entries.size()+" records. Review the count mismatch before saving.");
        var types=new TreeMap<String,Long>();long missingQuantity=0,missingExpiration=0,review=0;
        for(var row:entries){types.merge(String.valueOf(row.get("discount")),1L,Long::sum);if(row.get("requiredQuantity")==null||row.get("requiredQuantity").toString().equals("0"))missingQuantity++;if(row.get("expirationDate")==null)missingExpiration++;@SuppressWarnings("unchecked")var details=(Map<String,String>)row.get("offerDetails");if(!"PARSED".equals(details.get("reviewStatus")))review++;}
        var out=new LinkedHashMap<String,Object>();out.put("entries",entries);out.put("source",source);out.put("layout",layout);
        boolean available=entries.stream().anyMatch(r->"AVAILABLE".equals(r.get("status"))),clipped=entries.stream().anyMatch(r->"CLIPPED".equals(r.get("status")));
        out.put("documentKind",available&&!clipped?"AVAILABLE":clipped&&!available?"CLIPPED":"MIXED_OR_UNKNOWN");out.put("importedOn",day);out.put("declaredCount",declared);out.put("extractedCount",entries.size());out.put("offerTypes",types);out.put("missingQuantity",missingQuantity);out.put("missingExpiration",missingExpiration);out.put("needsReview",review);out.put("warnings",warnings);out.put("ocrPages",pages.stream().filter(PageText::scanned).count());
        // Account membership IDs, personal navigation and addresses are never returned as offer text.
        out.put("originalText",entries.stream().map(r->String.valueOf(r.get("originalOfferText"))).reduce("",(a,b)->a+"\n\n"+b));return out;
    }
    private static boolean categoryLine(Line l){return Set.of("Beauty","Diet & Fitness","Grocery","Household","Medicines & Treatments","Other","Personal Care","Sexual Wellness","Vitamins & Supplements").contains(l.text(60,550))&&l.glyphs().stream().anyMatch(g->g.x()<120);}
    private static String region(List<Line> lines,float top,float bottom,float left,float right){return lines.stream().filter(l->l.y()>=top&&l.y()<bottom).map(l->l.text(left,right)).filter(s->!s.isBlank()).reduce((a,b)->a+"\n"+b).orElse("");}
    private static String normalized(String s){return s.replaceAll("\\s+"," ").strip();}
    private static LocalDate date(String s){try{return LocalDate.parse(s,DateTimeFormatter.ofPattern(s.substring(s.lastIndexOf('/')+1).length()==2?"M/d/uu":"M/d/uuuu").withResolverStyle(ResolverStyle.STRICT));}catch(DateTimeException e){return null;}}
    private static Map<String,Object> offer(String label,String title,String products,String meta,String category,int page,String source,LocalDate day,String layout,boolean truncated) {
        var row=new LinkedHashMap<String,Object>();var details=new LinkedHashMap<String,String>();String flat=normalized(label),all=flat+" "+normalized(products),lower=all.toLowerCase(Locale.ROOT);
        String mechanism=flat.toLowerCase(Locale.ROOT).startsWith("earn")?"REWARD":lower.contains("via rebate")?"REBATE":flat.toLowerCase(Locale.ROOT).contains("buy one")?"BOGO":flat.matches("\\$[0-9.]+ off \\d+")?"DOLLARS":"UNKNOWN";
        var fixed=Pattern.compile("\\$(\\d+(?:\\.\\d+)?) off (\\d+)").matcher(flat);String amount=null;Integer quantity=null;if(fixed.find()){amount=fixed.group(1);quantity=Integer.valueOf(fixed.group(2));}
        if(mechanism.equals("BOGO")){quantity=1;details.put("freeQuantity","1");var cap=Pattern.compile("(?i)(?:up to|max(?:imum)? savings(?: value)?(?: up to)?)\\s*\\$(\\d+(?:\\.\\d+)?)").matcher(normalized(products));if(cap.find())details.put("maximumSavings",cap.group(1));}
        if(mechanism.equals("REBATE")&&amount!=null)details.put("rebateAmount",amount);
        if(mechanism.equals("REWARD")) {
            var reward=Pattern.compile("(?i)Earn \\$(\\d+(?:\\.\\d+)?)").matcher(flat);if(reward.find())details.put("rewardAmount",reward.group(1));
            var qty=Pattern.compile("(?i)(?:buy|when you buy) (\\d+)\\+").matcher(flat);if(qty.find())quantity=Integer.valueOf(qty.group(1));
            var spend=Pattern.compile("\\$(\\d+(?:\\.\\d+)?)\\+ (?:spent|on)|spend \\$(\\d+(?:\\.\\d+)?)\\+").matcher(flat);if(spend.find())row.put("minimumPurchase",spend.group(1)!=null?spend.group(1):spend.group(2));
        }
        String issuer=meta.contains("Manufacturer")?"MANUFACTURER":Pattern.compile("(?im)^Store coupon$").matcher(meta).find()?"STORE":meta.contains("Monthly Savings")?"MONTHLY_SAVINGS_BOOK":meta.contains("Suggested For You")?"SUGGESTED_FOR_YOU":"UNKNOWN";
        String classification=issuer.equals("MANUFACTURER")?"MANUFACTURER":(issuer.equals("MONTHLY_SAVINGS_BOOK")||issuer.equals("STORE"))?"STORE":"OTHER";
        var expires=Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/\\d{2,4})\\b").matcher(meta);LocalDate expiration=expires.find()?date(expires.group(1)):null;
        String raw=label+"\n"+title+"\n"+products+"\n"+meta;
        details.put("title",title);details.put("offerLabel",flat);details.put("category",category);details.put("offerType",mechanism);details.put("qualifyingProducts",products);details.put("issuerType",issuer);details.put("sourcePage",String.valueOf(page));details.put("sourceLayout",layout);details.put("sourceDocument",source);
        var exclusions=Pattern.compile("(?is)\\(?\\bexcl(?:udes|uding|usions)?\\.?\\s+.*").matcher(products);if(exclusions.find())details.put("exclusions",exclusions.group());
        if(lower.contains("online only"))details.put("channelRestriction","ONLINE_ONLY");
        if(lower.contains("multi use")||lower.contains("each qualifying transaction"))details.put("usageRestriction",products);
        if(truncated)details.put("truncated","true");
        boolean incomplete=truncated||mechanism.equals("UNKNOWN")||issuer.equals("UNKNOWN")||expiration==null||quantity==null&&!mechanism.equals("REWARD");
        details.put("reviewStatus",incomplete?"NEEDS_REVIEW":"PARSED");
        // This is a local full-evidence fingerprint, never an invented Walgreens offer ID.
        details.put("importKey",hash(normalized(raw)+"|"+mechanism));
        row.put("provider","Walgreens");row.put("source","DIGITAL_"+classification);row.put("couponType",classification);row.put("discount",mechanism);row.put("copies",1);row.put("usageLimit",1);row.put("requiredQuantity",quantity);row.put("amount",mechanism.equals("DOLLARS")?amount:null);row.put("expirationDate",expiration);row.put("status","CLIPPED");row.put("clippedOn",day);row.put("storeRestriction","Walgreens");row.put("qualifyingSkus",List.of());row.put("originalOfferText",raw);row.put("importedOn",day);row.put("importSource",source);row.put("description",title+" — "+flat);row.put("productOrBrand",title);row.put("conditions",products);row.put("offerDetails",details);
        // Unknown quantities are representable without inventing a one-item minimum.
        if(mechanism.equals("DOLLARS")&&quantity==null){row.put("discount","UNKNOWN");row.put("amount",null);}
        return row;
    }
    private static Map<String,Object> webOffer(List<String> card,int page,String source,LocalDate day) {
        String meta=card.getFirst();var rest=new ArrayList<>(card.subList(1,card.size()));var label=new StringBuilder();
        while(!rest.isEmpty()&&(label.isEmpty()||rest.getFirst().matches("(?i)^(Rebate|One|rewards.*|yo\\.\\.\\.|Cash.*)$")))label.append(label.isEmpty()?"":" ").append(rest.removeFirst());
        String title=rest.isEmpty()?"Unrecognized website card":rest.removeFirst();
        var body=new ArrayList<String>();var originalBody=new ArrayList<String>();
        for(String s:rest){
            if(!s.contains("View details")&&!s.contains("Remove")&&!s.equals("Shop"))originalBody.add(s);
            if(!Pattern.compile("(?i)view\\s*details").matcher(s).find()&&!s.matches("(?:Remove|Clip)(?:\\s+Shop)?")&&!s.equals("Shop"))body.add(s);
        }
        String rawLabel=label.toString();var row=offer(rawLabel,title,String.join("\n",body),meta,"",page,source,day,"WEB_CARDS",true);
        String original=rawLabel+"\n"+title+"\n"+String.join("\n",originalBody)+"\n"+meta;
        row.put("originalOfferText",original);
        boolean remove=card.stream().anyMatch(s->s.matches("Remove(?:\\s+Shop)?")),clip=card.stream().anyMatch(s->s.matches("Clip(?:\\s+Shop)?"));
        @SuppressWarnings("unchecked")var details=(Map<String,String>)row.get("offerDetails");
        String previous=hash(normalized(original)+"|"+row.get("discount"));if(!previous.equals(details.get("importKey")))details.put("previousImportKey",previous);
        if(remove&&!clip)details.put("statusEvidence","WEBSITE_REMOVE_CONTROL");
        else if(clip&&!remove){row.put("status","AVAILABLE");row.put("clippedOn",null);details.put("statusEvidence","WEBSITE_CLIP_CONTROL");}
        else{row.put("status",null);row.put("clippedOn",null);details.put("statusEvidence","UNVERIFIED");details.put("reviewStatus","NEEDS_REVIEW");}if(meta.startsWith("Valid")){
            details.put("validityText",meta);row.put("expirationDate",null);
            var range=Pattern.compile("(?i)^Valid\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*[-–]\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})$").matcher(meta);
            if(range.find()){row.put("startsOn",date(range.group(1)));row.put("expirationDate",date(range.group(2)));}
            else {
                var partial=Pattern.compile("(?i)^Valid\\s+(\\d{1,2})/(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})/(\\d{1,2})$").matcher(meta);
                if(partial.find())try {
                    var start=LocalDate.of(day.getYear(),Integer.parseInt(partial.group(1)),Integer.parseInt(partial.group(2)));
                    var end=LocalDate.of(day.getYear(),Integer.parseInt(partial.group(3)),Integer.parseInt(partial.group(4)));
                    row.put("startsOn",start);row.put("expirationDate",end);
                    details.put("datePrecision","CURRENT_YEAR_RANGE");details.put("dateYearBasis","CURRENT_IMPORT_YEAR");details.put("dateNote","Printed month/day range uses the current import year: "+day.getYear()+".");
                    details.put("previousImportKey",details.get("importKey"));details.put("importKey",hash(details.get("importKey")+"|year="+day.getYear()));
                }catch(DateTimeException ignored){/* Invalid printed dates still require correction. */}
            }
        }
        details.put("qualificationStatus","NEEDS_REVIEW");details.put("unknownRestrictions","Website descriptions may be truncated; complete eligibility terms and issuer classification are not verified.");
        // Catalog completeness is distinct from checkout eligibility. Missing optional terms never invent qualifications.
        if(Set.of("AVAILABLE","CLIPPED").contains(String.valueOf(row.get("status")))&&catalogComplete(row))details.put("reviewStatus","PARSED");
        return row;
    }
    static boolean matchingImportYear(ClippedCoupon existing,ClippedCoupon incoming) {
        if(!"CURRENT_IMPORT_YEAR".equals(incoming.coupon().getTerms().offerDetails().get("dateYearBasis")))return true;
        var prior=existing.coupon().getExpirationDate();
        if(prior==null&&existing.coupon().getTerms()!=null)prior=existing.coupon().getTerms().importedOn();
        return prior!=null&&incoming.coupon().getExpirationDate()!=null&&prior.getYear()==incoming.coupon().getExpirationDate().getYear();
    }
    private static boolean catalogComplete(Map<String,Object> r) {
        String type=String.valueOf(r.get("discount"));
        @SuppressWarnings("unchecked")var d=(Map<String,String>)r.get("offerDetails");
        boolean qty=r.get("requiredQuantity") instanceof Number n&&n.intValue()>0;
        if(r.get("expirationDate")==null&&!"MONTH_DAY_RANGE".equals(d.get("datePrecision"))||String.valueOf(r.get("description")).isBlank()||"Unrecognized website card".equals(r.get("productOrBrand")))return false;
        if(r.get("startsOn") instanceof LocalDate start&&start.isAfter((LocalDate)r.get("expirationDate")))return false;
        if(type.equals("DOLLARS"))return qty&&r.get("amount")!=null;
        if(type.equals("REBATE"))return qty&&d.get("rebateAmount")!=null;
        if(type.equals("BOGO"))return qty&&d.get("freeQuantity")!=null;
        if(type.equals("REWARD"))return d.get("rewardAmount")!=null&&(qty||r.get("minimumPurchase")!=null);
        return false;
    }
    private static void fallback(PageText page,String source,LocalDate day,List<Map<String,Object>> entries,List<String>warnings,String layout) {
        var extracted=legacy(page.text(),source,day);for(var row:extracted){@SuppressWarnings("unchecked")var d=(Map<String,String>)row.get("offerDetails");d.put("reviewStatus","NEEDS_REVIEW");d.put("ocr","true");d.put("sourcePage",String.valueOf(page.page()));if(layout.equals("PRINTABLE"))d.put("statusEvidence","CLIPPED_LIST_HEADING");else{row.put("status",null);row.put("clippedOn",null);d.put("statusEvidence","UNVERIFIED");}}entries.addAll(extracted);warnings.add("Page "+page.page()+" used OCR. Its grouping and terms require review.");
    }
    static Map<String,Object> parse(String text,String source,LocalDate day){return structured(List.of(new PageText(1,List.of(),text,false)),source,day,"TEXT",null);}
    private static List<Map<String,Object>> legacy(String text,String source,LocalDate day) {
        if(text.length()>500_000)throw new IllegalArgumentException("PDF text is too large to review safely.");var entries=new ArrayList<Map<String,Object>>();
        for(String block:text.replace("\r","").split("\\n\\s*\\n")) {
            String raw=block.strip();if(raw.isBlank())continue;
            var simple=Pattern.compile("(?im)^([A-Za-z][A-Za-z &’'./-]*?)\\s+(\\$\\d+(?:\\.\\d{1,2})?\\s+off\\s+\\d+)\\s*$").matcher(raw);
            String title=raw.lines().findFirst().orElse("Unrecognized offer"),label="";if(simple.find()){title=simple.group(1);label=simple.group(2);}
            var row=offer(label,title,raw,raw,"",1,source,day,"TEXT",false);row.put("originalOfferText",raw);
            var id=Pattern.compile("(?im)^(?:Coupon|Offer)\\s+ID\\s*:\\s*([A-Za-z0-9_-]+)\\s*$").matcher(raw);if(id.find())row.put("externalId",id.group(1));
            entries.add(row);
        }
        return entries;
    }
}
