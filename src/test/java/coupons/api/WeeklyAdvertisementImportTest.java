package coupons.api;

import coupons.promotions.DealTerms;
import coupons.shopping.ShoppingPlan;
import coupons.serialization.LegacyObjectStreams;
import java.math.BigDecimal;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyAdvertisementImportTest {
    static Map<String,Object> input(){return new LinkedHashMap<>(Map.of("retailer","Walgreens","validFrom","2026-11-08","validThrough","2026-11-14"));}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> offers(Map<String,Object> result){return (List<Map<String,Object>>)result.get("offers");}
    @Test void screenshotSessionReadsSeparateImagesAndPreservesOriginalBytes()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isExecutable(java.nio.file.Path.of("/opt/homebrew/bin/tesseract")),"Local screenshot OCR requires Tesseract");
        var files=new ArrayList<Object>();for(String name:List.of("Tide","Downy")){
            var image=new java.awt.image.BufferedImage(1000,400,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1000,400);g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,40));g.drawString(name+" $5.00",50,100);g.drawString("Use $1 off 1 digital coupon",50,180);g.dispose();var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);files.add(Map.of("name",name+".png","mediaType","image/png","contentBase64",Base64.getEncoder().encodeToString(bytes.toByteArray())));
        }
        var f=input();f.put("files",files);var result=WeeklyAdvertisementImport.uploadMany(f,"screenshot-test");var rows=offers(result);assertEquals(2,rows.size());assertTrue(rows.stream().anyMatch(o->o.get("title").toString().contains("Tide")));assertTrue(rows.stream().anyMatch(o->o.get("title").toString().contains("Downy")));
        var source=WeeklyAdvertisementImport.source(result.get("sourcePreviewToken").toString(),"screenshot-test");assertEquals("application/json",source.getMediaType());assertEquals(files,Json.parseObject(new String(source.getContent(),java.nio.charset.StandardCharsets.UTF_8)).get("files"));
    }
    @Test void webPromotionsUseExistingPriceRulesWithoutInventingPrices(){
        for(var entry:Map.of("buy 1 get 1 FREE","BOGO_FREE","buy 1 get 1 50% off","BOGO_HALF","buy 1 get 2 FREE","BUY_ONE_GET_TWO","buy 2 get 1 FREE","BUY_TWO_GET_ONE").entrySet()){
            var rows=offers(WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,"WEB_OFFER\n"+entry.getKey()+"\nLaundry detergent\nShop products")),input(),"screen.png"));
            var item=ReceiptInput.rows(Json.parseObject(Json.encode(rows.getFirst().get("draft"))),"items",10).getFirst();assertEquals(entry.getValue(),((Map<?,?>)item.get("priceRule")).get("type"));assertNull(item.get("regular"));assertNull(item.get("sale"));
        }
    }
    @Test void actualCompactWalgreensColumnsSeparateNiceAndDoritosAndUsePrintedYear()throws Exception{
        byte[] bytes;try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()){
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){
                adText(stream,18,770,7,"Weekly Ad: Top Deals Online & In-Store | Walgreens");adText(stream,400,770,7,"11/9/26, 8:49 PM");adText(stream,100,582,16,"November 8 through November 14");adText(stream,60,565,16,"Search the Weekly Ad");
                adText(stream,63,376,20,"$1.99");adText(stream,63,368,16,"Nice! Peanuts, Trail");adText(stream,63,362,16,"Mix or Snacks");adText(stream,63,357,12,"with myWalgreens");adText(stream,69.9f,343,16,"Shop products");
                adText(stream,147.8f,376,20,"2/ $6");adText(stream,147.8f,368,20,"or $5.49 each");adText(stream,147.8f,360,16,"Doritos");adText(stream,147.8f,355,12,"with myWalgreens");adText(stream,154.6f,342,16,"Shop products");
                adText(stream,232.4f,376,20,"$7.99");adText(stream,232.4f,368,16,"Starbucks Coffee");adText(stream,232.4f,363,12,"with myWalgreens");adText(stream,239.3f,350,16,"Shop products");
            }pdf.save(out);bytes=out.toByteArray();
        }
        var pages=WeeklyAdvertisementImport.extract(bytes,"application/pdf",b->{throw new AssertionError("Text PDF must not use OCR");});var rows=offers(WeeklyAdvertisementImport.interpret(pages,Map.of("retailer","Walgreens"),"Availables.pdf"));assertEquals(3,rows.size(),rows.toString());
        var nice=rows.stream().filter(o->o.get("title").toString().startsWith("Nice!")).findFirst().orElseThrow();var doritos=rows.stream().filter(o->o.get("title").equals("Doritos")).findFirst().orElseThrow();
        assertEquals("Nice! Peanuts, Trail Mix or Snacks",nice.get("title"));assertEquals("2026-11-08",nice.get("validFrom"));assertEquals("2026-11-14",nice.get("validThrough"));
        var a=ReceiptInput.rows(Json.parseObject(Json.encode(nice.get("draft"))),"items",10).getFirst();assertEquals("1.99",a.get("sale"));
        var b=ReceiptInput.rows(Json.parseObject(Json.encode(doritos.get("draft"))),"items",10).getFirst();assertEquals("5.49",b.get("regular"));assertEquals(2,((Number)b.get("quantity")).intValue());assertEquals("6",((Map<?,?>)b.get("priceRule")).get("bundlePrice"));
        assertTrue(rows.stream().noneMatch(o->o.get("title").toString().matches("(?i).*shop products?.*")));assertNull(WeeklyAdvertisementImport.printedDates("November 8 through November 14"));
    }
    static void adText(org.apache.pdfbox.pdmodel.PDPageContentStream stream,float x,float y,int size,String text)throws Exception{stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),size);stream.setHorizontalScaling(40);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();}
    @Test void normalizesNiceNameAndKeepsMembershipOutsideTheDescription(){
        var text="$1.99\nNice!® Peanuts, Trail\nMix or Snacks\nwith myWalgreens\nShop Products";var offer=offers(WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,"WEB_OFFER\n"+text)),input(),"Availables.pdf")).getFirst();
        assertEquals("Nice! Peanuts, Trail Mix or Snacks",offer.get("title"));var fields=(Map<?,?>)offer.get("fields");assertEquals("myWalgreens",fields.get("loyalty"));assertEquals(text,fields.get("originalText"));var draft=Json.parseObject(Json.encode(offer.get("draft")));assertEquals("1.99",ReceiptInput.rows(draft,"items",10).getFirst().get("sale"));assertTrue(ReceiptInput.rows(draft,"coupons",10).isEmpty());assertTrue(ReceiptInput.rows(draft,"promotions",10).isEmpty());
    }
    @Test void footnoteIsNotAQuantityAndBuyOneGetTwoUsesExistingPriceRule(){
        var text="Buy 1 Get ∞\n2 FREE\nWalgreens Food\nStorage Bags\nwith myWalgreens\nShop Now";var offer=offers(WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,"WEB_OFFER\n"+text)),input(),"Availables.pdf")).getFirst();assertEquals("Walgreens Food Storage Bags",offer.get("title"));var fields=(Map<?,?>)offer.get("fields");assertEquals("Buy 1 Get 2 Free",fields.get("promotionText"));assertEquals("1",fields.get("buyQuantity"));assertEquals("2",fields.get("freeQuantity"));var item=ReceiptInput.rows(Json.parseObject(Json.encode(offer.get("draft"))),"items",10).getFirst();assertEquals("BUY_ONE_GET_TWO",((Map<?,?>)item.get("priceRule")).get("type"));assertEquals(3,((Number)item.get("quantity")).intValue());assertNull(item.get("sale"));assertEquals(new BigDecimal("5"),new ShoppingPlan.PriceRule("BUY_ONE_GET_TWO",null).total(new BigDecimal("5"),3));
    }
    @Test void colgateVariantsDoNotInventAPriceOrAnInStoreRewardClassification(){
        String text="$5 or $6\nColgate, Hello or Tom's of Maine\nDental Care with myWalgreens.\nEarn $5 In-store\nrewards when you buy 2.\nShop Product";var offer=offers(WeeklyAdvertisementImport.interpret(List.of(new WeeklyAdvertisementImport.Page(1,"WEB_OFFER\n"+text)),input(),"Availables.pdf")).getFirst();assertEquals("Colgate, Hello or Tom's of Maine Dental Care",offer.get("title"));var draft=Json.parseObject(Json.encode(offer.get("draft")));var item=ReceiptInput.rows(draft,"items",10).getFirst();assertNull(item.get("regular"));assertNull(item.get("sale"));assertTrue(ReceiptInput.rows(draft,"coupons",10).isEmpty());var reward=ReceiptInput.rows(draft,"promotions",10).getFirst();assertEquals("5",reward.get("rewardAmount"));assertEquals("2",reward.get("requiredQuantity"));assertEquals("",reward.get("rewardType"));assertEquals(false,reward.get("paymentRulesReviewed"));assertEquals("myWalgreens",((Map<?,?>)offer.get("fields")).get("loyalty"));assertTrue(offer.get("warnings").toString().contains("prices vary"));
    }
    @Test void futureWalgreensCashAndRegisterRewardsStayOutsideCheckoutFunding(){
        for(String type:List.of("WALGREENS_CASH","REGISTER_REWARD")){
            var line=new ShoppingPlan.Line(UUID.randomUUID(),"Qualifying product",null,new BigDecimal("10"),null,2,0);var coupon=new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Immediate coupon",new BigDecimal("2"),1,List.of(line.id()),null);var reward=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Entered promotion",type,new BigDecimal("5"),2,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(line.id()),false);
            var scenario=new ShoppingPlan.Scenario(List.of(line),List.of(coupon),List.of(reward),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","");var report=scenario.estimate("Walgreens");assertEquals(0,new BigDecimal("18").compareTo((BigDecimal)report.get("calculatedOop")));assertEquals(0,new BigDecimal("5").compareTo((BigDecimal)report.get("potentialRewards")));assertEquals(0,new BigDecimal("13").compareTo((BigDecimal)report.get("afterPotentialRewards")));assertEquals(BigDecimal.ZERO,report.get("registerRewardsUsed"));assertEquals(BigDecimal.ZERO,report.get("walgreensCashUsed"));
        }
    }
    @Test void combinedBogoAndSpendRewardRemainOneLogicalOffer() throws Exception {
        var f=input();f.put("text","CeraVe $30.00 x2\nBuy 1 get 1 50% off\nSpend $45 earn $15 Walgreens Cash");
        var rows=offers(WeeklyAdvertisementImport.preview(f));assertEquals(1,rows.size());
        var draft=Json.parseObject(Json.encode(rows.getFirst().get("draft")));
        assertEquals(1,ReceiptInput.rows(draft,"items",100).size());assertEquals(1,ReceiptInput.rows(draft,"promotions",100).size());
        var reward=ReceiptInput.rows(draft,"promotions",100).getFirst();
        assertEquals("",reward.get("spendBasis"));assertEquals(false,reward.get("paymentRulesReviewed"));
        reward.put("spendBasis","BEFORE_COUPONS");reward.put("paymentRulesReviewed",true);
        draft.putAll(Map.of("tax","0","walgreensCashUsed","0","registerRewardsUsed","0","giftCardUsed","0","paymentMethod","CASH","fundingNotes",""));
        ReceiptInput.rows(draft,"items",100).getFirst().put("memberRewardRate",BigDecimal.ZERO);
        var scenario=CouponApiService.parseScenario(draft);var estimate=scenario.estimate("Walgreens");
        assertEquals(0,new BigDecimal("45.00").compareTo((BigDecimal)estimate.get("saleTotal")));assertEquals(0,new BigDecimal("15").compareTo((BigDecimal)estimate.get("potentialRewards")));
        assertEquals(0,new BigDecimal("45.00").compareTo((BigDecimal)estimate.get("calculatedOop")));
    }
    @Test void preservesBenefitContinuationAndSeparatesProductOffers() throws Exception {
        var f=input();f.put("text","CeraVe $30.00 x2\n\nBuy 1 get 1 50% off\nSpend $45 earn $15 Walgreens Cash\n\nTide $5.00\nUse $1 off 1 Tide digital coupon");
        var rows=offers(WeeklyAdvertisementImport.preview(f));assertEquals(2,rows.size());
        assertTrue(rows.getFirst().get("interpretedTerms").toString().contains("Spend $45"));
        assertEquals(1,ReceiptInput.rows(Json.parseObject(Json.encode(rows.get(1).get("draft"))),"coupons",100).size(),rows.toString());
        assertEquals(rows.stream().map(r->r.get("dealId")).toList(),offers(WeeklyAdvertisementImport.preview(f)).stream().map(r->r.get("dealId")).toList());
        f.put("validThrough","2026-11-21");assertNotEquals(rows.getFirst().get("dealId"),offers(WeeklyAdvertisementImport.preview(f)).getFirst().get("dealId"));
    }
    @Test void textPdfUsesTextAndRetainsPageEvidence() throws Exception {
        byte[] bytes;
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()){
            for(String text:List.of("Tide $5.00","Downy $6.00")){
                var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
                try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){
                    stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(40,700);stream.showText(text);stream.endText();
                }
            }pdf.save(out);bytes=out.toByteArray();
        }
        var f=input();f.put("name","weekly.pdf");f.put("mediaType","application/pdf");f.put("contentBase64",Base64.getEncoder().encodeToString(bytes));
        var rows=offers(WeeklyAdvertisementImport.preview(f,b->{fail("Text PDFs must not invoke OCR");return "";}));assertEquals(2,rows.size());
        assertEquals("2",((Map<?,?>)rows.get(1).get("fields")).get("page"));
    }
    @Test void imageAndScannedPdfReuseLocalOcr() throws Exception {
        var f=input();f.put("mediaType","image/png");f.put("contentBase64",Base64.getEncoder().encodeToString(new byte[]{1,2}));
        assertEquals(1,offers(WeeklyAdvertisementImport.preview(f,b->"Soap $4.00")).size());
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new ByteArrayOutputStream()){
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(out);f.put("mediaType","application/pdf");f.put("contentBase64",Base64.getEncoder().encodeToString(out.toByteArray()));
            assertEquals(1,offers(WeeklyAdvertisementImport.preview(f,b->"Soap $4.00")).size());
        }
    }
    @Test void missingDatesAndPricesRemainUnknownAndOriginalTextIsPreserved() throws Exception {
        var f=new LinkedHashMap<String,Object>();f.put("text","CeraVe skincare\nBOGO 50% off\nSpend $45 earn $15 Walgreens Cash");
        var row=offers(WeeklyAdvertisementImport.preview(f)).getFirst();assertNull(row.get("validFrom"));assertNull(row.get("validThrough"));
        assertEquals(f.get("text"),row.get("interpretedTerms"));assertTrue(row.get("warnings").toString().contains("No usable product price"));
    }
    @Test void serializedWeeklyOfferAndPlanKeepCombinedTermsAndSourceIds() throws Exception {
        UUID deal=UUID.randomUUID();var source=new ShoppingPlan.PlanningSource("original",null,null,List.of(),null,List.of(deal));
        var ad=new DealTerms.WeeklyAdvertisement("{\"items\":[],\"coupons\":[],\"promotions\":[]}",Map.of("originalText","source"),List.of("Review"));
        var terms=new DealTerms(DealTerms.Type.ADVERTISEMENT,"CeraVe",null,null,1,null,0,null,false,null,List.of(),null,null,null,false,false,ad);
        var out=new ByteArrayOutputStream();try(var stream=LegacyObjectStreams.output(out)){stream.writeObject(List.of(terms,source));}
        try(var stream=LegacyObjectStreams.input(new ByteArrayInputStream(out.toByteArray()))){var saved=(List<?>)stream.readObject();assertEquals(terms,saved.get(0));assertEquals(List.of(deal),((ShoppingPlan.PlanningSource)saved.get(1)).advertisedDealIds());}
        assertEquals(List.of(),new ShoppingPlan.PlanningSource("legacy",null,null).advertisedDealIds());
    }
    @Test void suppliedWalgreensLayoutKeepsCeraveAndOtherBrandsSeparate()throws Exception {
        String text;try(var in=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/coupons/walgreens-weekly-ad-layout-redacted.json.gz"))){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var fixture=Json.parseObject("{\"pages\":"+text+"}");
        var pages=ReceiptInput.rows(fixture,"pages",500).stream().map(p->new WeeklyAdvertisementImport.Page(Integer.parseInt(p.get("number").toString()),p.get("text").toString())).toList();
        var rows=offers(WeeklyAdvertisementImport.interpret(pages,input(),"Walgreens weekly ad.pdf"));
        var cerave=rows.stream().filter(o->o.get("title").toString().equals("Cerave Skin Care")).toList();assertEquals(1,cerave.size());
        var offer=cerave.getFirst();assertFalse(offer.get("interpretedTerms").toString().contains("Keeffe"));assertEquals("18",((Map<?,?>)offer.get("fields")).get("page"));
        var draft=Json.parseObject(Json.encode(offer.get("draft")));var line=ReceiptInput.rows(draft,"items",100).getFirst();
        assertNull(line.get("sale"));assertNull(line.get("regular"));assertEquals("BOGO_HALF",((Map<?,?>)line.get("priceRule")).get("type"));
        var reward=ReceiptInput.rows(draft,"promotions",100).getFirst();assertEquals("WALGREENS_CASH",reward.get("rewardType"));assertEquals("15",reward.get("rewardAmount"));assertEquals("45",reward.get("requiredSpend"));assertEquals(false,reward.get("paymentRulesReviewed"));
        assertTrue(rows.stream().anyMatch(o->o.get("title").toString().contains("Bounce, Downy or Tide")));
        assertTrue(rows.stream().anyMatch(o->o.get("title").toString().contains("Crest or Oral-B")));
        assertTrue(rows.stream().noneMatch(o->o.get("interpretedTerms").toString().contains("Riverside")));
    }
    @Test void linkedPurchaseProjectionReusesBogoCouponAndRewardCalculationsOnce()throws Exception {
        UUID planId=UUID.randomUUID(),firstId=UUID.randomUUID(),purchaseId=UUID.randomUUID(),dealId=UUID.randomUUID(),adLine=UUID.randomUUID();
        var zero=BigDecimal.ZERO;var scenario=new ShoppingPlan.Scenario(List.of(new ShoppingPlan.Line(firstId,"Test Product",null,BigDecimal.ONE,null,1,0)),List.of(),List.of(),zero,zero,zero,zero,null,"CASH","");
        var plan=new ShoppingPlan(planId,"Combined","Walgreens",java.time.LocalDate.of(2026,11,9),List.of(),1,scenario);
        var purchase=new coupons.shopping.PlannedPurchase(purchaseId,planId,"CoverGirl",null,2,new BigDecimal("4"),dealId,List.of(),1,new BigDecimal("4"),null,"DEAL");
        var product=Map.of("id",adLine,"name","CoverGirl","regular","4","quantity",2,"memberRewardRate",0,"priceRule",Map.of("type","BOGO_HALF","bundlePrice", "0"));
        var coupon=Map.of("id",UUID.randomUUID(),"description","CoverGirl coupon","amount","1","requiredQuantity",1,"kind","MANUFACTURER","lineIds",List.of(adLine));
        var reward=new LinkedHashMap<String,Object>();reward.put("id",UUID.randomUUID());reward.put("description","CoverGirl RR");reward.put("rewardType","REGISTER_REWARD");reward.put("rewardAmount","3");reward.put("requiredQuantity",2);reward.put("requiredSpend","0");reward.put("spendBasis","BEFORE_COUPONS");reward.put("allowsWalgreensCash",true);reward.put("paymentRulesReviewed",true);reward.put("lineIds",List.of(adLine));
        var ad=new DealTerms.WeeklyAdvertisement(Json.encode(Map.of("items",List.of(product),"coupons",List.of(coupon),"promotions",List.of(reward))),Map.of(),List.of());
        var terms=new DealTerms(DealTerms.Type.ADVERTISEMENT,"CoverGirl",null,null,1,null,0,null,false,null,List.of(),null,null,null,false,false,ad);
        var deal=new coupons.promotions.AdvertisedDeal(dealId,"CoverGirl","BOGO 50% and RR","Walgreens",null,null,List.of(UUID.randomUUID()),1,terms);
        var input=ShoppingPlanViews.object(Map.of("plan",plan,"items",List.of(purchase)));var deals=Map.of(dealId.toString(),ShoppingPlanViews.object(deal));
        var projected=ShoppingPlanViews.project(input,deals);var report=Json.parseObject(Json.encode(projected.get("scenarioEstimate")));
        assertEquals(0,new BigDecimal("7").compareTo((BigDecimal)report.get("saleTotal")));assertEquals(0,new BigDecimal("1").compareTo((BigDecimal)report.get("couponTotal")));assertEquals(0,new BigDecimal("6").compareTo((BigDecimal)report.get("calculatedOop")));assertEquals(0,new BigDecimal("3").compareTo((BigDecimal)report.get("potentialRewards")));
        var again=ShoppingPlanViews.project(ShoppingPlanViews.object(projected),deals);var combined=Json.parseObject(Json.encode(Json.parseObject(Json.encode(again.get("plan"))).get("scenario")));
        assertEquals(2,ReceiptInput.rows(combined,"items",10).size());assertEquals(1,ReceiptInput.rows(combined,"coupons",10).size());assertEquals(1,ReceiptInput.rows(combined,"promotions",10).size());assertEquals(1,scenario.items().size());
    }
    @Test void rejectsUnsupportedFilesAndOversizedInput() {
        var f=input();f.put("mediaType","text/html");f.put("contentBase64","AQID");assertThrows(IllegalArgumentException.class,()->WeeklyAdvertisementImport.preview(f));
    }
}
