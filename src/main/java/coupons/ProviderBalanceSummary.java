package coupons;

import coupons.ReceiptObservations.EarningKind;
import coupons.repository.*;
import java.io.PrintStream;
import java.math.*;
import java.time.*;
import java.util.*;

/** Balances derived from confirmed openings and latest receipt revisions, with explicit external movements. */
public final class ProviderBalanceSummary {
    private static final List<String> PROGRAMS = List.of("Walgreens Cash", "Register Rewards", "ECB", "Walmart Cash",
            "Checkout 51", "Ibotta", "Venmo", "Fetch", "P&G Brand", "Swagbucks", "MyPoints", "Coupons DotCom",
            "PayPal", "Gift Cards", "Returns", "Target", "Target Gift Card", "Target Circle", "Merryfield", "Makeena", "Shopmium", "Rakuten");
    private static final Currency USD = Currency.getInstance("USD");
    private final InMemoryTransactionRepository repository;
    private AccountBalanceState local = AccountBalanceState.empty();
    public ProviderBalanceSummary() { repository = null; }
    public ProviderBalanceSummary(InMemoryTransactionRepository repository) { this.repository = Objects.requireNonNull(repository); }
    private AccountBalanceState state() { return repository == null ? local : repository.accountBalances(); }
    public void recordOpening(LocalDate date, String provider, BigDecimal value, boolean monthStart) {
        Objects.requireNonNull(value);
        LocalDate effectiveDate = monthStart ? date.withDayOfMonth(1) : date;
        var prior = state().openings().stream().filter(o -> o.provider().equals(canonical(provider))
                && o.date().equals(effectiveDate) && o.monthStart() == monthStart).reduce((a,b) -> b);
        if (prior.isPresent() && prior.orElseThrow().originalUnits() != null && prior.orElseThrow().usdValue() != null
                && prior.orElseThrow().usdValue().compareTo(value) == 0) return; // retain separately entered original points
        recordOpening(date, provider, null, value, monthStart, "Recorded USD observation", "User-entered opening");
    }
    public void recordOpening(LocalDate date, String provider, BigDecimal points, BigDecimal value,
            boolean monthStart, String basis, String source) {
        var opening = new AccountOpening(UUID.randomUUID(), monthStart ? date.withDayOfMonth(1) : date,
                canonical(provider), points, value, monthStart, basis, source, Instant.now());
        if (repository != null) repository.recordAccountOpening(opening);
        else { var values = new ArrayList<>(local.openings()); values.add(opening); local = new AccountBalanceState(values, local.adjustments(), local.redemptionHistory()); }
    }
    public List<AccountOpening> openingHistory() { return state().openings(); }
    public void recordAdjustment(LocalDate date, String provider, BigDecimal points, BigDecimal usd, String reason, String actor) {
        var event = new AccountAdjustment(UUID.randomUUID(), date, canonical(provider), points, usd, reason, actor, Instant.now());
        if (repository != null) repository.recordAccountAdjustment(event);
        else { var values = new ArrayList<>(local.adjustments()); values.add(event); local = new AccountBalanceState(local.openings(), values, local.redemptionHistory()); }
    }
    public List<String> programs(List<ReceiptRecord> records) {
        var names = new LinkedHashSet<>(PROGRAMS);
        state().openings().forEach(o -> names.add(o.provider()));
        state().adjustments().forEach(a -> names.add(a.provider()));
        records.forEach(r -> r.transaction().getObservations().earnings().forEach(e -> names.add(program(e.kind(), e.provider()))));
        for(var r:redemptions()) { names.add(canonical(r.provider())); if(r.status()==ProviderRedemption.Status.RECEIVED && r.destination()!=ProviderRedemption.Destination.PURCHASE) names.add(canonical(r.destinationAccount())); }
        return List.copyOf(names);
    }
    /** Retains source receipt/event IDs for review; negative values are redemptions/adjustments. */
    public record Movement(LocalDate date, String provider, BigDecimal points, BigDecimal usd, String source, LocalTime time) {
        public Movement(LocalDate date,String provider,BigDecimal points,BigDecimal usd,String source) { this(date,provider,points,usd,source,null); }
    }
    public List<Movement> movements(List<ReceiptRecord> records) {
        Map<UUID, ReceiptRecord> latest = new LinkedHashMap<>();
        records.forEach(r -> latest.merge(r.transactionId(), r, (a,b) -> a.revision() >= b.revision() ? a : b));
        var result = new ArrayList<Movement>();
        for (var r : latest.values()) {
            if (r.status() != ReceiptStatus.ACTIVE) continue;
            var tx = r.transaction(); var date = tx.getObservations().purchaseDate();
            String source = "Receipt " + (r.metadata() == null ? r.transactionId() : r.metadata().receiptNumber())
                    + " | revision " + r.revision() + " | " + r.transactionId();
            for (var p : tx.getPayments()) {
                String provider = p.getPaymentType() == PaymentType.WALGREENS_CASH ? "Walgreens Cash"
                        : p.getPaymentType() == PaymentType.GIFT_CARD ? "Gift Cards" : null;
                if (p.getAccountName() != null) provider = canonical(p.getAccountName());
                if (provider != null) {
                    var use=tx.getObservations().worksheet().uses().get(p.getPaymentId());
                    result.add(new Movement(date,provider,use==null || use.originalUnits()==null?null:use.originalUnits().negate(),p.getAmount().negate(),source+" | payment "+p.getPaymentId()));
                }
            }
            for (var rr : tx.getRegisterRewardRedemptions()) result.add(new Movement(date, "Register Rewards", null,
                    rr.getRedeemedAmount().negate(), source + " | redemption " + rr.getRedemptionId()));
            for (var rr : tx.getObservations().unidentifiedRR()) result.add(new Movement(date, "Register Rewards", null,
                    rr.amount().negate(), source + " | RR used " + rr.eventId()));
            for (var e : tx.getObservations().earnings()) {
                if (e.confirmed() == null || e.status() != RebateStatus.CREDITED && e.status() != RebateStatus.PAID) continue;
                var benefit = e.confirmed(); boolean points = benefit.kind() == BenefitKind.POINTS;
                BigDecimal usd = points ? e.valuation() == null || !USD.equals(e.valuation().currency()) ? null : e.valuation().amount()
                        : USD.equals(benefit.currency()) ? benefit.amount() : null;
                result.add(new Movement(date, program(e.kind(), e.provider()), points ? benefit.amount() : null, usd,
                        source + " | earning " + e.earningId()));
            }
        }
        state().adjustments().forEach(a -> result.add(new Movement(a.date(), a.provider(), a.unitChange(), a.usdChange(),
                "Adjustment " + a.eventId() + " | " + a.reason() + " | " + a.recordedBy())));
        for(var r:redemptions()) {
            result.add(new Movement(r.requestedDate(),canonical(r.provider()),r.originalUnitsUsed()==null?null:r.originalUnitsUsed().negate(),r.sourceUsdValue().negate(),"Redemption "+r.redemptionId()+" | reserved",r.requestedTime()));
            if(r.status()==ProviderRedemption.Status.CANCELLED)
                result.add(new Movement(r.completedDate(),canonical(r.provider()),r.originalUnitsUsed(),r.sourceUsdValue(),"Redemption "+r.redemptionId()+" | cancellation",r.completedTime()));
            else if(r.status()==ProviderRedemption.Status.RECEIVED && r.destination()!=ProviderRedemption.Destination.PURCHASE)
                result.add(new Movement(r.completedDate(),canonical(r.destinationAccount()),null,r.destinationUsdValue(),"Redemption "+r.redemptionId()+" | received",r.completedTime()));
        }
        return List.copyOf(result);
    }
    private List<ProviderRedemption> redemptions() {
        var latest=new LinkedHashMap<UUID,ProviderRedemption>(); state().redemptionHistory().forEach(r->latest.put(r.redemptionId(),r)); return List.copyOf(latest.values());
    }
    public static String accountName(String name) { return canonical(name); }
    /** Accounts potentially affected by a receipt, including removed/reclassified earnings in an earlier revision. */
    public Set<String> receiptProviders(ReceiptRecord receipt) {
        var providers = new LinkedHashSet<String>();
        var tx = receipt.transaction();
        for (var payment : tx.getPayments()) {
            if (payment.getAccountName() != null) providers.add(canonical(payment.getAccountName()));
            else if (payment.getPaymentType() == PaymentType.WALGREENS_CASH) providers.add("Walgreens Cash");
            else if (payment.getPaymentType() == PaymentType.GIFT_CARD) providers.add("Gift Cards");
        }
        if (!tx.getRegisterRewardRedemptions().isEmpty() || !tx.getObservations().unidentifiedRR().isEmpty()) providers.add("Register Rewards");
        tx.getObservations().earnings().forEach(e -> providers.add(program(e.kind(), e.provider())));
        return Set.copyOf(providers);
    }
    public void printAfterWrite(ReceiptRecord before, ReceiptRecord after, List<ReceiptRecord> records, PrintStream out) {
        var providers = new TreeSet<>(receiptProviders(after));
        if (before != null) providers.addAll(receiptProviders(before));
        out.println("ACCOUNT BALANCES AFTER SAVE — current committed receipts; no activity added by this display");
        if (providers.isEmpty()) { out.println("No reward-account activity on this receipt."); return; }
        var dates = new TreeSet<LocalDate>();
        if (after.transaction().getObservations().purchaseDate() != null) dates.add(after.transaction().getObservations().purchaseDate());
        if (before != null && before.transaction().getObservations().purchaseDate() != null) dates.add(before.transaction().getObservations().purchaseDate());
        if (dates.isEmpty()) { out.println("Receipt date unknown; carry-forward cannot be positioned. Enter the date with menu 3."); return; }
        for (var day : dates) {
            out.println("Activity day " + day + " | next day " + day.plusDays(1));
            out.printf("%-22s %15s %15s %19s%n", "Account", "Day start", "Day close", "Next-day opening");
            for (String provider : providers) {
                var current = balance(day, provider, records); var next = balance(day.plusDays(1), provider, records);
                out.printf("%-22s %15s %15s %19s%n", provider, value(current.startUsd()), value(current.endUsd()), value(next.startUsd()));
                if (current.startPoints() != null || current.endPoints() != null || next.startPoints() != null)
                    out.println("  Points: start " + units(current.startPoints()) + " | close " + units(current.endPoints()) + " | next opening " + units(next.startPoints()));
                if (current.opening() == null || current.startUsd() == null)
                    out.println("  " + provider + ": enter a confirmed opening/valuation in menu 6; unknown is not zero.");
                if (next.opening() != null && next.opening().date().equals(day.plusDays(1)))
                    out.println("  Next-day opening uses its separately confirmed observation; reconcile it against this day close.");
            }
        }
        out.println("Carry-forward covers saved activity only. Pending rewards/online rebate progress are excluded; points and USD valuations remain separate.");
    }

    private AccountOpening baseline(LocalDate day, String provider) {
        AccountOpening best = null;
        for (var opening : state().openings()) {
            if (!opening.provider().equals(provider) || opening.date().isAfter(day)) continue;
            if (best == null || opening.date().isAfter(best.date())
                    || opening.date().equals(best.date()) && (!opening.monthStart() || best.monthStart())) best = opening;
        }
        return best;
    }
    private BigDecimal at(LocalDate day, String provider, boolean end, boolean points, List<Movement> movements) {
        return atFrom(day, end, points, movements, baseline(day, provider));
    }
    private BigDecimal atFrom(LocalDate day, boolean end, boolean points, List<Movement> movements, AccountOpening opening) {
        if (opening == null) return null;
        BigDecimal value = points ? opening.originalUnits() : opening.usdValue();
        if (value == null) return null;
        for (var m : movements) {
            if (!m.provider().equals(opening.provider())) continue;
            // Undated movements cannot be safely positioned relative to an opening.
            if (m.date() == null) return null;
            if (m.date().isBefore(opening.date()) || m.date().isAfter(day) || !end && m.date().equals(day)) continue;
            BigDecimal delta = points ? m.points() : m.usd();
            if (points && m.points() == null) continue;
            if (delta == null) return null;
            value = value.add(delta);
        }
        return value;
    }
    public void printTransaction(ReceiptRecord target,List<ReceiptRecord> records,PrintStream out) {
        var day=target.transaction().getObservations().purchaseDate();
        if(day==null) { out.println("Enter the receipt date before viewing transaction balances."); return; }
        var active=SavingsReport.latestActive(records); var sameDay=new ArrayList<>(SavingsReport.day(active,day));
        if(sameDay.stream().noneMatch(r->r.transactionId().equals(target.transactionId()))) sameDay.add(target);
        boolean explicit=sameDay.stream().allMatch(r->r.transaction().getObservations().worksheet().dayOrder()!=null)
                && sameDay.stream().map(r->r.transaction().getObservations().worksheet().dayOrder()).distinct().count()==sameDay.size();
        boolean timed=sameDay.stream().allMatch(r->ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).isPresent())
                && sameDay.stream().map(r->ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElseThrow()).distinct().count()==sameDay.size();
        if(sameDay.size()>1 && !explicit && !timed) { out.println("Receipt order is ambiguous. Use Update > ORDER to assign a distinct day order to every receipt on "+day+"."); return; }
        if(explicit) sameDay.sort(Comparator.comparing(r->r.transaction().getObservations().worksheet().dayOrder()));
        else if(timed) sameDay.sort(Comparator.comparing(r->ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElseThrow()));
        int index=0;while(!sameDay.get(index).transactionId().equals(target.transactionId())) index++;
        var prior=new ArrayList<>(active.stream().filter(r->{var d=r.transaction().getObservations().purchaseDate();return d!=null && d.isBefore(day);}).toList()); prior.addAll(sameDay.subList(0,index));
        var after=new ArrayList<>(prior);after.add(target);
        var time=ReceiptTimeInput.parse(target.transaction().getObservations().printedTime()).orElse(null);
        var providers=new TreeSet<>(receiptProviders(target));
        var beforeEvents=movements(prior);var afterEvents=movements(after);
        if(afterEvents.stream().anyMatch(m->m.date()==null && providers.contains(m.provider()))) { out.println("Undated account activity must be dated before showing transaction balances."); return; }
        if(afterEvents.stream().anyMatch(m->day.equals(m.date()) && m.source().startsWith("Redemption ") && (time==null || m.time()==null))) {
            out.println("Same-day redemption order is unknown. Record receipt and redemption times before viewing transaction balances."); return;
        }
        java.util.function.Predicate<Movement> cutoff=m->!day.equals(m.date()) || !m.source().startsWith("Redemption ") || !m.time().isAfter(time);
        beforeEvents=beforeEvents.stream().filter(cutoff).toList();afterEvents=afterEvents.stream().filter(cutoff).toList();
        out.println("\nACCOUNTS — receipt activity in purchase order");
        out.printf("%-22s | %14s | %14s | %14s | %14s%n","Provider","Before","Earned","Used","After");
        var currentEvents=movements(List.of(target)).stream().filter(m->m.source().startsWith("Receipt ")).toList();
        for(var name:providers) {
            var earned=currentEvents.stream().filter(m->m.provider().equals(name)&&m.usd()!=null&&m.usd().signum()>0).map(Movement::usd).reduce(BigDecimal.ZERO,BigDecimal::add);
            var used=currentEvents.stream().filter(m->m.provider().equals(name)&&m.usd()!=null&&m.usd().signum()<0).map(Movement::usd).reduce(BigDecimal.ZERO,BigDecimal::add).negate();
            out.printf("%-22s | %14s | %14s | %14s | %14s%n",name,value(atFrom(day,true,false,beforeEvents,baseline(day,name))),value(earned),value(used),value(atFrom(day,true,false,afterEvents,baseline(day,name))));
            var start=atFrom(day,true,true,beforeEvents,baseline(day,name));var end=atFrom(day,true,true,afterEvents,baseline(day,name));
            if(start!=null||end!=null) out.println("  Original points: "+units(start)+" -> "+units(end));
        }
    }

    /** Difference between the confirmed opening and the carry from an earlier dated observation. */
    public BigDecimal openingDifference(LocalDate day, String name, List<ReceiptRecord> records) {
        var chosen = baseline(day, canonical(name));
        if (chosen == null || chosen.usdValue() == null) return null;
        AccountOpening prior = null;
        for (var candidate : state().openings()) {
            if (!candidate.provider().equals(chosen.provider()) || candidate.observationId().equals(chosen.observationId())) continue;
            boolean earlier = candidate.date().isBefore(chosen.date()) || candidate.date().equals(chosen.date()) && candidate.monthStart() && !chosen.monthStart();
            if (earlier && (prior == null || !candidate.date().isBefore(prior.date()))) prior = candidate;
        }
        var calculated = atFrom(chosen.date(), false, false, movements(records), prior);
        return calculated == null ? null : chosen.usdValue().subtract(calculated);
    }
    public record Balance(BigDecimal monthUsd, BigDecimal startUsd, BigDecimal endUsd,
            BigDecimal startPoints, BigDecimal endPoints, AccountOpening opening) { }
    public Balance balance(LocalDate day, String name, List<ReceiptRecord> records) {
        String provider = canonical(name); var events = movements(records);
        // An explicit opening replaces carry; otherwise the preceding close is this month's start.
        BigDecimal month = at(day.withDayOfMonth(1), provider, false, false, events);
        return new Balance(month, at(day, provider, false, false, events), at(day, provider, true, false, events),
                at(day, provider, false, true, events), at(day, provider, true, true, events), baseline(day, provider));
    }
    public void print(LocalDate day, List<ReceiptRecord> records, PrintStream out) {
        out.println("\nACCOUNT BALANCES — " + day);
        if (day == null) return;
        out.printf("%-20s | %14s | %14s | %14s%n", "Provider", "Month start", "Day start", "Day close");
        out.println("---------------------+----------------+----------------+----------------");
        BigDecimal month = BigDecimal.ZERO, start = BigDecimal.ZERO, end = BigDecimal.ZERO;
        boolean mc = true, sc = true, ec = true;
        boolean targetSplit = state().openings().stream().anyMatch(o -> !o.date().isAfter(day)
                && (o.provider().equals("Target Gift Card") || o.provider().equals("Target Circle")))
                || movements(records).stream().anyMatch(m -> m.date() != null && !m.date().isAfter(day)
                && (m.provider().equals("Target Gift Card") || m.provider().equals("Target Circle")));
        for (String name : programs(records)) {
            if (name.equals("Target") && targetSplit) continue; // combined historical opening is not a third account
            var b = balance(day, name, records);
            if (!nonzero(b.monthUsd()) && !nonzero(b.startUsd()) && !nonzero(b.endUsd())
                    && !nonzero(b.startPoints()) && !nonzero(b.endPoints())) continue;
            out.printf("%-20s | %14s | %14s | %14s%n", name, compactValue(b.monthUsd()), compactValue(b.startUsd()), compactValue(b.endUsd()));
            if (b.monthUsd() == null) mc = false; else month = month.add(b.monthUsd());
            if (b.startUsd() == null) sc = false; else start = start.add(b.startUsd());
            if (b.endUsd() == null) ec = false; else end = end.add(b.endUsd());
            if (b.startPoints() != null || b.endPoints() != null)
                out.printf("  Points             | %14s | %14s | %14s%n", units(at(day.withDayOfMonth(1), canonical(name), false, true, movements(records))), units(b.startPoints()), units(b.endPoints()));
        }
        out.println("---------------------+----------------+----------------+----------------");
        out.printf("%-20s | %14s | %14s | %14s%n", "USD value total", total(month,mc), total(start,sc), total(end,ec));
        if (!mc || !sc || !ec) {
            out.println("Missing data = opening, activity date or USD valuation is absent; the total is partial.");
            for (var movement : movements(records)) if (movement.usd() == null && (movement.date() == null || !movement.date().isAfter(day)))
                out.println("  Missing USD valuation: " + movement.provider() + " | " + movement.points() + " points | " + movement.source());
        }
    }
    /** Automatically show account movements for the selected day's sales week and month. */
    public void printPeriods(LocalDate day,List<ReceiptRecord> records,PrintStream out) {
        printPeriod(ReceiptWorksheetReport.weekLabel(day),ReceiptWorksheetReport.weekStart(day),ReceiptWorksheetReport.weekEnd(day),records,out);
        printPeriod("MONTH TO DATE",day.withDayOfMonth(1),day,records,out);
    }
    private void printPeriod(String label,LocalDate first,LocalDate last,List<ReceiptRecord> records,PrintStream out) {
        var all=movements(records);
        var period=all.stream().filter(m->m.date()!=null && !m.date().isBefore(first) && !m.date().isAfter(last)).toList();
        boolean split=state().openings().stream().anyMatch(o->!o.date().isAfter(last) && (o.provider().equals("Target Gift Card")||o.provider().equals("Target Circle")))
                || all.stream().anyMatch(m->m.date()!=null && !m.date().isAfter(last) && (m.provider().equals("Target Gift Card")||m.provider().equals("Target Circle")));
        out.println("\n"+label+" ACCOUNT BALANCES — "+first+" through "+last);
        out.printf("%-20s | %14s | %14s | %14s | %14s%n","Provider","Start","Added","Used / out","Close");
        out.println("---------------------+----------------+----------------+----------------+----------------");
        BigDecimal totalStart=BigDecimal.ZERO,totalAdded=BigDecimal.ZERO,totalUsed=BigDecimal.ZERO,totalEnd=BigDecimal.ZERO;
        boolean startKnown=true,activityKnown=true,endKnown=true;
        for(String provider:programs(records)) {
            if(provider.equals("Target") && split)continue;
            var activity=period.stream().filter(m->m.provider().equals(provider)).toList();
            BigDecimal start=at(first,provider,false,false,all),end=at(last,provider,true,false,all);
            BigDecimal startPoints=at(first,provider,false,true,all),endPoints=at(last,provider,true,true,all);
            BigDecimal added=activity.stream().filter(m->m.usd()!=null && m.usd().signum()>0).map(Movement::usd).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal used=activity.stream().filter(m->m.usd()!=null && m.usd().signum()<0).map(Movement::usd).reduce(BigDecimal.ZERO,BigDecimal::add).negate();
            boolean complete=activity.stream().allMatch(m->m.usd()!=null);
            if(!nonzero(start)&&!nonzero(end)&&!nonzero(startPoints)&&!nonzero(endPoints)&&activity.isEmpty())continue;
            out.printf("%-20s | %14s | %14s | %14s | %14s%n",provider,compactValue(start),total(added,complete),total(used,complete),compactValue(end));
            if(start==null)startKnown=false;else totalStart=totalStart.add(start);
            if(end==null)endKnown=false;else totalEnd=totalEnd.add(end);
            if(!complete)activityKnown=false;
            totalAdded=totalAdded.add(added);totalUsed=totalUsed.add(used);
            if(startPoints!=null || endPoints!=null || activity.stream().anyMatch(m->m.points()!=null)) {
                BigDecimal pointIn=activity.stream().map(Movement::points).filter(Objects::nonNull).filter(n->n.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);
                BigDecimal pointOut=activity.stream().map(Movement::points).filter(Objects::nonNull).filter(n->n.signum()<0).reduce(BigDecimal.ZERO,BigDecimal::add).negate();
                out.printf("%-20s | %14s | %14s | %14s | %14s%n","  Original points",units(startPoints),units(pointIn),units(pointOut),units(endPoints));
            }
        }
        out.println("---------------------+----------------+----------------+----------------+----------------");
        out.printf("%-20s | %14s | %14s | %14s | %14s%n","USD value total",total(totalStart,startKnown),total(totalAdded,activityKnown),total(totalUsed,activityKnown),total(totalEnd,endKnown));
        out.println("Added and used/out include recorded external movements. Confirmed openings may reset carry-forward.");
    }

    private static boolean nonzero(BigDecimal amount) { return amount != null && amount.signum() != 0; }
    private static String compactValue(BigDecimal amount) { return amount == null ? "Missing data" : value(amount); }
    private static String program(EarningKind kind, String provider) {
        return kind == EarningKind.REGISTER_REWARD ? "Register Rewards" : kind == EarningKind.WALGREENS_CASH ? "Walgreens Cash" : canonical(provider);
    }
    private static String units(BigDecimal value) { return value == null ? "not entered" : value.toPlainString(); }
    private static String value(BigDecimal value) { return value == null ? "not entered" : "$" + value.setScale(2,RoundingMode.HALF_UP).toPlainString(); }
    private static String total(BigDecimal value, boolean complete) { return (complete ? "" : "partial ") + value(value); }
    private static String canonical(String name) {
        String compact = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (compact) {
            case "wc", "walgreenscash" -> "Walgreens Cash";
            case "rr", "registerreward", "registerrewards" -> "Register Rewards";
            case "swag", "swagbuck", "swagbucks" -> "Swagbucks";
            case "mypoint", "mypoints" -> "MyPoints";
            case "pgbrand", "pg", "pggoodeveryday" -> "P&G Brand";
            case "couponcom", "couponscom", "couponsdotcom" -> "Coupons DotCom";
            case "mckenna", "makenna", "makeena" -> "Makeena";
            case "shop", "shopmuim", "shopmium" -> "Shopmium";
            case "giftcard", "giftcards" -> "Gift Cards";
            case "ecb", "cvsextrabucks" -> "ECB";
            default -> PROGRAMS.stream().filter(p -> p.equalsIgnoreCase(name.trim())).findFirst().orElse(name);
        };
    }

}
