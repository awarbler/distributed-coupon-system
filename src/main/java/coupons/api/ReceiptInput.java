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
    static ReceiptInput read(Map<String,Object> fields) {
        known(fields,"receiptDraftId","receiptDraftRevision","shoppingPlanId","shoppingPlanRevision","clippedCouponIds","printedFooter","transactionId","retailer","receiptNumber","purchaseDate","printedTime","purchaseChannel","printedTotalBasis","paymentEntryStatus","tax","reportedTotal","items","discounts","payments","rrUsed","rrUsageId","rrUses","earnings","evidence","reviewToken","acknowledgeDifference");
        if(optional(fields,"shoppingPlanId")!=null){UUID.fromString(required(fields,"shoppingPlanId"));if(optional(fields,"shoppingPlanRevision")==null||Long.parseLong(required(fields,"shoppingPlanRevision"))<1)throw new IllegalArgumentException("A saved shopping-plan revision is required.");}
        else if(optional(fields,"shoppingPlanRevision")!=null)throw new IllegalArgumentException("Shopping-plan revision requires a plan ID.");
        UUID id=uuid(fields,"transactionId");LocalDate day=optional(fields,"purchaseDate")==null?null:LocalDate.parse(required(fields,"purchaseDate"));
        String time=optional(fields,"printedTime");LocalTime parsed=time==null?null:ReceiptTimeInput.parse(time).orElseThrow(()->new IllegalArgumentException("Use a valid receipt time."));
        var metadata=new ReceiptMeta(required(fields,"retailer"),optional(fields,"receiptNumber"),parsed==null||day==null?null:day.atTime(parsed),null,ReceiptMeta.PurchaseChannel.valueOf(required(fields,"purchaseChannel")),
                optional(fields,"printedTotalBasis") == null ? ReceiptMeta.PrintedTotalBasis.UNKNOWN : ReceiptMeta.PrintedTotalBasis.valueOf(required(fields,"printedTotalBasis")),
                optional(fields,"paymentEntryStatus") == null ? ReceiptMeta.PaymentEntryStatus.UNKNOWN : ReceiptMeta.PaymentEntryStatus.valueOf(required(fields,"paymentEntryStatus")));
        var tx=new CouponTransaction(new ReceiptTotals(decimal(fields,"tax",false),decimal(fields,"reportedTotal",false)));
        var itemRows=rows(fields,"items",100);if(itemRows.isEmpty())throw new IllegalArgumentException("At least one purchased product is required.");
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
        tx.setObservations(new ReceiptObservations(day,time,earnings,uses,footer.walgreensCash(),footer.savings(),kinds,
                footer.receiptSavingValuePercent(), List.of(), new ReceiptWorksheetData(Map.of(), paymentUses, null), footer.footerStatus()));
        var result=new ReceiptInput(id,tx,metadata,List.copyOf(evidence),digest(fields),optional(fields,"reviewToken"),flag(fields,"acknowledgeDifference"));ReceiptValidator.validate(result.record());return result;
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
        known(f,"status","myWalgreensSavings","storeCouponSavings","manufacturerCouponSavings","totalSavings","savingsValuePercent","wcOpening","wcEarned","wcUsed","wcClosing");
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
        if (List.of("wcOpening","wcEarned","wcUsed","wcClosing").stream().anyMatch(f::containsKey)) {
            wc=new WalgreensCashAccount(f.containsKey("wcOpening")?decimal(f,"wcOpening",true):wc==null?null:wc.startingBalance(),
                f.containsKey("wcUsed")?decimal(f,"wcUsed",true):wc==null?null:wc.used(),
                f.containsKey("wcEarned")?decimal(f,"wcEarned",true):wc==null?null:wc.earned(),
                f.containsKey("wcClosing")?decimal(f,"wcClosing",true):wc==null?null:wc.endingBalance());
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
        known(f, "printedFooter", "retailer", "receiptNumber", "purchaseDate", "printedTime", "purchaseChannel",
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
                m == null ? null : m.storeReference(),
                f.containsKey("purchaseChannel") ? ReceiptMeta.PurchaseChannel.valueOf(required(f,"purchaseChannel")) : m == null ? ReceiptMeta.PurchaseChannel.UNKNOWN : m.purchaseChannel(),
                f.containsKey("printedTotalBasis") ? ReceiptMeta.PrintedTotalBasis.valueOf(required(f,"printedTotalBasis")) : m == null ? ReceiptMeta.PrintedTotalBasis.UNKNOWN : m.printedTotalBasis(),
                f.containsKey("paymentEntryStatus") ? ReceiptMeta.PaymentEntryStatus.valueOf(required(f,"paymentEntryStatus")) : m == null ? ReceiptMeta.PaymentEntryStatus.UNKNOWN : m.paymentEntryStatus());
        var items = f.containsKey("items") ? rows(f,"items",100).stream().map(row -> {
            known(row,"lineItemId","productName","productCode","regularUnitPrice","saleUnitPrice","quantity");
            UUID lineId = uuid(row,"lineItemId");
            var regular = decimal(row,"regularUnitPrice",true);
            var previous = s.purchaseItems().stream().filter(p -> p.lineItemId().equals(lineId)).findFirst().orElse(null);
            var base = row.containsKey("saleUnitPrice") ? decimal(row,"saleUnitPrice",false) : regular != null ? regular : previous == null ? null : previous.saleUnitPrice();
            if (base == null) throw new IllegalArgumentException("Enter a regular price for new products.");
            return new PurchaseLineSnap(lineId, required(row,"productName"), optional(row,"productCode"), regular, base, Math.toIntExact(number(row.get("quantity"))));
        }).toList() : s.purchaseItems();
        if (items.isEmpty()) throw new IllegalArgumentException("A receipt needs at least one product.");
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
        var observations = new ReceiptObservations(day,time,o.earnings(),rrUses,footer.walgreensCash(),savings,kinds,footer.receiptSavingValuePercent(),o.promotionLinks(),worksheet,footer.footerStatus());
        var totals = new ReceiptTotalsSnap(f.containsKey("tax") ? decimal(f,"tax",false) : s.receiptTotals().taxAmount(), f.containsKey("reportedTotal") ? decimal(f,"reportedTotal",false) : s.receiptTotals().finalTotal());
        var snapshot = new ReceiptSnapshot(items,s.coupons(),discounts,s.promotions(),payments,s.rewards(),s.registerRewardRedemptions(),totals,observations);
        var tx = snapshot.toTransaction();
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
