package coupons.api;

import coupons.ReceiptObservations;
import static coupons.api.ReceiptInput.*;

import coupons.*;
import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static coupons.api.Json.*;

/** Web request boundary only: builds existing receipt models; no persistence or financial formula. */
record ReceiptInput(UUID id,CouponTransaction transaction,ReceiptMeta metadata,List<ReceiptEvidence> evidence,
                    String reviewToken,String suppliedReviewToken,boolean acknowledgeDifference) {
    ReceiptRecord record(){return new ReceiptRecord(id,ReceiptStatus.ACTIVE,1,transaction,metadata,evidence.stream().map(ReceiptEvidence::getEvidenceId).toList());}
    static void validateRewardOnly(ReceiptMeta metadata,CouponTransaction tx){if(metadata==null||metadata.receiptKind()!=ReceiptMeta.ReceiptKind.REWARD_ADJUSTMENT)return;if(!tx.getPurchaseItems().isEmpty()||!tx.getAppliedCoupons().isEmpty()||!tx.getPayments().isEmpty()||!tx.getObservations().unidentifiedRR().isEmpty()||tx.getReceiptTotals().getTaxAmount().signum()!=0||tx.getReceiptTotals().getFinalTotal().signum()!=0)throw new IllegalArgumentException("Reward-only receipts must have no purchased items, payments, redemptions, tax or purchase total.");if(tx.getObservations().earnings().stream().noneMatch(e->e.kind()==EarningKind.WALGREENS_CASH&&e.status()==RebateStatus.CREDITED&&e.confirmed()!=null&&e.confirmed().amount().signum()>0))throw new IllegalArgumentException("Record the Walgreens Cash actually added on this adjustment receipt.");}
    static ReceiptInput read(Map<String,Object> fields) {
        known(fields,"receiptDraftId","receiptDraftRevision","shoppingPlanId","shoppingPlanRevision","clippedCouponIds","printedFooter","transactionId","retailer","receiptNumber","storeReference","purchaseDate","printedTime","purchaseChannel","receiptKind","printedTotalBasis","paymentEntryStatus","tax","reportedTotal","items","discounts","payments","rrUsed","rrUsageId","rrUses","earnings","evidence","reviewToken","acknowledgeDifference");
        if(optional(fields,"shoppingPlanId")!=null){UUID.fromString(required(fields,"shoppingPlanId"));if(optional(fields,"shoppingPlanRevision")==null||Long.parseLong(required(fields,"shoppingPlanRevision"))<1)throw new IllegalArgumentException("A saved shopping-plan revision is required.");}
        else if(optional(fields,"shoppingPlanRevision")!=null)throw new IllegalArgumentException("Shopping-plan revision requires a plan ID.");
        UUID id=uuid(fields,"transactionId");LocalDate day=optional(fields,"purchaseDate")==null?null:LocalDate.parse(required(fields,"purchaseDate"));
        String time=optional(fields,"printedTime");LocalTime parsed=time==null?null:ReceiptTimeInput.parse(time).orElseThrow(()->new IllegalArgumentException("Use a valid receipt time."));
        var metadata=new ReceiptMeta(required(fields,"retailer"),optional(fields,"receiptNumber"),parsed==null||day==null?null:day.atTime(parsed),optional(fields,"storeReference"),ReceiptMeta.PurchaseChannel.valueOf(required(fields,"purchaseChannel")),
                optional(fields,"printedTotalBasis") == null ? ReceiptMeta.PrintedTotalBasis.UNKNOWN : ReceiptMeta.PrintedTotalBasis.valueOf(required(fields,"printedTotalBasis")),
                optional(fields,"paymentEntryStatus") == null ? ReceiptMeta.PaymentEntryStatus.UNKNOWN : ReceiptMeta.PaymentEntryStatus.valueOf(required(fields,"paymentEntryStatus")),optional(fields,"receiptKind")==null?ReceiptMeta.ReceiptKind.PURCHASE:ReceiptMeta.ReceiptKind.valueOf(required(fields,"receiptKind")));
        var tx=new CouponTransaction(new ReceiptTotals(decimal(fields,"tax",false),decimal(fields,"reportedTotal",false)));
        var itemRows=rows(fields,"items",100);if(itemRows.isEmpty()&&metadata.receiptKind()!=ReceiptMeta.ReceiptKind.REWARD_ADJUSTMENT)throw new IllegalArgumentException("At least one purchased product is required.");
        for(var item:itemRows) {
            known(item,"lineItemId","description","sku","regularUnitPrice","unitPrice","quantity");long quantity=number(item.get("quantity"));if(quantity<1||quantity>Integer.MAX_VALUE)throw new IllegalArgumentException("Quantity must be a positive whole number.");
            tx.addPurchaseItem(new PurchaseItem(uuid(item,"lineItemId"),required(item,"description"),optional(item,"sku"),decimal(item,"regularUnitPrice",true),decimal(item,"unitPrice",false),(int)quantity));
        }
        Map<UUID,DiscountKind> kinds=new HashMap<>();
        for(var d:rows(fields,"discounts",100)) {
            known(d,"applicationId","label","amount","kind","lineItemIds","wholeReceipt","qualifyingQuantities","requiredQuantity");var kind=DiscountKind.valueOf(required(d,"kind"));
            if(!Set.of(DiscountKind.SALE_PROMOTION,DiscountKind.STORE_COUPON,DiscountKind.MANUFACTURER).contains(kind))throw new IllegalArgumentException("Choose a supported discount type.");
            boolean whole=flag(d,"wholeReceipt");var lines=ids(d,"lineItemIds");if(!whole&&lines.isEmpty())throw new IllegalArgumentException("Select discount products or Whole receipt.");
            var coupon=new CouponApplied(uuid(d,"applicationId"),required(d,"label"),decimal(d,"amount",false),lines,requiredQuantity(d),whole,quantities(d));tx.addAppliedCoupon(coupon);kinds.put(coupon.getApplicationId(),kind);
        }
        Map<UUID, ReceiptWorksheetData.Use> paymentUses = new HashMap<>();
        for(var p:rows(fields,"payments",20)) {
            known(p,"paymentId","type","amount","notes","role","accountName");var type=PaymentType.valueOf(required(p,"type"));
            if(!Set.of(PaymentType.CASH,PaymentType.CARD,PaymentType.GIFT_CARD,PaymentType.OTHER,PaymentType.WALGREENS_CASH).contains(type))throw new IllegalArgumentException("Unsupported payment type; record RR use separately.");
            UUID paymentId = uuid(p,"paymentId");
            String role = optional(p,"role");
            if (role != null) {
                if (!Set.of("FUNDING", "CHECKOUT_DEDUCTION", "UNRESOLVED").contains(role))
                    throw new IllegalArgumentException("Unsupported payment role.");
                if (type != PaymentType.OTHER && !role.equals("FUNDING"))
                    throw new IllegalArgumentException("Only Other tender may be explicitly recorded as a deduction; record RR separately.");
                if (!role.equals("UNRESOLVED")) paymentUses.put(paymentId,
                        new ReceiptWorksheetData.Use(List.of(), null, role.equals("CHECKOUT_DEDUCTION")));
            }
            tx.addPayment(new Payment(paymentId,type,decimal(p,"amount",false),optional(p,"notes"),optional(p,"accountName")));
        }
        var rr=decimal(fields,"rrUsed",true);
        if(fields.containsKey("rrUses") && rr != null && rr.signum()!=0) throw new IllegalArgumentException("Enter separate RR uses or the legacy combined amount, not both.");
        var uses = new ArrayList<UnidentifiedRR>();
        if(fields.containsKey("rrUses")) {
            var seen = new HashSet<UUID>();
            for(var row:rows(fields,"rrUses",100)) {
                known(row,"eventId","amount","recordedDescription");
                var event=uuid(row,"eventId"); var value=decimal(row,"amount",false);
                if(!seen.add(event)) throw new IllegalArgumentException("Each RR use must have its own identifier.");
                if(value.signum()<=0) throw new IllegalArgumentException("Each RR used amount must be greater than zero.");
                var description=optional(row,"recordedDescription");
                uses.add(new UnidentifiedRR(event,value,description==null || description.isBlank()?"Register Rewards used":description.trim()));
            }
        } else if(rr!=null && rr.signum()!=0) uses.add(new UnidentifiedRR(uuid(fields,"rrUsageId"),rr,"Recorded RR use; original printed coupon not yet matched"));
        List<Earning> earnings=new ArrayList<>();
        for(var e:rows(fields,"earnings",100))earnings.add(EarningInput.read(e));
        // User-authorized default: one received 25-point Fetch entry per new receipt.
        if(earnings.stream().noneMatch(e -> e.provider()!=null && ProviderBalanceSummary.accountName(e.provider()).equals("Fetch"))) {
            var fetchId=UUID.nameUUIDFromBytes((id+":automatic-fetch").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            earnings.add(EarningInput.read(new LinkedHashMap<>(Map.of(
                    "earningId",fetchId.toString(),"kind","THIRD_PARTY_REBATE","provider","Fetch","form","POINTS",
                    "amount","25","status","CREDITED","confirmedAmount","25","unit","points",
                    "confirmAmountAndUnit",true,"offer","Automatic receipt points — user default"))));
        }
        ReceiptEarningRules.distinct(earnings);
        var evidence=evidence(fields,id);
        var footer = PrintedFooter.read(fields, new ReceiptObservations(null,null,List.of(),List.of(),null,List.of(),Map.of()));
        earnings=printedCashEarnings(id,metadata,footer,earnings);
        tx.setObservations(new ReceiptObservations(day,time,earnings,uses,footer.walgreensCash(),footer.savings(),kinds,
                footer.receiptSavingValuePercent(), List.of(), new ReceiptWorksheetData(Map.of(), paymentUses, null), footer.footerStatus()));
        var result=new ReceiptInput(id,tx,metadata,List.copyOf(evidence),digest(fields),optional(fields,"reviewToken"),flag(fields,"acknowledgeDifference"));validateRewardOnly(metadata,tx);ReceiptValidator.validate(result.record());return result;
    }
    static UUID printedCashId(UUID receiptId){return UUID.nameUUIDFromBytes((receiptId+":automatic-printed-walgreens-cash").getBytes(StandardCharsets.UTF_8));}
    /** Recorded earned amount only; explicit reward entries override this default, as with Fetch. */
    static List<Earning> printedCashEarnings(UUID receiptId,ReceiptMeta metadata,ReceiptObservations footer,List<Earning> original){
        UUID automaticId=printedCashId(receiptId);
        // A later explicit status correction must not be silently changed back to Received.
        if(original.stream().anyMatch(e->e.earningId().equals(automaticId)&&e.status()!=RebateStatus.CREDITED))return new ArrayList<>(original);
        var earnings=new ArrayList<>(original);earnings.removeIf(e->e.earningId().equals(automaticId));
        boolean walgreens=metadata!=null&&metadata.retailer()!=null&&metadata.retailer().trim().matches("(?i)^walgreens(?:\\s|#|$).*");
        var cash=footer.walgreensCash();
        if(walgreens&&footer.footerStatus()!=FooterStatus.NOT_PRINTED&&cash!=null&&cash.earned()!=null&&cash.earned().signum()>0&&earnings.stream().noneMatch(e->e.kind()==EarningKind.WALGREENS_CASH)) {
            earnings.add(EarningInput.read(new LinkedHashMap<>(Map.of(
                "earningId",automaticId.toString(),"kind","WALGREENS_CASH","provider","Walgreens","form","STORE_REWARD",
                "amount",cash.earned().toPlainString(),"status","CREDITED","confirmedAmount",cash.earned().toPlainString(),"unit","USD",
                "confirmAmountAndUnit",true,"offer","Printed Walgreens Cash Earned This Visit — automatic"))));
        }
        return earnings;
    }
    static List<ReceiptEvidence> evidence(Map<String,Object> fields,UUID id) {
        var evidence=new ArrayList<ReceiptEvidence>();int bytes=0;
        for(var e:rows(fields,"evidence",4)) {
            known(e,"evidenceId","mediaType","contentBase64");String type=required(e,"mediaType");if(!Set.of("image/jpeg","image/png","image/webp","application/pdf").contains(type))throw new IllegalArgumentException("Evidence must be JPEG, PNG, WebP or PDF.");
            byte[] content=Base64.getDecoder().decode(required(e,"contentBase64"));bytes+=content.length;if(content.length>5*1024*1024||bytes>10*1024*1024)throw new IllegalArgumentException("Evidence limit: 5 MiB each, 10 MiB total.");
            evidence.add(new ReceiptEvidence(uuid(e,"evidenceId"),id,type,content));
        }
        return List.copyOf(evidence);
    }
    static Map<UUID,Integer> quantities(Map<String,Object> f) {
        var out=new HashMap<UUID,Integer>();if(f.get("qualifyingQuantities")==null)return out;
        if(!(f.get("qualifyingQuantities") instanceof Map<?,?> q))throw new IllegalArgumentException("Quantities must map line IDs to whole numbers.");
        for(var entry:q.entrySet())out.put(UUID.fromString(asText(entry.getKey())),Math.toIntExact(number(entry.getValue())));return out;
    }
    static UUID uuid(Map<String,Object> m,String name){return UUID.fromString(required(m,name));}
    static BigDecimal decimal(Map<String,Object> m,String name,boolean nullable){String text=optional(m,name);if(text==null&&nullable)return null;if(text==null||text.length()>80||!text.matches("(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)"))throw new IllegalArgumentException(name+" must be a nonnegative decimal string.");return new BigDecimal(text);}
    static boolean flag(Map<String,Object> m,String name){var value=m.get(name);if(value!=null&&!(value instanceof Boolean))throw new IllegalArgumentException(name+" must be a boolean.");return Boolean.TRUE.equals(value);}
    static List<UUID> ids(Map<String,Object> m,String name){var v=m.get(name);if(v==null)return List.of();if(!(v instanceof List<?> l))throw new IllegalArgumentException(name+" must be a list.");return l.stream().map(x->UUID.fromString(asText(x))).toList();}
    static Integer requiredQuantity(Map<String,Object> fields) {
        if(fields.get("requiredQuantity")==null)return null;
        long value=number(fields.get("requiredQuantity"));
        if(value<1 || value>Integer.MAX_VALUE)throw new IllegalArgumentException("Required purchase quantity must be a positive whole number.");
        return (int)value;
    }
    static List<Map<String,Object>> rows(Map<String,Object> m,String name,int max){var v=m.get(name);if(v==null)return List.of();if(!(v instanceof List<?> l)||l.size()>max)throw new IllegalArgumentException(name+" has an invalid number of rows.");var out=new ArrayList<Map<String,Object>>();for(var x:l){if(!(x instanceof Map<?,?>))throw new IllegalArgumentException(name+" rows must be objects.");@SuppressWarnings("unchecked") var row=(Map<String,Object>)x;out.add(row);}return out;}
    static String digest(Map<String,Object> m){var copy=new LinkedHashMap<>(m);copy.remove("reviewToken");copy.remove("acknowledgeDifference");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.object(copy).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}

/** Optional printed observations; never creates payments, account openings or earnings. */
final class PrintedFooter {
    static final Map<String,String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("myWalgreensSavings","myWalgreens Savings");
        LABELS.put("storeCouponSavings","Store coupon savings");
        LABELS.put("manufacturerCouponSavings","Mfg coupon savings");
        LABELS.put("totalSavings","Your total Savings");
    }
    static ReceiptObservations read(Map<String,Object> request, ReceiptObservations old) {
        if (!request.containsKey("printedFooter")) return old;
        if (!(request.get("printedFooter") instanceof Map<?,?> raw)) throw new IllegalArgumentException("printedFooter must be an object.");
        @SuppressWarnings("unchecked") var f=(Map<String,Object>)raw;
        known(f,"status","myWalgreensSavings","storeCouponSavings","manufacturerCouponSavings","totalSavings","savingsValuePercent","wcOpening","wcEarned","wcUsed","wcClosing","wcAvailable");
        var savings=new ArrayList<>(old.savings());
        for (var entry:LABELS.entrySet()) {
            if (!f.containsKey(entry.getKey())) continue;
            var prior=savings.stream().filter(c->c.printedLabel().equalsIgnoreCase(entry.getValue())).findFirst().orElse(null);
            savings.removeIf(c->c.printedLabel().equalsIgnoreCase(entry.getValue()));
            var amount=decimal(f,entry.getKey(),true);
            if(amount!=null) savings.add(new SavingsCategory(prior==null?UUID.randomUUID():prior.categoryId(),entry.getValue(),Currency.getInstance("USD"),amount,
                prior!=null && prior.reportedAmount().compareTo(amount)==0?prior.inclusions():List.of(),prior!=null && prior.reportedAmount().compareTo(amount)==0 && prior.explanationComplete()));
        }
        var wc=old.walgreensCash();
        if (List.of("wcOpening","wcEarned","wcUsed","wcClosing","wcAvailable").stream().anyMatch(f::containsKey)) {
            wc=new WalgreensCashAccount(f.containsKey("wcOpening")?decimal(f,"wcOpening",true):wc==null?null:wc.startingBalance(),
                f.containsKey("wcUsed")?decimal(f,"wcUsed",true):wc==null?null:wc.used(),
                f.containsKey("wcEarned")?decimal(f,"wcEarned",true):wc==null?null:wc.earned(),
                f.containsKey("wcClosing")?decimal(f,"wcClosing",true):wc==null?null:wc.endingBalance(),
                f.containsKey("wcAvailable")?decimal(f,"wcAvailable",true):wc==null?null:wc.availableBalance());
        }
        return new ReceiptObservations(old.purchaseDate(),old.printedTime(),old.earnings(),old.unidentifiedRR(),wc,savings,old.discountKinds(),
            f.containsKey("savingsValuePercent")?decimal(f,"savingsValuePercent",true):old.receiptSavingValuePercent(),old.promotionLinks(),old.worksheet(),
            f.containsKey("status")?FooterStatus.valueOf(required(f,"status")):old.footerStatus());
    }
}

/** Corrections start from the saved snapshot, preserving unedited facts and stable event IDs. */
record ReceiptCorrection(CouponTransaction transaction, ReceiptMeta metadata, String reason, String token) {
    static final class InvalidFields extends IllegalArgumentException {
        InvalidFields(String message) { super(message); }
    }
    static ReceiptCorrection read(ReceiptRecord old, Map<String,Object> f) {
        try {return parse(old,f);} catch(IllegalArgumentException error) {
            String message=error.getMessage();
            if(message==null || message.startsWith("No enum constant"))message="Choose a valid receipt, payment or discount option.";
            if(message.contains("Payment amount must be greater than zero"))message="Payment amounts must be greater than zero. Remove unused payment rows instead of entering zero.";
            throw new InvalidFields(message);
        }
    }
    private static ReceiptCorrection parse(ReceiptRecord old, Map<String, Object> f) {
        known(f, "printedFooter", "retailer", "receiptNumber", "storeReference", "purchaseDate", "printedTime", "purchaseChannel", "receiptKind",
                "printedTotalBasis", "paymentEntryStatus", "tax", "reportedTotal", "items", "payments",
                "discounts", "rrUses", "rrReclassifications", "reason", "reviewToken", "acknowledgeDifference");
        var s = ReceiptSnapshot.from(old.transaction());
        var o = s.observations();
        var m = old.metadata();
        LocalDate day = f.containsKey("purchaseDate") ? optional(f,"purchaseDate") == null ? null : LocalDate.parse(required(f,"purchaseDate")) : o.purchaseDate();
        String time = f.containsKey("printedTime") ? optional(f,"printedTime") : o.printedTime();
        // Unchanged historical time text is preserved, even if its original format is unsupported.
        if (!Objects.equals(time, o.printedTime()) && time != null && ReceiptTimeInput.parse(time).isEmpty())
            throw new IllegalArgumentException("Use a valid receipt time.");
        boolean dateChanged = !Objects.equals(day,o.purchaseDate()) || !Objects.equals(time,o.printedTime());
        LocalDateTime purchased = !dateChanged && m != null ? m.purchasedAt()
                : day != null && ReceiptTimeInput.parse(time).isPresent() ? day.atTime(ReceiptTimeInput.parse(time).orElseThrow()) : null;
        var metadata = new ReceiptMeta(f.containsKey("retailer") ? required(f,"retailer") : m == null ? "Store not recorded" : m.retailer(),
                f.containsKey("receiptNumber") ? optional(f,"receiptNumber") : m == null ? null : m.receiptNumber(),
                f.containsKey("purchaseDate") || f.containsKey("printedTime") ? purchased : m == null ? null : m.purchasedAt(),
                f.containsKey("storeReference") ? optional(f,"storeReference") : m == null ? null : m.storeReference(),
                f.containsKey("purchaseChannel") ? ReceiptMeta.PurchaseChannel.valueOf(required(f,"purchaseChannel")) : m == null ? ReceiptMeta.PurchaseChannel.UNKNOWN : m.purchaseChannel(),
                f.containsKey("printedTotalBasis") ? ReceiptMeta.PrintedTotalBasis.valueOf(required(f,"printedTotalBasis")) : m == null ? ReceiptMeta.PrintedTotalBasis.UNKNOWN : m.printedTotalBasis(),
                f.containsKey("paymentEntryStatus") ? ReceiptMeta.PaymentEntryStatus.valueOf(required(f,"paymentEntryStatus")) : m == null ? ReceiptMeta.PaymentEntryStatus.UNKNOWN : m.paymentEntryStatus(),
                f.containsKey("receiptKind")?ReceiptMeta.ReceiptKind.valueOf(required(f,"receiptKind")):m==null?ReceiptMeta.ReceiptKind.PURCHASE:m.receiptKind());
        var items = f.containsKey("items") ? rows(f,"items",100).stream().map(row -> {
            known(row,"lineItemId","productName","productCode","regularUnitPrice","saleUnitPrice","quantity");
            UUID lineId = uuid(row,"lineItemId");
            var regular = decimal(row,"regularUnitPrice",true);
            var previous = s.purchaseItems().stream().filter(p -> p.lineItemId().equals(lineId)).findFirst().orElse(null);
            var base = row.containsKey("saleUnitPrice") ? decimal(row,"saleUnitPrice",false) : regular != null ? regular : previous == null ? null : previous.saleUnitPrice();
            if (base == null) throw new IllegalArgumentException("Enter a regular price for new products.");
            return new PurchaseLineSnap(lineId, required(row,"productName"), optional(row,"productCode"), regular, base, Math.toIntExact(number(row.get("quantity"))));
        }).toList() : s.purchaseItems();
        if (items.isEmpty() && metadata.receiptKind()!=ReceiptMeta.ReceiptKind.REWARD_ADJUSTMENT) throw new IllegalArgumentException("A purchase receipt needs at least one product.");
        var payments = f.containsKey("payments") ? rows(f,"payments",100).stream().map(row -> {
            known(row,"paymentId","paymentType","amount","notes","role","accountName");
            UUID id = uuid(row,"paymentId");
            var previous = s.payments().stream().filter(p -> p.paymentId().equals(id)).findFirst().orElse(null);
            var type=PaymentType.valueOf(required(row,"paymentType"));
            String account=type==PaymentType.OTHER || type==PaymentType.GIFT_CARD ? row.containsKey("accountName")?optional(row,"accountName"):previous==null?null:previous.accountName():null;
            return new PaymentSnap(id,type,decimal(row,"amount",false),optional(row,"notes"),account);
        }).toList() : s.payments();
        var kinds = new HashMap<>(o.discountKinds());
        var discounts = f.containsKey("discounts") ? rows(f,"discounts",100).stream().map(row -> {
            known(row,"applicationId","printedLabel","appliedAmount","kind","lineItemIds","wholeReceipt","qualifyingQuantities","requiredQuantity");
            UUID id = uuid(row,"applicationId");
            var previous = s.appliedCoupons().stream().filter(c -> c.getApplicationId().equals(id)).findFirst().orElse(null);
            if (row.containsKey("kind")) kinds.put(id,coupons.ReceiptObservations.DiscountKind.valueOf(required(row,"kind")));
            var lines = row.containsKey("lineItemIds") ? ids(row,"lineItemIds") : previous == null ? List.<UUID>of() : previous.getLineItemIds();
            boolean whole = row.containsKey("wholeReceipt") ? flag(row,"wholeReceipt") : previous == null || previous.isWholeReceipt();
            var quantities = row.containsKey("qualifyingQuantities") ? quantities(row) : previous == null ? Map.<UUID,Integer>of() : previous.getQualifyingQuantities();
            return new CouponApplied(id, required(row,"printedLabel"), decimal(row,"appliedAmount",false), lines,
                    row.containsKey("requiredQuantity") ? requiredQuantity(row) : previous == null ? null : previous.getRequiredQuantity(), whole, quantities);
        }).toList() : s.appliedCoupons();
        kinds.keySet().retainAll(discounts.stream().map(CouponApplied::getApplicationId).toList());
        var rrUses=f.containsKey("rrUses")?rows(f,"rrUses",100).stream().map(row->{
            known(row,"eventId","amount","recordedDescription","notes");
            return new ReceiptObservations.UnidentifiedRR(uuid(row,"eventId"),decimal(row,"amount",false),required(row,"recordedDescription"),optional(row,"notes"));
        }).toList():o.unidentifiedRR();
        var uses=new HashMap<>(o.worksheet().uses());
        var validUses=new HashSet<UUID>();payments.forEach(p->validUses.add(p.paymentId()));rrUses.forEach(r->validUses.add(r.eventId()));s.registerRewardRedemptions().forEach(r->validUses.add(r.getRedemptionId()));uses.keySet().retainAll(validUses);
        if(f.containsKey("payments"))for(var row:rows(f,"payments",100)) {
            if(!row.containsKey("role"))continue;
            UUID id=uuid(row,"paymentId");var role=TransactionCalculator.PaymentRole.valueOf(required(row,"role"));
            var payment=payments.stream().filter(p->p.paymentId().equals(id)).findFirst().orElseThrow();
            if(payment.paymentType()!=PaymentType.OTHER && role!=TransactionCalculator.PaymentRole.FUNDING)throw new IllegalArgumentException("Move Register Rewards to RR used; cash/card is payment funding.");
            var prior=uses.get(id);
            if(role==TransactionCalculator.PaymentRole.UNRESOLVED)uses.remove(id);
            else uses.put(id,new ReceiptWorksheetData.Use(prior==null?List.of():prior.lineIds(),prior==null?null:prior.originalUnits(),role==TransactionCalculator.PaymentRole.CHECKOUT_DEDUCTION));
        }
        var converted=new HashMap<ReceiptObservations.EventReference,ReceiptObservations.EventReference>();
        for(var row:rows(f,"rrReclassifications",100)) {
            known(row,"kind","id","targetEventId");UUID id=uuid(row,"id"),target=uuid(row,"targetEventId");
            var kind=ReceiptObservations.EventKind.valueOf(required(row,"kind"));
            java.math.BigDecimal amount;
            if(kind==ReceiptObservations.EventKind.PAYMENT) {
                amount=s.payments().stream().filter(p->p.paymentId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Original payment not found.")).amount();
                if(payments.stream().anyMatch(p->p.paymentId().equals(id)))throw new IllegalArgumentException("Remove the original payment when moving it to RR used.");
            } else if(kind==ReceiptObservations.EventKind.COUPON_APPLIED) {
                amount=s.appliedCoupons().stream().filter(c->c.getApplicationId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Original discount not found.")).getAppliedAmount();
                if(discounts.stream().anyMatch(c->c.getApplicationId().equals(id)))throw new IllegalArgumentException("Remove the original discount when moving it to RR used.");
            } else throw new IllegalArgumentException("Only a payment or listed discount can be moved to RR used.");
            var targetUse=rrUses.stream().filter(r->r.eventId().equals(target)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose an RR use for the reclassified amount."));
            if(targetUse.amount().compareTo(amount)!=0)throw new IllegalArgumentException("The original amount and selected RR use must match exactly.");
            converted.put(new ReceiptObservations.EventReference(kind,id),new ReceiptObservations.EventReference(ReceiptObservations.EventKind.UNIDENTIFIED_RR,target));
        }
        var worksheet=new ReceiptWorksheetData(o.worksheet().earningGroups(),uses,o.worksheet().dayOrder());
        var footer = PrintedFooter.read(f,o);
        var savings=footer.savings().stream().map(category->new ReceiptObservations.SavingsCategory(category.categoryId(),category.printedLabel(),category.currency(),category.reportedAmount(),
            category.inclusions().stream().map(i->new ReceiptObservations.Inclusion(converted.getOrDefault(i.event(),i.event()),i.includedAmount(),i.confirmedBy(),i.confirmedAt())).toList(),category.explanationComplete())).toList();
        var correctedEarnings=f.containsKey("printedFooter")?ReceiptInput.printedCashEarnings(old.transactionId(),metadata,footer,o.earnings()):o.earnings();
        var observations = new ReceiptObservations(day,time,correctedEarnings,rrUses,footer.walgreensCash(),savings,kinds,footer.receiptSavingValuePercent(),o.promotionLinks(),worksheet,footer.footerStatus());
        var totals = new ReceiptTotalsSnap(f.containsKey("tax") ? decimal(f,"tax",false) : s.receiptTotals().taxAmount(), f.containsKey("reportedTotal") ? decimal(f,"reportedTotal",false) : s.receiptTotals().finalTotal());
        var snapshot = new ReceiptSnapshot(items,s.coupons(),discounts,s.promotions(),payments,s.rewards(),s.registerRewardRedemptions(),totals,observations);
        var tx = snapshot.toTransaction();
        ReceiptInput.validateRewardOnly(metadata,tx);
        ReceiptValidator.validate(new ReceiptRecord(old.transactionId(),ReceiptStatus.ACTIVE,old.revision(),tx,metadata,old.evidenceIds()));
        // Include revision so a preview cannot be reused after an intervening write.
        var payload = new LinkedHashMap<String,Object>(f);
        payload.put("expectedRevision", old.revision());
        return new ReceiptCorrection(tx,metadata,required(f,"reason"),ReceiptInput.digest(payload));
    }
    /** Editable projection only: historical price reductions become explicit item-linked draft discounts. */
    static List<Map<String,Object>> editDiscounts(ReceiptRecord receipt) {
        var snapshot = ReceiptSnapshot.from(receipt.transaction());
        List<Map<String,Object>> result = new ArrayList<>();
        for (var discount : snapshot.appliedCoupons()) {
            var f = new LinkedHashMap<String,Object>();
            f.put("applicationId",discount.getApplicationId()); f.put("printedLabel",discount.getPrintedLabel()); f.put("appliedAmount",discount.getAppliedAmount());
            f.put("requiredQuantity",discount.getRequiredQuantity());
            f.put("kind",snapshot.observations().discountKinds().getOrDefault(discount.getApplicationId(),coupons.ReceiptObservations.DiscountKind.OTHER));
            f.put("lineItemIds",discount.getLineItemIds()); f.put("wholeReceipt",discount.isWholeReceipt()); f.put("qualifyingQuantities",discount.getQualifyingQuantities()); result.add(f);
        }
        for (var item : snapshot.purchaseItems()) {
            if (item.regularUnitPrice() == null || item.regularUnitPrice().compareTo(item.saleUnitPrice()) <= 0) continue;
            UUID id = UUID.nameUUIDFromBytes((receipt.transactionId()+":"+item.lineItemId()+":price-reduction").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            result.add(new LinkedHashMap<>(Map.of("applicationId",id,"printedLabel","Previously recorded price reduction", "appliedAmount",item.regularUnitPrice().subtract(item.saleUnitPrice()).multiply(java.math.BigDecimal.valueOf(item.quantity())),
                    "kind",coupons.ReceiptObservations.DiscountKind.SALE_PROMOTION,"lineItemIds",List.of(item.lineItemId()),"wholeReceipt",false,"qualifyingQuantities",Map.of(item.lineItemId(),item.quantity()))));
        }
        return result;
    }

}

/** Local, review-only extraction. No database writes or remote document service. */
final class ReceiptImport {
    static final String AMOUNT="-?\\$?\\s*([0-9]+[.,][0-9]{2})";
    static String match(String text,String pattern){var m=java.util.regex.Pattern.compile(pattern,java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);return m.find()?m.group(1).replace(',','.').trim():"";}
    static String value(String text,String label){return match(text,"(?m)^\\s*"+label+"\\s*:?\\s*[-−~]?\\$?\\s*([0-9]+[.,][0-9]{2})\\b");}
    static java.util.Map<String,Object> extract(String name,String type,byte[] bytes)throws Exception {
        if(bytes.length==0||bytes.length>5*1024*1024)throw new CouponApiService.ApiError(400,"Import a file up to 5 MiB.");
        String text;boolean scanned=false;
        if(type.equals("application/pdf")) {
            try(var doc=org.apache.pdfbox.Loader.loadPDF(bytes)) {
                if(doc.getNumberOfPages()>8)throw new CouponApiService.ApiError(400,"Import at most eight PDF pages per file.");
                if(doc.isEncrypted())throw new CouponApiService.ApiError(400,"Use an unlocked PDF.");
                var stripper=new org.apache.pdfbox.text.PDFTextStripper();stripper.setSortByPosition(true);var out=new StringBuilder();var renderer=new org.apache.pdfbox.rendering.PDFRenderer(doc);
                for(int i=0;i<doc.getNumberOfPages();i++) {
                    stripper.setStartPage(i+1);stripper.setEndPage(i+1);String page=stripper.getText(doc);
                    if(page.replaceAll("\\s","").length()<80) {
                        scanned=true;var box=doc.getPage(i).getCropBox();if((double)box.getWidth()*box.getHeight()*6.25>25_000_000)throw new CouponApiService.ApiError(400,"PDF page is too large to scan safely. Upload a smaller scan.");
                        var image=renderer.renderImageWithDPI(i,150);var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",png);page=ocr(png.toByteArray());image.flush();
                    }out.append(page).append('\n');
                }text=out.toString();
            }catch(java.io.IOException ex){throw new CouponApiService.ApiError(400,"The PDF could not be read. Export it as an unlocked PDF or JPEG.");}
        } else if(java.util.Set.of("image/jpeg","image/png","image/webp").contains(type)) {scanned=true;text=ocr(bytes);}
        else throw new CouponApiService.ApiError(400,"Import PDF, JPEG, PNG or WebP. Convert HEIC first.");
        if(text.isBlank())throw new CouponApiService.ApiError(400,"No receipt text could be read. Try a clearer scan.");
        if(text.length()>150000)throw new CouponApiService.ApiError(400,"Too much text for one receipt. Import fewer pages.");
        return parse(name,text,scanned);
    }
    static String ocr(byte[] image)throws Exception {return ocr(image,false);}
    static String ocr(byte[] image,boolean sparse)throws Exception {
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(image))){var readers=javax.imageio.ImageIO.getImageReaders(stream);if(readers.hasNext()){var reader=readers.next();try{reader.setInput(stream);if((long)reader.getWidth(0)*reader.getHeight(0)>25_000_000)throw new CouponApiService.ApiError(400,"Image is too large to scan. Resize it below 25 megapixels.");}finally{reader.dispose();}}}
        String executable=java.nio.file.Files.isExecutable(java.nio.file.Path.of("/opt/homebrew/bin/tesseract"))?"/opt/homebrew/bin/tesseract":"tesseract";
        Process process;
        try{process=new ProcessBuilder(executable,"stdin","stdout","-l","eng","--psm",sparse?"11":"6").redirectError(ProcessBuilder.Redirect.DISCARD).start();}
        catch(java.io.IOException ex){throw new CouponApiService.ApiError(503,"Scanned receipts need local Tesseract OCR. Text PDFs can still be imported.");}
        var output=new java.util.concurrent.atomic.AtomicReference<byte[]>(new byte[0]);
        var reader=Thread.startVirtualThread(()->{try{output.set(process.getInputStream().readNBytes(200000));}catch(java.io.IOException ignored){}});
        var writer=Thread.startVirtualThread(()->{try(var stream=process.getOutputStream()){stream.write(image);}catch(java.io.IOException ignored){}});
        try {
            if(!process.waitFor(45,java.util.concurrent.TimeUnit.SECONDS))throw new CouponApiService.ApiError(400,"Scan timed out. Try a smaller, clearer image.");
            reader.join(1000);writer.join(1000);
            if(process.exitValue()!=0)throw new CouponApiService.ApiError(400,"Image could not be scanned. Convert it to a standard JPEG or PNG.");
            return new String(output.get(),java.nio.charset.StandardCharsets.UTF_8);
        }finally{process.destroyForcibly();}
    }
    static java.util.Map<String,Object> parse(String name,String raw,boolean scanned) {
        var warnings=new java.util.ArrayList<String>();warnings.add("Review every imported value before saving. Printed labels do not prove whether a coupon was an RR.");
        if(scanned)warnings.add("Read by OCR: check digits, decimal points, quantities and product boundaries against the original.");
        var text=raw.replace('\r','\n').replace('\u00a0',' ');var lines=text.lines().map(v->v.replaceAll("\\s+"," ").trim().replaceFirst("(?i)^.{0,12}?(?=(?:SUBTOTAL|SALES TAX|TOTAL\\s+\\$?\\d|MFG (?:ECPN|CPN)|MYWALGREENS SAVINGS|RETURN VALUE|REGULAR PRICE)\\b)","")).filter(v->!v.isBlank()).toList();
        text=String.join("\n",lines);
        var result=new java.util.LinkedHashMap<String,Object>();result.put("name",name);result.put("rawText",raw);result.put("scanned",scanned);
        result.put("store",text.toUpperCase().contains("WALGREENS")?"Walgreens":"");
        if(!text.toUpperCase().contains("WALGREENS"))warnings.add("Store was not identified as Walgreens. Review all fields; this first importer is tuned for Walgreens receipts.");
        result.put("number",match(text,"(?:RFN|REN)\\s*#?\\s*([0-9][0-9 -]{14,30})").replaceAll("\\s",""));
        var day=match(text,"\\b([0-1]?[0-9]/[0-3]?[0-9]/20[0-9]{2})\\b");
        if(!day.isBlank()){try{day=java.time.LocalDate.parse(day,java.time.format.DateTimeFormatter.ofPattern("M/d/uuuu")).toString();}catch(Exception ex){day="";}}
        if(day.isBlank()){var date=match(text,"\\b((?:JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+[0-9]{1,2},?\\s+20[0-9]{2})");try{day=java.time.LocalDate.parse(date.replace(",",""),new java.time.format.DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM d uuuu").toFormatter(java.util.Locale.US)).toString();}catch(Exception ignored){}}
        result.put("storeReference",lines.stream().filter(v->v.matches("(?i).*#\\s*[0-9]{3,6}\\s+[0-9]+\\s+.*")).findFirst().orElse(""));
        result.put("day",day);result.put("time",match(text,"\\b([0-9]{1,2}:[0-9]{2}\\s*[AP]M)\\b"));
        result.put("subtotal",value(text,"SUBTOTAL"));result.put("websiteSavings",value(text,"YOU SAVED"));
        result.put("tax",value(text,"SALES TAX(?:\\s+[A-Z]?[= ]?[0-9.]+%)?"));result.put("reported",value(text,"TOTAL"));
        var footer=new java.util.LinkedHashMap<String,String>();footer.put("status","UNKNOWN");
        for(var field:java.util.Map.ofEntries(java.util.Map.entry("myWalgreensSavings","MYWALGREENS SAVINGS"),java.util.Map.entry("storeCouponSavings","STORE COUPON SAVINGS"),java.util.Map.entry("manufacturerCouponSavings","(?:MFG COUPON SAVINGS|MANUFACTURER COUPONS)"),java.util.Map.entry("totalSavings","TOTAL SAVINGS"),java.util.Map.entry("wcOpening","OPENING BALANCE"),java.util.Map.entry("wcEarned","EARNED THIS VISIT"),java.util.Map.entry("wcUsed","(?:REDEEMED|USED) THIS VISIT"),java.util.Map.entry("wcClosing","CLOSING BALANCE")).entrySet())footer.put(field.getKey(),value(text,field.getValue()));
        footer.put("wcAvailable",match(text,"(?m)^\\$?([0-9]+[.,][0-9]{2})\\s+W(?:ALGREENS)? CASH REWARDS AVAILABLE"));
        footer.put("savingsValuePercent",match(text,"SAVINGS VALUE\\s+([0-9]+(?:[.][0-9]+)?)%"));if(footer.values().stream().anyMatch(v->!v.isBlank()&&!v.equals("UNKNOWN")))footer.put("status","PRINTED");result.put("footer",footer);
        var earned=footer.get("wcEarned");if(earned.isBlank())earned=match(text,"(?m)^\\$?([0-9]+[.][0-9]{2})\\s+WALGREENS CASH REWARDS EARNED");result.put("wcEarned",earned);
        if(text.toUpperCase().contains("YOU SAVED"))warnings.add("Website savings totals can include earned rewards and may conflict with paper savings. They were not added as new discounts. If the website combines coupon amounts, use the paper source or omit the aggregate and enter separate coupon / RR rows.");
        var items=new java.util.ArrayList<java.util.Map<String,String>>();var deductions=new java.util.ArrayList<java.util.Map<String,String>>();
        java.util.Map<String,String> current=null;var pending=new java.util.ArrayList<String>();boolean finished=false;String promotion="";
        for(String line:lines) {
            String upper=line.toUpperCase(java.util.Locale.ROOT);
            if(upper.matches(".*BUY.*GET.*(?:OFF|FREE).*")){promotion=line;pending.clear();continue;}
            if(upper.matches("^(?:SUBTOTAL|SALES TAX|TOTAL\\b|MFG |MANUFACTURER COUPONS|FINAL CHARGES|VISA|MASTERCARD|CASH\\b|RFN).*"))finished=true;
            var rx=java.util.regex.Pattern.compile("(?i)^(?:\\[FSA/HSA\\]\\s*)?(?:FSA[- ]*RX|RX)\\s+([0-9]{5,9})\\s+\\$?([0-9]+[.,][0-9]{2})\\b").matcher(line);
            if(!finished&&result.get("store").equals("Walgreens")&&rx.find()) {current=new java.util.LinkedHashMap<>();current.put("id",java.util.UUID.randomUUID().toString());current.put("description","[FSA/HSA] RX");current.put("sku",rx.group(1));current.put("quantity","1");current.put("regular",rx.group(2).replace(',','.'));current.put("sale","");current.put("purchaseTotal",rx.group(2).replace(',','.'));current.put("promotion","");items.add(current);pending.clear();continue;}
            var sku=java.util.regex.Pattern.compile("\\b([0-9]{8,14})\\b").matcher(line);
            boolean hasSku=!finished&&sku.find();
            if(hasSku) {
                String before=line.substring(0,sku.start()).replaceAll("(?i)^(?:FSA|OTC|FSA/HSA)\\s*","").trim();
                String desc=before.isBlank()?String.join(" ",pending):before;
                desc=desc.replaceAll("(?i)\\[FSA/HSA\\]","").trim();
                if(desc.isBlank())desc="Product — Review Description";
                current=new java.util.LinkedHashMap<>();current.put("id",java.util.UUID.randomUUID().toString());current.put("description",desc);current.put("sku",sku.group(1));current.put("quantity","1");current.put("regular","");current.put("sale","");
                String after=line.substring(sku.end());var price=match(after,"(?:^|\\s)\\$?([0-9]+[.,][0-9]{2})\\s*$");current.put("purchaseTotal",price);
                if(price.isBlank()){price=match(desc,"(?<![0-9.])([0-9]+[.][0-9]{2})(?![0-9A-Za-z])");if(!price.isBlank()){current.put("purchaseTotal",price);current.put("description",desc.replaceFirst(java.util.regex.Pattern.quote(price),"").replaceAll("\\s+"," ").trim());}}
                String qty=match(line,"QTY\\s*:?\\s*([0-9]+)");if(!qty.isBlank())current.put("quantity",qty);
                current.put("promotion",promotion);items.add(current);pending.clear();continue;
            }
            if(current!=null&&!finished) {
                if(upper.startsWith("REGULAR PRICE")){current.put("regular",match(line,"REGULAR PRICE\\s*\\$?([0-9]+[.][0-9]{2})"));continue;}
                if(upper.matches("MYWALGREENS SA[VY]INGS.*"))continue;
                var quantity=java.util.regex.Pattern.compile("(?i)([0-9]+)\\s*@\\s*\\$?([0-9]+[.][0-9]{2})").matcher(line);
                if(quantity.find()){current.put("quantity",quantity.group(1));current.put("sale",quantity.group(2));continue;}
            }
            if(upper.startsWith("RETURN VALUE"))continue;
            if(!finished&&!upper.matches(".*(?:WALGREENS|TRANSACTION ID|STORE #|IN STORE PURCHASE|[0-9]{1,2}:[0-9]{2}|[0-9]{3}[-)][0-9]{3}[-][0-9]{4}|TX [0-9]{5}|[0-9]{5}.*(?:DR|RD|ST)|AUSTIN|THANK YOU).*")) {
                if(line.matches("[0-9]+[.][0-9]{2}")&&current!=null&&current.get("purchaseTotal").isBlank())current.put("purchaseTotal",line);
                else if(line.matches(".*[A-Za-z].*")) {pending.add(line);if(pending.size()>3)pending.removeFirst();}
            }
            if(upper.matches("^(?:MFG (?:E?CPN)|MANUFACTURER COUPONS|STORE CPN|.*OFF.*PURCHASE).*")) {
                var amount=match(line,"-?\\$?([0-9]+[.][0-9]{2})(?:\\s*(?:RR)?)?\\s*$");
                if(!amount.isBlank())deductions.add(new java.util.LinkedHashMap<>(java.util.Map.of("id",java.util.UUID.randomUUID().toString(),"label",line,"amount",amount,"kind",upper.startsWith("MFG ECPN")?"MANUFACTURER":"UNCLASSIFIED")));else warnings.add("Coupon amount could not be read: "+line+". Add or correct this deduction manually.");
            }
        }
        for(var item:items) {
            String regular=item.get("regular"),sale=item.get("sale"),total=item.get("purchaseTotal");
            if(sale.isBlank()&&!total.isBlank())sale=new java.math.BigDecimal(total).divide(new java.math.BigDecimal(item.get("quantity")),2,java.math.RoundingMode.HALF_UP).toPlainString();
            item.put("sale",sale);
            if(regular.isBlank())warnings.add(item.get("description")+": regular price was not identified. Enter it or review the source; none was invented.");
        }
        // Product savings are read within each block, never from the footer total.
        for(int i=0;i<lines.size();i++)if(lines.get(i).toUpperCase().matches("MYWALGREENS SA[VY]INGS.*")&&i+1<lines.size()&&!lines.get(i+1).toUpperCase().startsWith("MFG COUPON")) {
            String amount=match(lines.get(i),"MYWALGREENS SA[VY]INGS\\s+([0-9]+[.,][0-9]{2})");
            java.util.Map<String,String> product=null;for(var item:items){int at=text.indexOf(item.get("sku"));if(at>=0&&at<text.indexOf(lines.get(i)))product=item;}
            if(product!=null&&!amount.isBlank()&&!product.get("regular").isBlank()) {
                // Use regular basis with one explicit sale deduction, not reduced sale plus deduction.
                deductions.add(new java.util.LinkedHashMap<>(java.util.Map.of("id",java.util.UUID.randomUUID().toString(),"label",product.get("description")+" Store Promotion","amount",amount,"kind","SALE_PROMOTION","lineId",product.get("id"))));product.put("sale","");
            }
        }
        var payments=new java.util.ArrayList<java.util.Map<String,String>>();String card=value(text,"(?:VISA(?: ACCT [0-9]+)?|MASTERCARD|VISA)");String cash=value(text,"CASH");
        if(!card.isBlank())payments.add(java.util.Map.of("id",java.util.UUID.randomUUID().toString(),"type","CARD","amount",card,"notes","Imported card payment — review"));
        if(!cash.isBlank()){String change=value(text,"CHANGE");if(!change.isBlank()&&new java.math.BigDecimal(cash).compareTo(new java.math.BigDecimal(change))>=0)cash=new java.math.BigDecimal(cash).subtract(new java.math.BigDecimal(change)).toPlainString();if(new java.math.BigDecimal(cash).signum()>0)payments.add(java.util.Map.of("id",java.util.UUID.randomUUID().toString(),"type","CASH","amount",cash,"notes","Imported cash tender less printed change when available — review"));}
        if(items.isEmpty())warnings.add("Product blocks were not recognized. Use the extracted text to enter products manually.");
        if(deductions.stream().anyMatch(d->d.get("kind").equals("UNCLASSIFIED")))warnings.add("Classify each ambiguous coupon as Manufacturer, Store Coupon or Register Rewards Used before saving.");
        warnings.add("Check the Walgreens Cash footer against the original; unreadable balance and earned fields stay blank.");
        warnings.add("Payment completeness and printed-total basis need your confirmation. Footer values are observations, not extra payments or discounts.");
        for(String field:java.util.List.of("store","number","day","time","tax","reported"))if(result.get(field).toString().isBlank())warnings.add(field+": not identified. Review the original and enter it manually.");
        if(!card.isBlank()&&!result.get("reported").toString().isBlank()&&new java.math.BigDecimal(card).compareTo(new java.math.BigDecimal(result.get("reported").toString()))>0)warnings.add("Card payment exceeds the printed total. OCR may have misread it; correct the payment before saving.");
        result.put("items",items);result.put("deductions",deductions);result.put("payments",payments);result.put("warnings",warnings);return result;
    }
}
