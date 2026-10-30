package coupons.receipts;

import coupons.rewards.Reward;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.PrintStream;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** One worksheet row owns each deduction/earning exactly once; shared events stay shared. */
public final class ReceiptWorksheetReport {
    private ReceiptWorksheetReport() { }
    public static final class Row {
        public final String label; public int quantity,mqCount; public BigDecimal regular=BigDecimal.ZERO,promos=BigDecimal.ZERO,mq=BigDecimal.ZERO,used=BigDecimal.ZERO,
                cash=BigDecimal.ZERO,gift=BigDecimal.ZERO,store=BigDecimal.ZERO,points=BigDecimal.ZERO;
        public BigDecimal rrUsed=BigDecimal.ZERO,wcUsed=BigDecimal.ZERO,otherUsed=BigDecimal.ZERO,
                rrEarned=BigDecimal.ZERO,wcEarned=BigDecimal.ZERO,otherStore=BigDecimal.ZERO;
        public boolean pricesKnown=true,valuesKnown=true;
        Row(String label) { this.label=label; }
        public BigDecimal discounts() { return promos.add(mq).add(used); }
        public BigDecimal beforeRebates() { return regular.subtract(discounts()); }
        public BigDecimal earnings() { return cash.add(gift).add(store).add(points); }
        public BigDecimal saved() { return discounts().add(earnings()); }
        public BigDecimal net() { return regular.subtract(saved()); }
        void add(Row row) { rrUsed=rrUsed.add(row.rrUsed);wcUsed=wcUsed.add(row.wcUsed);otherUsed=otherUsed.add(row.otherUsed);rrEarned=rrEarned.add(row.rrEarned);wcEarned=wcEarned.add(row.wcEarned);otherStore=otherStore.add(row.otherStore);quantity+=row.quantity;mqCount+=row.mqCount;regular=regular.add(row.regular);promos=promos.add(row.promos);mq=mq.add(row.mq);used=used.add(row.used);cash=cash.add(row.cash);gift=gift.add(row.gift);store=store.add(row.store);points=points.add(row.points);pricesKnown&=row.pricesKnown;valuesKnown&=row.valuesKnown; }
    }
    public static List<Row> rows(ReceiptRecord receipt) {
        var tx=receipt.transaction();var obs=tx.getObservations();var rows=new ArrayList<Row>();var items=new HashMap<UUID,Row>();
        for(int i=0;i<tx.getPurchaseItems().size();i++) {
            var item=tx.getPurchaseItems().get(i);var row=new Row("Line "+(i+1)+": "+item.getProductName());row.quantity=item.getQuantity();
            if(item.getRegularUnitPrice()==null) { row.pricesKnown=false;row.regular=item.calculateLineTotal(); }
            else { row.regular=item.getRegularUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));row.promos=row.regular.subtract(item.calculateLineTotal()); }
            items.put(item.getLineItemId(),row);rows.add(row);
        }
        for(var c:tx.getAppliedCoupons()) {
            Row row=c.getLineItemIds().size()==1?items.get(c.getLineItemIds().get(0)):null;
            if(row==null) { row=new Row("Shared/receipt coupon: "+c.getPrintedLabel()+" ["+(c.isWholeReceipt()?"Whole receipt":lineNumbers(tx,c.getLineItemIds()))+"]");rows.add(row); }
            if(obs.discountKinds().get(c.getApplicationId())==DiscountKind.MANUFACTURER) {row.mq=row.mq.add(c.getAppliedAmount());row.mqCount++;}
            else row.promos=row.promos.add(c.getAppliedAmount());
        }
        var tax=new Row("Sales tax");tax.regular=tx.getReceiptTotals().getTaxAmount();if(tax.regular.signum()!=0) rows.add(tax);
        for(var p:tx.getPayments()) {
            boolean reward=p.getPaymentType()==PaymentType.WALGREENS_CASH||p.getPaymentType()==PaymentType.GIFT_CARD||p.getAccountName()!=null;
            if(reward) addUse(rows,items,obs.worksheet().uses().get(p.getPaymentId()),p.getAccountName()==null?p.getPaymentType().name():p.getAccountName(),p.getAmount(),p.getPaymentType()==PaymentType.WALGREENS_CASH?"WC":"OTHER");
        }
        for(var r:obs.unidentifiedRR()) addUse(rows,items,obs.worksheet().uses().get(r.eventId()),"RR used: "+r.recordedDescription(),r.amount(),"RR");
        for(var r:tx.getRegisterRewardRedemptions()) addUse(rows,items,obs.worksheet().uses().get(r.getRedemptionId()),"RR used",r.getRedeemedAmount(),"RR");
        boolean wc=false;
        for(var e:obs.earnings()) if(SavingsReport.earned(e)) {
            if(e.kind()==EarningKind.WALGREENS_CASH) wc=true;
            Row row=e.lineItemIds().size()==1?items.get(e.lineItemIds().get(0)):null;
            if(row==null) { row=new Row(e.provider()+" earned ["+(e.scope()==Scope.RECEIPT?"whole receipt":lineNumbers(tx,e.lineItemIds()))+"]");rows.add(row); }
            var dollars=SavingsReport.earnedDollars(e);if(dollars==null) { row.valuesKnown=false;continue; }
            if(e.kind()==EarningKind.REGISTER_REWARD) row.rrEarned=row.rrEarned.add(dollars);
            else if(e.kind()==EarningKind.WALGREENS_CASH) row.wcEarned=row.wcEarned.add(dollars);
            else if(obs.worksheet().group(e)==ReceiptWorksheetData.Group.STORE_REWARD) row.otherStore=row.otherStore.add(dollars);
            switch(obs.worksheet().group(e)) {case CASH->row.cash=row.cash.add(dollars);case GIFT_CARD->row.gift=row.gift.add(dollars);case STORE_REWARD->row.store=row.store.add(dollars);case POINTS_VALUE->row.points=row.points.add(dollars);}
        }
        if(!wc && obs.walgreensCash()!=null && obs.walgreensCash().earned()!=null) { var row=new Row("Walgreens Cash printed earnings");row.store=obs.walgreensCash().earned();row.wcEarned=row.store;rows.add(row); }
        return List.copyOf(rows);
    }
    private static void addUse(List<Row> rows,Map<UUID,Row> items,ReceiptWorksheetData.Use use,String label,BigDecimal amount,String kind) {
        Row row=use!=null && use.lineIds().size()==1?items.get(use.lineIds().get(0)):null;
        if(row==null) { row=new Row(label+(use==null||use.lineIds().isEmpty()?" [whole receipt]":" [shared products]"));rows.add(row); }
        row.used=row.used.add(amount);
        switch(kind) {case "RR"->row.rrUsed=row.rrUsed.add(amount);case "WC"->row.wcUsed=row.wcUsed.add(amount);default->row.otherUsed=row.otherUsed.add(amount);}
    }
    public static Row total(List<ReceiptRecord> receipts) {
        var total=new Row("TOTAL");for(var r:SavingsReport.latestActive(receipts)) rows(r).forEach(total::add);return total;
    }
    public static void compact(ReceiptRecord receipt,PrintStream out) {
        SavingsReport.printReceiptTotals(receipt,out);
    }
    public static void itemized(ReceiptRecord receipt,PrintStream out) {
        out.println("\nITEMIZED BREAKDOWN — "+number(receipt)+" | "+receipt.transaction().getObservations().purchaseDate()+" | "+ReceiptTimeInput.display(receipt.transaction().getObservations().printedTime()));
        printColumns(rows(receipt),out);
        out.println("\nPROVIDER EARNINGS — original units and status");
        out.printf("%-20s | %-18s | %-18s | %-14s | %-12s | %s%n","Provider","Applies to lines","Original amount","USD value","Report group","Status");
        var tx=receipt.transaction();
        for(var e:tx.getObservations().earnings()) {
            var b=e.confirmed()==null?e.expected():e.confirmed();var value=e.confirmed()==null?null:SavingsReport.earnedDollars(e);
            out.printf("%-20s | %-18s | %-18s | %-14s | %-12s | %s%n",earnedName(e),e.scope()==Scope.RECEIPT?"Whole receipt":lineNumbers(tx,e.lineItemIds()),b.amount().toPlainString()+" "+benefitLabel(e),value==null?"Not valued":dollars(value),switch(tx.getObservations().worksheet().group(e)) {case CASH->"Cash";case GIFT_CARD->"Gift card";case STORE_REWARD->"Retailer reward";case POINTS_VALUE->"Point valuation";},e.status());
        }
        out.println("Shared events appear once. Point counts are shown above; only their USD value enters worksheet totals.");
    }
    private static void printColumns(List<Row> rows,PrintStream out) {
        out.println("\nPRICES AND DISCOUNTS");
        out.printf("%-38s | %4s | %10s | %10s | %10s | %4s | %9s | %9s | %9s | %9s | %11s | %11s%n","Line","Qty","Regular","Store/IVC","After IVC","MQ #","MQ total","RR used","WC used","Other used","Discounts","Before rebate");
        var total=new Row("TOTAL");
        for(var r:rows) { priceRow(r,out);total.add(r); } priceRow(total,out);
        out.println("\nREBATES AND SAVINGS");
        out.printf("%-38s | %10s | %10s | %10s | %10s | %10s | %10s | %11s | %11s | %11s | %11s | %11s%n","Line","Cash rebates","Gift cards","RR earned","WC earned","Other store","Point value","After cash","All earned","All saved","After all","Before rebate");
        for(var r:rows) rebateRow(r,out);rebateRow(total,out);
        if(!total.pricesKnown||!total.valuesKnown) out.println("Partial: a regular price or saved USD valuation is missing; recorded line amounts are used only for the known calculation.");
    }
    private static String shortLabel(String label) { return label.length()>38?label.substring(0,35)+"...":label; }
    private static void priceRow(Row r,PrintStream out) {out.printf("%-38s | %4d | %10s | %10s | %10s | %4d | %9s | %9s | %9s | %9s | %11s | %11s%n",shortLabel(r.label),r.quantity,dollars(r.regular),dollars(r.promos),dollars(r.regular.subtract(r.promos)),r.mqCount,dollars(r.mq),dollars(r.rrUsed),dollars(r.wcUsed),dollars(r.otherUsed),dollars(r.discounts()),dollars(r.beforeRebates()));}
    private static void rebateRow(Row r,PrintStream out) {out.printf("%-38s | %10s | %10s | %10s | %10s | %10s | %10s | %11s | %11s | %11s | %11s | %11s%n",shortLabel(r.label),dollars(r.cash),dollars(r.gift),dollars(r.rrEarned),dollars(r.wcEarned),dollars(r.otherStore),dollars(r.points),dollars(r.beforeRebates().subtract(r.cash)),dollars(r.earnings()),dollars(r.saved()),dollars(r.net()),dollars(r.beforeRebates()));}
    public static void period(String label,List<ReceiptRecord> records,PrintStream out) {
        var active=SavingsReport.latestActive(records);out.println("\nDETAILED BREAKDOWN TOTALS — "+label+" | "+active.size()+" receipts");
        var rows=new ArrayList<Row>();for(var r:active) {var t=total(List.of(r));var row=new Row(number(r));row.add(t);rows.add(row);} printColumns(rows,out);
        var tax=active.stream().map(r->r.transaction().getReceiptTotals().getTaxAmount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        out.println("Accumulated sales tax: "+dollars(tax));
        var cash=active.stream().map(r->paid(r.transaction(),PaymentType.CASH)).reduce(BigDecimal.ZERO,BigDecimal::add);
        var card=active.stream().flatMap(r->r.transaction().getPayments().stream()).filter(p->Set.of(PaymentType.CARD,PaymentType.CREDIT_CARD,PaymentType.DEBIT_CARD).contains(p.getPaymentType())).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        out.println("Cash paid: "+dollars(cash)+" | Card paid: "+dollars(card));
        printProviderDetails(active,out);
    }
    static String earnedName(Earning e) {
        return switch(e.kind()) {case REGISTER_REWARD->"Register Rewards";case WALGREENS_CASH->"Walgreens Cash";default->e.provider();};
    }
    static String benefitLabel(Earning e) {
        if(e.kind()==EarningKind.REGISTER_REWARD) return "Register Reward";
        if(e.kind()==EarningKind.WALGREENS_CASH) return "Walgreens Cash";
        var b=e.confirmed()==null?e.expected():e.confirmed();
        return switch(b.kind()) {case POINTS->"points";case CASH->"cash";case GIFT_CARD->"gift card";case STORE_COUPON->"store coupon / IVC";case STORE_REWARD->"store reward";};
    }
    private static void printProviderDetails(List<ReceiptRecord> receipts,PrintStream out) {
        out.println("\nEARNED BY RECEIPT AND PROVIDER");
        out.printf("%-30s | %-20s | %-20s | %14s | %14s | %s%n","Receipt","Provider / reward","Benefit","Original amount","USD value","Product lines");
        record Key(String provider,String benefit,String currency) { }
        var units=new LinkedHashMap<Key,BigDecimal>();var values=new LinkedHashMap<Key,BigDecimal>();var unknown=new HashSet<Key>();
        for(var r:receipts) {
            var tx=r.transaction(); boolean wc=false;
            for(var e:tx.getObservations().earnings()) if(SavingsReport.earned(e)) {
                if(e.kind()==EarningKind.WALGREENS_CASH) wc=true;
                var b=e.confirmed();var value=SavingsReport.earnedDollars(e);
                var key=new Key(earnedName(e),benefitLabel(e),Objects.toString(b.currency(),"points"));
                units.merge(key,b.amount(),BigDecimal::add);
                if(value==null) unknown.add(key);else values.merge(key,value,BigDecimal::add);
                out.printf("%-30s | %-20s | %-20s | %14s | %14s | %s%n",number(r),key.provider(),key.benefit(),b.amount().toPlainString(),value==null?"Not valued":dollars(value),e.scope()==Scope.RECEIPT?"Whole receipt":lineNumbers(tx,e.lineItemIds()));
            }
            var account=tx.getObservations().walgreensCash();
            if(!wc && account!=null && account.earned()!=null && account.earned().signum()!=0) {
                var key=new Key("Walgreens Cash","Walgreens Cash","USD");
                units.merge(key,account.earned(),BigDecimal::add);values.merge(key,account.earned(),BigDecimal::add);
                out.printf("%-30s | %-20s | %-20s | %14s | %14s | %s%n",number(r),key.provider(),key.benefit(),account.earned().toPlainString(),dollars(account.earned()),"Printed receipt earnings");
            }
        }
        out.println("\nPROVIDER EARNED TOTALS");
        out.printf("%-20s | %-20s | %-10s | %14s | %14s%n","Provider / reward","Benefit","Unit","Original amount","USD value");
        units.forEach((key,amount)->out.printf("%-20s | %-20s | %-10s | %14s | %14s%n",key.provider(),key.benefit(),key.currency(),amount.toPlainString(),(unknown.contains(key)?"partial ":"")+dollars(values.getOrDefault(key,BigDecimal.ZERO))));
        out.println("\nREWARDS AND BALANCES USED — BY RECEIPT");
        out.printf("%-30s | %-22s | %12s%n","Receipt","Account / reward","Amount used");
        for(var r:receipts) {
            var tx=r.transaction();var calc=new TransactionCalculator();
            var rr=calc.calculateRegisterRewardsRedeemed(tx);if(rr.signum()!=0) out.printf("%-30s | %-22s | %12s%n",number(r),"Register Rewards",dollars(rr));
            for(var payment:tx.getPayments()) if(payment.getPaymentType()==PaymentType.WALGREENS_CASH||payment.getPaymentType()==PaymentType.GIFT_CARD||payment.getAccountName()!=null)
                out.printf("%-30s | %-22s | %12s%n",number(r),payment.getPaymentType()==PaymentType.WALGREENS_CASH?"Walgreens Cash":Objects.toString(payment.getAccountName(),"Gift card"),dollars(payment.getAmount()));
        }
        out.println("Other used = gift-card or named account balances consumed. Shared rewards are counted once.");
    }

    public static LocalDate weekStart(LocalDate date) { var sunday=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)); return sunday.isBefore(date.withDayOfMonth(1))?date.withDayOfMonth(1):sunday; }
    public static LocalDate weekEnd(LocalDate date) { var saturday=date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)); var end=date.withDayOfMonth(date.lengthOfMonth()); return saturday.isAfter(end)?end:saturday; }
    public static String weekLabel(LocalDate date) { return "WEEK " + ((date.getDayOfMonth()-1+date.withDayOfMonth(1).getDayOfWeek().getValue()%7)/7+1); }
    public static List<ReceiptRecord> week(List<ReceiptRecord> records,LocalDate date) {var start=weekStart(date);return SavingsReport.latestActive(records).stream().filter(r->{var day=r.transaction().getObservations().purchaseDate();return day!=null&&!day.isBefore(start)&&!day.isAfter(weekEnd(date));}).toList();}
    private static BigDecimal paid(CouponTransaction tx,PaymentType type) {return tx.getPayments().stream().filter(p->p.getPaymentType()==type).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}

    public static String lineNumbers(CouponTransaction tx,List<UUID> ids) {if(ids.isEmpty())return "Receipt/unknown";var numbers=new ArrayList<String>();for(int i=0;i<tx.getPurchaseItems().size();i++)if(ids.contains(tx.getPurchaseItems().get(i).getLineItemId()))numbers.add(Integer.toString(i+1));return String.join(",",numbers);}
    public static String dollars(BigDecimal n) {return (n.signum()<0?"-$":"$")+n.abs().setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static String number(ReceiptRecord r) {return r.metadata()==null?r.transactionId().toString():Objects.toString(r.metadata().receiptNumber(),"Unknown");}
}
