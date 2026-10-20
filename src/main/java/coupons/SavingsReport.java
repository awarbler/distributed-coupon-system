package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.PrintStream;
import java.math.*;
import java.time.*;
import java.util.*;

/** Read-only savings from current receipts; balances spent and newly earned benefits remain separate. */
public final class SavingsReport {
    private static final Currency USD = Currency.getInstance("USD");
    private SavingsReport() { }

    public record Totals(int receipts, BigDecimal regularWithTax, BigDecimal due, BigDecimal cash,
            BigDecimal card, BigDecimal saleSavings, BigDecimal storeCoupons, BigDecimal manufacturerCoupons,
            BigDecimal unclassifiedDiscounts, BigDecimal priorRewardsUsed, BigDecimal otherTender,
            BigDecimal newStoreRewards, BigDecimal appRebates, boolean regularKnown, boolean paymentsKnown,
            boolean earningsKnown, boolean storeEarningsKnown, Map<String,BigDecimal> apps, Set<String> unvaluedApps, List<String> issues) {
        public Totals { apps=Collections.unmodifiableMap(new LinkedHashMap<>(apps)); unvaluedApps=Set.copyOf(unvaluedApps); issues=List.copyOf(issues); }
        public BigDecimal couponSavings() { return storeCoupons.add(manufacturerCoupons); }
        public BigDecimal salesAndCoupons() { return saleSavings.add(couponSavings()).add(unclassifiedDiscounts); }
        public BigDecimal newlyEarned() { return newStoreRewards.add(appRebates); }
        public BigDecimal paidAfterRebates() { return cash.add(card).subtract(newlyEarned()); }
        public BigDecimal totalSaved() { return regularWithTax.subtract(paidAfterRebates()); }
        public BigDecimal percentSaved() {
            return !regularKnown || !paymentsKnown || !earningsKnown || regularWithTax.signum()==0 ? null
                    : totalSaved().multiply(new BigDecimal("100")).divide(regularWithTax,MathContext.DECIMAL128);
        }
    }
    /** Deduplicate before filtering: a deleted latest revision must not resurrect an older active one. */
    public static List<ReceiptRecord> latestActive(List<ReceiptRecord> records) {
        var latest=new LinkedHashMap<UUID,ReceiptRecord>();
        records.forEach(r->latest.merge(r.transactionId(),r,(a,b)->a.revision()>=b.revision()?a:b));
        return latest.values().stream().filter(r->r.status()==ReceiptStatus.ACTIVE).toList();
    }
    public static List<ReceiptRecord> day(List<ReceiptRecord> records, LocalDate day) {
        return latestActive(records).stream().filter(r->day.equals(r.transaction().getObservations().purchaseDate())).toList();
    }
    public static List<ReceiptRecord> month(List<ReceiptRecord> records, YearMonth month) {
        return latestActive(records).stream().filter(r->{var day=r.transaction().getObservations().purchaseDate(); return day!=null && YearMonth.from(day).equals(month);}).toList();
    }
    static boolean earned(Earning e) {
        return e.confirmed()!=null && (e.status()==RebateStatus.CREDITED || e.status()==RebateStatus.PAID);
    }
    static BigDecimal earnedDollars(Earning e) {
        var benefit=e.confirmed();
        if(benefit.kind()==BenefitKind.POINTS) return e.valuation()!=null && USD.equals(e.valuation().currency())?e.valuation().amount():null;
        return USD.equals(benefit.currency())?benefit.amount():null;
    }
    public static Totals calculate(List<ReceiptRecord> records) {
        var active=latestActive(records); var a=new Accumulator();
        for(var receipt:active) a.add(receipt);
        return new Totals(active.size(),a.regular,a.due,a.cash,a.card,a.sale,a.store,a.mq,a.unknown,a.used,a.other,
                a.newStore,a.appsTotal,a.regularKnown,a.paymentsKnown,a.earningsKnown,a.storeKnown,a.apps,a.unvaluedApps,a.issues);
    }
    private static final class Accumulator {
        BigDecimal regular=BigDecimal.ZERO,due=BigDecimal.ZERO,cash=BigDecimal.ZERO,card=BigDecimal.ZERO,
                sale=BigDecimal.ZERO,store=BigDecimal.ZERO,mq=BigDecimal.ZERO,unknown=BigDecimal.ZERO,
                used=BigDecimal.ZERO,other=BigDecimal.ZERO,newStore=BigDecimal.ZERO,appsTotal=BigDecimal.ZERO;
        boolean regularKnown=true,paymentsKnown=true,earningsKnown=true,storeKnown=true;
        Map<String,BigDecimal> apps=new LinkedHashMap<>(); Set<String> unvaluedApps=new LinkedHashSet<>(); List<String> issues=new ArrayList<>();
        void add(ReceiptRecord receipt) {
            var tx=receipt.transaction(); var obs=tx.getObservations(); var calc=new TransactionCalculator();
            String source=receipt.metadata()==null?receipt.transactionId().toString():Objects.toString(receipt.metadata().receiptNumber(),receipt.transactionId().toString());
            regular=regular.add(tx.getReceiptTotals().getTaxAmount());
            for(int line=0;line<tx.getPurchaseItems().size();line++) {
                var item=tx.getPurchaseItems().get(line);
                if(item.getRegularUnitPrice()==null) { regularKnown=false; issues.add(source+": missing regular price for receipt line "+(line+1)); }
                else {
                    regular=regular.add(item.getRegularUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    sale=sale.add(item.getRegularUnitPrice().subtract(item.getSaleUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
            for(var coupon:tx.getAppliedCoupons()) {
                var kind=obs.discountKinds().get(coupon.getApplicationId()); var amount=coupon.getAppliedAmount();
                if(kind==DiscountKind.SALE_PROMOTION) sale=sale.add(amount);
                else if(kind==DiscountKind.STORE_COUPON) store=store.add(amount);
                else if(kind==DiscountKind.MANUFACTURER) mq=mq.add(amount);
                else unknown=unknown.add(amount);
            }
            due=due.add(tx.getReceiptTotals().getFinalTotal()); used=used.add(calc.calculateRewardRedemptionTotal(tx));
            boolean tender=false;
            for(var payment:tx.getPayments()) switch(payment.getPaymentType()) {
                case CASH -> { cash=cash.add(payment.getAmount()); tender=true; }
                case CARD,CREDIT_CARD,DEBIT_CARD -> { card=card.add(payment.getAmount()); tender=true; }
                case GIFT_CARD,OTHER -> { other=other.add(payment.getAmount()); tender=true; }
                default -> { }
            }
            // A completely reward-funded zero checkout needs no invented cash/card tender.
            if(!tender && tx.getReceiptTotals().getFinalTotal().signum()!=0) { paymentsKnown=false; issues.add(source+": cash/card or other tender not recorded"); }
            boolean wc=false;
            for(var e:obs.earnings()) {
                if(e.kind()==EarningKind.WALGREENS_CASH) wc=true;
                if(!earned(e)) continue;
                var dollars=earnedDollars(e); boolean app=e.kind()==EarningKind.THIRD_PARTY_REBATE;
                if(e.kind()==EarningKind.WALGREENS_CASH) wc=true;
                if(dollars==null) {
                    earningsKnown=false;
                    if(app) unvaluedApps.add(e.provider()); else storeKnown=false;
                    issues.add(source+": missing USD valuation for "+e.provider()+" earning "+e.earningId());
                } else if(app) { appsTotal=appsTotal.add(dollars); apps.merge(e.provider(),dollars,BigDecimal::add); }
                else newStore=newStore.add(dollars);
            }
            if(!wc && obs.walgreensCash()!=null && obs.walgreensCash().earned()!=null) newStore=newStore.add(obs.walgreensCash().earned());
        }
    }
    public static void print(String scope,List<ReceiptRecord> records,PrintStream out) {
        var t=calculate(records);
        out.println("\nSAVINGS SUMMARY — "+scope);
        out.println("Saved receipts: "+t.receipts());
        if(t.receipts()==0) { out.println("No saved receipts in this selection."); return; }
        String border="+--------------------+--------------------+----------------------+--------------------+--------------+";
        out.println(border);
        out.printf("| %-18s | %-18s | %-20s | %-18s | %-12s |%n","Regular + tax","Due at register","Paid after rebates","Total saved","% saved");
        out.println(border);
        out.printf("| %-18s | %-18s | %-20s | %-18s | %-12s |%n",amount(t.regularWithTax(),t.regularKnown()),amount(t.due(),true),
                t.paymentsKnown()?amount(t.paidAfterRebates(),t.earningsKnown()):"Missing payment",
                t.paymentsKnown()?amount(t.totalSaved(),t.regularKnown()&&t.earningsKnown()):"Missing payment",
                t.percentSaved()==null?"N/A":t.percentSaved().setScale(2,RoundingMode.HALF_UP).toPlainString()+"%");
        out.println(border);
        out.println("\nSAVINGS BREAKDOWN");
        out.println("+----------------------------------------+----------------------+ ");
        row(out,"Sale/promotion reductions",t.saleSavings(),t.regularKnown());
        row(out,"Store coupons",t.storeCoupons(),true); row(out,"Manufacturer coupons",t.manufacturerCoupons(),true);
        row(out,"Coupons only (classified)",t.couponSavings(),true);
        if(t.unclassifiedDiscounts().signum()!=0) row(out,"Combined/unclassified discounts",t.unclassifiedDiscounts(),true);
        row(out,"Sales + all coupons saved",t.salesAndCoupons(),t.regularKnown());
        var active=latestActive(records);var calc=new TransactionCalculator();
        row(out,"Register Rewards used",active.stream().map(r->calc.calculateRegisterRewardsRedeemed(r.transaction())).reduce(BigDecimal.ZERO,BigDecimal::add),true);
        row(out,"Walgreens Cash used",active.stream().map(r->calc.calculateWalgreensCashRedeemed(r.transaction())).reduce(BigDecimal.ZERO,BigDecimal::add),true);
        row(out,"Gift cards / other tender used",t.otherTender(),true);
        row(out,"Cash paid",t.cash(),true);row(out,"Card paid",t.card(),true);
        if(!t.paymentsKnown()) textRow(out,"Payment recording","Missing payment");
        for(var kind:List.of(EarningKind.REGISTER_REWARD,EarningKind.WALGREENS_CASH)) {
            BigDecimal amount=BigDecimal.ZERO;boolean known=true;
            for(var r:active) {
                boolean recorded=false;
                for(var e:r.transaction().getObservations().earnings()) if(earned(e)&&e.kind()==kind) {
                    recorded=true;var value=earnedDollars(e);if(value==null) known=false;else amount=amount.add(value);
                }
                var wc=r.transaction().getObservations().walgreensCash();
                if(kind==EarningKind.WALGREENS_CASH&&!recorded&&wc!=null&&wc.earned()!=null) amount=amount.add(wc.earned());
            }
            row(out,kind==EarningKind.REGISTER_REWARD?"Register Rewards earned":"Walgreens Cash earned",amount,known);
        }
        var names=new LinkedHashSet<>(t.apps().keySet()); names.addAll(t.unvaluedApps());
        for(String name:names) row(out,"App: "+name,t.apps().getOrDefault(name,BigDecimal.ZERO),!t.unvaluedApps().contains(name));
        row(out,"All rebate apps earned",t.appRebates(),t.unvaluedApps().isEmpty());
        row(out,"Total new earned value",t.newlyEarned(),t.earningsKnown());
        if(t.paymentsKnown()) row(out,"Total savings incl. earned benefits",t.totalSaved(),t.regularKnown()&&t.earningsKnown());
        else textRow(out,"Total savings incl. earned benefits","Missing payment");
        out.println("+----------------------------------------+----------------------+");
        out.println("Paid after rebates = cash/card paid minus newly earned value. Prior rewards used are separate from coupons.");
        out.println("Points use saved USD valuations. Expected-only rewards and online offer progress are excluded.");
        for(String issue:t.issues()) out.println("Missing data: "+issue);
    }
    /** The same immutable calculation drives receipt totals and explicitly requested period summaries. */
    public static void printCompact(String scope,List<ReceiptRecord> records,PrintStream out) {
        out.println("\nSAVINGS SUMMARY — " + scope);
        printCompactTotals(records,out,false);
    }
    static void printReceiptTotals(ReceiptRecord receipt,PrintStream out) {
        out.println("\nRECEIPT TOTALS");
        printCompactTotals(List.of(receipt),out,true);
    }
    private record SummaryRow(String label,String value) { }
    private static void printCompactTotals(List<ReceiptRecord> records,PrintStream out,boolean receipt) {
        var active=latestActive(records);var t=calculate(active);var rows=new ArrayList<SummaryRow>();
        if(!receipt)rows.add(new SummaryRow("Transactions",Integer.toString(active.size())));
        if(active.isEmpty()) {out.println("No saved receipts in this selection.");return;}
        rows.add(new SummaryRow("Products purchased",Integer.toString(active.stream().flatMap(r->r.transaction().getPurchaseItems().stream()).mapToInt(PurchaseItem::getQuantity).sum())));
        var tax=active.stream().map(r->r.transaction().getReceiptTotals().getTaxAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        rows.add(new SummaryRow("Regular merchandise",t.regularKnown()?amount(t.regularWithTax().subtract(tax),true):"unknown"));
        compactMoney(rows,"Sales tax",tax,true,false);
        rows.add(new SummaryRow("Regular total including tax",amount(t.regularWithTax(),t.regularKnown())));
        compactMoney(rows,"Sale/promotion reductions",t.saleSavings(),t.regularKnown(),false);
        compactMoney(rows,"Store / IVC / online coupons",t.storeCoupons(),true,false);
        compactMoney(rows,"Manufacturer coupons",t.manufacturerCoupons(),true,false);
        compactMoney(rows,"Unclassified deductions",t.unclassifiedDiscounts(),true,false);
        rows.add(new SummaryRow("",""));
        var calc=new TransactionCalculator();
        compactMoney(rows,"Register Rewards used",active.stream().map(r->calc.calculateRegisterRewardsRedeemed(r.transaction())).reduce(BigDecimal.ZERO,BigDecimal::add),true,false);
        compactMoney(rows,"Walgreens Cash used",active.stream().map(r->calc.calculateWalgreensCashRedeemed(r.transaction())).reduce(BigDecimal.ZERO,BigDecimal::add),true,false);
        compactMoney(rows,"Retailer total due",t.due(),true,true);
        compactMoney(rows,"Paid cash",t.cash(),true,false);compactMoney(rows,"Paid card",t.card(),true,false);
        var gift=active.stream().flatMap(r->r.transaction().getPayments().stream()).filter(p->p.getPaymentType()==PaymentType.GIFT_CARD).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        compactMoney(rows,"Paid gift cards",gift,true,false);
        compactMoney(rows,"Other payment",t.otherTender().subtract(gift),true,false);
        if(!t.paymentsKnown())rows.add(new SummaryRow("Payment recording","Missing payment"));
        rows.add(new SummaryRow("",""));
        for(var kind:List.of(EarningKind.REGISTER_REWARD,EarningKind.WALGREENS_CASH)) {
            BigDecimal total=BigDecimal.ZERO;boolean known=true;
            for(var r:active) {
                boolean recorded=false;var obs=r.transaction().getObservations();
                for(var e:obs.earnings())if(earned(e)&&e.kind()==kind) {
                    recorded=true;var value=earnedDollars(e);if(value==null)known=false;else total=total.add(value);
                }
                if(kind==EarningKind.WALGREENS_CASH&&!recorded&&obs.walgreensCash()!=null&&obs.walgreensCash().earned()!=null)total=total.add(obs.walgreensCash().earned());
            }
            compactMoney(rows,kind==EarningKind.REGISTER_REWARD?"New Register Rewards earned":"New Walgreens Cash earned",total,known,false);
        }
        compactMoney(rows,"Other rebates/point value earned",t.appRebates(),t.unvaluedApps().isEmpty(),false);
        compactMoney(rows,"TOTAL FUTURE VALUE EARNED",t.newlyEarned(),t.earningsKnown(),false);
        BigDecimal expected=BigDecimal.ZERO;boolean expectedKnown=true;boolean hasExpected=false;
        for(var r:active)for(var e:r.transaction().getObservations().earnings())if(!earned(e)&&e.status()!=RebateStatus.REJECTED) {
            hasExpected=true;var b=e.confirmed()==null?e.expected():e.confirmed();
            BigDecimal value=b.kind()==BenefitKind.POINTS?(e.valuation()!=null&&USD.equals(e.valuation().currency())?e.valuation().amount():null):USD.equals(b.currency())?b.amount():null;
            if(value==null)expectedKnown=false;else expected=expected.add(value);
        }
        if(hasExpected)compactMoney(rows,"Expected, not confirmed",expected,expectedKnown,true);
        rows.add(new SummaryRow("",""));
        rows.add(new SummaryRow("PAID AFTER EARNED BENEFITS",t.paymentsKnown()?amount(t.paidAfterRebates(),t.earningsKnown()):"unknown — tender not recorded"));
        rows.add(new SummaryRow("TOTAL SAVED",t.paymentsKnown()?amount(t.totalSaved(),t.regularKnown()&&t.earningsKnown()):"unknown — tender not recorded"));
        rows.add(new SummaryRow("PERCENT SAVED",t.percentSaved()==null?"N/A":t.percentSaved().setScale(2,RoundingMode.HALF_UP).toPlainString()+"%"));
        for(int i=rows.size()-1;i>0;i--)if(rows.get(i).label().isEmpty()&&rows.get(i-1).label().isEmpty())rows.remove(i);
        int labelWidth=Math.max(32,rows.stream().mapToInt(row->row.label().length()).max().orElse(0));
        int valueWidth=Math.max(8,rows.stream().mapToInt(row->row.value().length()).max().orElse(0));
        String border="+"+"-".repeat(labelWidth+2)+"+"+"-".repeat(valueWidth+2)+"+";
        out.println(border);
        for(var row:rows)out.printf("| %-"+labelWidth+"s | %"+valueWidth+"s |%n",row.label(),row.value());
        out.println(border);
        if(t.unclassifiedDiscounts().signum()!=0) {
            out.println("\nUNCLASSIFIED DEDUCTIONS — choose Update receipt > Discounts > Discount type");
            out.println("Date | Time | Receipt | Discount | Amount");
            for(var r:active) for(var c:r.transaction().getAppliedCoupons()) {
                var o=r.transaction().getObservations(); var kind=o.discountKinds().get(c.getApplicationId());
                if(kind!=DiscountKind.SALE_PROMOTION && kind!=DiscountKind.STORE_COUPON && kind!=DiscountKind.MANUFACTURER)
                    out.println(Objects.toString(o.purchaseDate(),"Not recorded")+" | "+Objects.toString(o.printedTime(),"Not recorded")+" | "+(r.metadata()==null?r.transactionId():r.metadata().receiptNumber())+" | "+c.getPrintedLabel()+" | "+amount(c.getAppliedAmount(),true));
            }
        }
        if(!t.unvaluedApps().isEmpty())out.println("Missing USD valuations: "+String.join(", ",t.unvaluedApps())+"; original points remain recorded.");
        if(receipt) {
            var tx=active.get(0).transaction();
            if(tx.getObservations().footerStatus()==FooterStatus.NOT_PRINTED)out.println("Walgreens savings/rewards footer: Not printed.");
            var calculated=calc.calculateExpectedCheckoutAmount(tx);
            if(calculated.compareTo(t.due())!=0)out.println("Printed total "+amount(t.due(),true)+" differs from calculated checkout "+amount(calculated,true)+"; review receipt inputs.");
        }
    }
    private static void compactMoney(List<SummaryRow> rows,String label,BigDecimal value,boolean known,boolean showZero) {
        if(!showZero&&known&&value.signum()==0)return;
        rows.add(new SummaryRow(label,amount(value,known)));
    }

    private static String amount(BigDecimal value,boolean complete) {
        String sign=value.signum()<0?"-$":"$";
        return (complete?"":"partial ")+sign+value.abs().setScale(2,RoundingMode.HALF_UP).toPlainString();
    }
    private static void row(PrintStream out,String label,BigDecimal amount,boolean complete) {
        if(complete && amount.signum()==0) return;
        textRow(out,label,amount(amount,complete));
    }
    private static void textRow(PrintStream out,String label,String text) {
        out.printf("| %-38s | %-20s |%n",label,text);
    }
}
