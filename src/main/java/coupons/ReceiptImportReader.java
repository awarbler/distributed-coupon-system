package coupons;

import coupons.ReceiptImportDraft.*;
import coupons.ReceiptObservations.*;
import coupons.repository.ReceiptEvidence;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDate;
import java.util.*;

/** Reads an assisted extraction manifest; never performs OCR or writes a financial record. */
public final class ReceiptImportReader {
    private final Properties fields=new Properties() {
        @Override public synchronized Object put(Object key,Object value) {
            if(containsKey(key))throw new IllegalArgumentException("Duplicate import field: "+key);
            return super.put(key,value);
        }
    };
    private final Set<String> consumed=new HashSet<>();
    private final List<ReviewIssue> issues=new ArrayList<>();
    private UUID id;
    private ReceiptImportReader() { }
    public static ReceiptImportDraft read(Path manifest) throws IOException {
        return new ReceiptImportReader().load(manifest.toAbsolutePath().normalize());
    }
    private ReceiptImportDraft load(Path manifest) throws IOException {
        byte[] original=Files.readAllBytes(manifest);
        fields.load(new StringReader(new String(original,StandardCharsets.UTF_8)));
        if(!"receipt-import-v1".equals(required("format")))throw new IllegalArgumentException("Unsupported import format; expected receipt-import-v1.");
        record Image(String type,byte[] content) { }
        var images=new ArrayList<Image>();var hashes=new LinkedHashSet<String>();
        int count=count("image",true);
        for(int i=1;i<=count;i++) {
            Path image=manifest.getParent().resolve(required("image."+i+".path")).normalize();
            String name=image.getFileName().toString().toLowerCase(Locale.ROOT);
            String type=name.endsWith(".png")?"image/png":name.endsWith(".jpg")||name.endsWith(".jpeg")?"image/jpeg":
                    name.endsWith(".pdf")?"application/pdf":name.endsWith(".webp")?"image/webp":name.endsWith(".heic")?"image/heic":null;
            if(type==null)throw new IllegalArgumentException("Unsupported receipt image: "+image.getFileName());
            byte[] content=Files.readAllBytes(image);
            if(ReceiptFileFormat.isHeif(content))type="image/heic";
            if(content.length==0)throw new IllegalArgumentException("Receipt image is empty: "+image.getFileName());
            if(hashes.add(hash(content)))images.add(new Image(type,content));
        }
        String suppliedId=text("receipt.id");
        id=suppliedId==null?stable(String.join("|",hashes.stream().sorted().toList())):UUID.fromString(suppliedId);
        var evidence=new ArrayList<ReceiptEvidence>();
        for(var image:images)evidence.add(new ReceiptEvidence(event("image:"+hash(image.content())),id,image.type(),image.content()));
        evidence.add(new ReceiptEvidence(event("manifest:"+hash(original)),id,"text/plain",original));
        String retailer=text("receipt.retailer"),number=text("receipt.number"),time=text("receipt.time");
        LocalDate date=date("receipt.date");
        if(date==null)issue("receipt.date","Purchase date not supplied; dated reports will exclude it unless corrected.");
        if(time==null||ReceiptTimeInput.parse(time).isEmpty())issue("receipt.time","Time is missing or unrecognized; verify it before confirming.");
        BigDecimal tax=money("receipt.tax",false),total=money("receipt.total",false);
        var items=new ArrayList<ImportedItem>();
        for(int i=1;i<=count("item",true);i++) {
            String prefix="item."+i+".";String description=text(prefix+"description"),sku=text(prefix+"sku");
            Integer quantity=integer(prefix+"quantity",1,Integer.MAX_VALUE,false);
            BigDecimal regular=money(prefix+"regularUnitPrice",false),entered=money(prefix+"enteredUnitPrice",false);
            if(regular==null)issue(prefix+"regularUnitPrice","Regular price is not provided. Keep unknown unless verified; savings will be partial.");
            items.add(new ImportedItem(event("line:"+i),description,sku,quantity,regular,entered));
        }
        var discounts=new ArrayList<ImportedDiscount>();
        for(int i=1;i<=count("coupon",false);i++) {
            String p="coupon."+i+".";var kind=enumeration(p+"kind",DiscountKind.class,null);
            if(kind==null)throw new IllegalArgumentException("Specify "+p+"kind; sale reductions must be distinguished from coupons.");
            var links=lines(p+"lines",items);var value=requiredMoney(p+"amount",true);
            var scope=text(p+"scope");
            if(scope!=null && !scope.equalsIgnoreCase("RECEIPT") && !scope.equalsIgnoreCase("UNKNOWN"))
                throw new IllegalArgumentException("Coupon scope must be RECEIPT or UNKNOWN; use lines for selected products.");
            boolean wholeReceipt="RECEIPT".equalsIgnoreCase(scope);
            var applied=new CouponApplied(event("coupon:"+i),required(p+"description"),value,links,integer(p+"requiredQuantity",1,Integer.MAX_VALUE,false),wholeReceipt);
            discounts.add(new ImportedDiscount(applied,kind));
            if(links.isEmpty()&&!wholeReceipt)issue(p+"lines","Coupon products are unknown; verify the association. The deduction is counted once.");
        }
        var payments=new ArrayList<Payment>();
        for(int i=1;i<=count("payment",false);i++) {
            String p="payment."+i+".";var type=enumeration(p+"type",PaymentType.class,null);
            if(type==null||type==PaymentType.REGISTER_REWARD)throw new IllegalArgumentException("Use rrUse entries for RR; specify a supported payment type in "+p+"type.");
            var value=requiredMoney(p+"amount",false);String notes=text(p+"notes"),account=text(p+"account");
            if(value.signum()>0)payments.add(new Payment(event("payment:"+i),type,value,notes,account));
        }
        var uses=new ArrayList<UnidentifiedRR>();
        for(int i=1;i<=count("rrUse",false);i++) {
            String p="rrUse."+i+".";String description=text(p+"description");
            uses.add(new UnidentifiedRR(event("rrUse:"+i),requiredMoney(p+"amount",true),description==null?"Receipt-reported RR; source unknown":description,text(p+"notes")));
        }
        var earnings=new ArrayList<Earning>();
        for(int i=1;i<=count("earning",false);i++) {
            String p="earning."+i+".";var kind=enumeration(p+"kind",EarningKind.class,null);
            if(kind==null)throw new IllegalArgumentException("Specify "+p+"kind.");
            String provider=required(p+"provider"),program=text(p+"program");
            var form=enumeration(p+"form",BenefitKind.class,kind==EarningKind.REGISTER_REWARD?BenefitKind.STORE_COUPON:kind==EarningKind.WALGREENS_CASH?BenefitKind.STORE_REWARD:null);
            if(form==null)throw new IllegalArgumentException("Specify the actual benefit form in "+p+"form.");
            String currency=text(p+"currency");
            if(form==BenefitKind.POINTS&&currency!=null)throw new IllegalArgumentException("Points must not have a currency in "+p+"currency; use usdValue for their separate valuation.");
            var unit=form==BenefitKind.POINTS?null:Currency.getInstance(currency==null?"USD":currency);
            if(program==null&&form==BenefitKind.POINTS)program=provider;
            var benefit=new RebateBenefit(form,requiredMoney(p+"amount",true),unit,program);
            var status=enumeration(p+"status",RebateStatus.class,RebateStatus.EXPECTED);
            var linked=lines(p+"lines",items);var scope=enumeration(p+"scope",Scope.class,linked.isEmpty()?Scope.UNKNOWN:linked.size()==1?Scope.ITEM:Scope.MULTI_ITEM);
            if(scope==Scope.UNKNOWN)issue(p+"scope","Reward product scope is unknown; verify it rather than assuming whole receipt.");
            var dollars=money(p+"usdValue",false);String basis=text(p+"valuationBasis");
            if(dollars!=null&&(basis==null||basis.isBlank()))throw new IllegalArgumentException("A saved valuation requires "+p+"valuationBasis.");
            var valuation=dollars==null?null:new RecordedValuation(dollars,Currency.getInstance("USD"),basis);
            if(form==BenefitKind.POINTS&&valuation==null)issue(p+"usdValue","Original points are preserved; dollar valuation is missing.");
            var requirementType=enumeration(p+"requirementType",PromotionType.class,null);
            var threshold=money(p+"requirementAmount",true);var spendBasis=enumeration(p+"spendBasis",SpendBasis.class,SpendBasis.UNKNOWN);
            if((requirementType==null)!=(threshold==null))throw new IllegalArgumentException("Requirement type and amount must both be supplied for "+p);
            OfferRequirement requirement=requirementType==null?null:new OfferRequirement(requirementType,threshold,null,spendBasis);
            String offer=text(p+"offer");LocalDate expires=date(p+"expires");String note=text(p+"note");
            boolean confirmed=status==RebateStatus.APPROVED||status==RebateStatus.CREDITED||status==RebateStatus.PAID;
            earnings.add(new Earning(event("earning:"+i),kind,provider,offer,scope,linked,confirmed?null:benefit,confirmed?benefit:null,
                    status,expires,null,null,List.of(),note,requirement,valuation));
        }
        var footer=enumeration("receipt.footer",FooterStatus.class,FooterStatus.UNKNOWN);
        var savings=new ArrayList<SavingsCategory>();
        String[] labels={"myWalgreens Savings","Mfg coupon savings","Your total Savings"};
        String[] keys={"footer.myWalgreensSavings","footer.manufacturerSavings","footer.totalSavings"};
        for(int i=0;i<keys.length;i++) {var amount=money(keys[i],false);if(amount!=null)savings.add(new SavingsCategory(event(keys[i]),labels[i],Currency.getInstance("USD"),amount,List.of(),false));}
        var percent=money("footer.percent",false);
        BigDecimal start=signedMoney("footer.start"),used=money("footer.used",false),earned=money("footer.earned",false),end=signedMoney("footer.end");
        var account=start==null&&used==null&&earned==null&&end==null?null:new WalgreensCashAccount(start,used,earned,end);
        if(footer==FooterStatus.NOT_PRINTED&&(account!=null||!savings.isEmpty()||percent!=null))throw new IllegalArgumentException("NOT_PRINTED footer cannot also contain printed footer amounts.");
        if(footer==FooterStatus.UNKNOWN)issue("receipt.footer","Footer printing is unknown; review the image or mark Not printed.");
        for(int i=1;i<=count("review",false);i++) {
            String p="review."+i+".";issues.add(new ReviewIssue(required(p+"field"),text(p+"source"),required(p+"reason")));
        }
        var channel=enumeration("receipt.channel",coupons.repository.ReceiptMeta.PurchaseChannel.class,coupons.repository.ReceiptMeta.PurchaseChannel.UNKNOWN);
        var extra=new TreeSet<>(fields.stringPropertyNames());extra.removeAll(consumed);
        if(!extra.isEmpty())throw new IllegalArgumentException("Unrecognized import fields: "+String.join(", ",extra));
        return new ReceiptImportDraft(id,retailer,number,date,time,tax,total,items,discounts,payments,uses,earnings,account,savings,percent,footer,evidence,issues,channel);
    }
    private void issue(String key,String explanation) {issues.add(new ReviewIssue(key,fields.getProperty(key),explanation));}
    private UUID event(String name) {return stable(id+":"+name);}
    private static UUID stable(String value) {return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private String text(String key) {consumed.add(key);String value=fields.getProperty(key);return value==null||value.isBlank()?null:value.trim();}
    private String required(String key) {String value=text(key);if(value==null)throw new IllegalArgumentException("Missing import field: "+key);return value;}
    private BigDecimal money(String key,boolean positive) {
        String value=text(key);if(value==null)return null;
        try {
            var number=new BigDecimal(value);
            if(number.signum()<0||positive&&number.signum()==0)throw new NumberFormatException();
            return number;
        } catch(NumberFormatException invalid) {throw new IllegalArgumentException("Invalid amount in "+key+": "+value);}
    }
    private BigDecimal signedMoney(String key) {
        String value=text(key);if(value==null)return null;
        try {return new BigDecimal(value);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid amount in "+key+": "+value);}
    }
    private BigDecimal requiredMoney(String key,boolean positive) {var value=money(key,positive);if(value==null)throw new IllegalArgumentException("Missing import amount: "+key);return value;}
    private Integer integer(String key,int min,int max,boolean required) {
        String raw=text(key);if(raw==null) {if(required)throw new IllegalArgumentException("Missing import field: "+key);return null;}
        try {int value=Integer.parseInt(raw);if(value<min||value>max)throw new NumberFormatException();return value;}
        catch(NumberFormatException invalid) {throw new IllegalArgumentException("Invalid whole number in "+key+": "+raw);}
    }
    private int count(String group,boolean positive) {var value=integer(group+".count",positive?1:0,1000,positive);return value==null?0:value;}
    private LocalDate date(String key) {
        String raw=text(key);if(raw==null)return null;
        var dates=ReceiptDateInput.candidates(raw);if(dates.size()!=1)throw new IllegalArgumentException("Invalid or ambiguous date in "+key+": "+raw);return dates.get(0);
    }
    private <E extends Enum<E>> E enumeration(String key,Class<E> type,E fallback) {
        String raw=text(key);if(raw==null)return fallback;
        try{return Enum.valueOf(type,raw.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Unsupported value in "+key+": "+raw);}
    }
    private List<UUID> lines(String key,List<ImportedItem> items) {
        String raw=text(key);if(raw==null)return List.of();var ids=new ArrayList<UUID>();
        try {for(String part:raw.split(",",-1)) {int index=Integer.parseInt(part.trim())-1;if(index<0||index>=items.size()||ids.contains(items.get(index).lineId()))throw new IllegalArgumentException();ids.add(items.get(index).lineId());}}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Choose distinct existing line numbers in "+key+": "+raw);}
        return List.copyOf(ids);
    }
}
