package coupons.repository;

import coupons.*;
import java.time.*;
import java.util.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import coupons.AdvertisedDeal;
import coupons.ProviderBalanceSummary.AccountActivityView;
import coupons.ClippedCoupon;
import coupons.PlannedPurchase;
import coupons.PromotionQualification;
import coupons.ProviderBalanceSummary;
import coupons.PurchaseItem;
import coupons.ReceiptTimeInput;
import coupons.ReceiptWorksheetReport;
import coupons.SavingsReport;
import coupons.ShoppingEstimate;
import coupons.SourceArtifact;
import coupons.TransactionCalculator;
import static coupons.repository.PostgresRepositorySupport.transaction;

/**
 * Optional web read model and narrow actions, reusing domain repositories and
 * calculations.
 */
public final class PostgresPlatformRepository implements PlatformRepository {

    private final PostgresConnectionFactory db;

    public PostgresPlatformRepository(PostgresConnectionFactory db) {
        this.db = Objects.requireNonNull(db);
    }

    public void correctStandaloneAmount(UUID id,BigDecimal expected,BigDecimal amount){new PostgresReceiptSupplementRepository(db).correctStandaloneAmount(id,expected,amount);}
    public Map<String,Object> memberReward(UUID id) {return PostgresRepositorySupport.transaction(db,c->memberReward(c,id));}
    private static Map<String,Object> memberReward(Connection c,UUID id)throws SQLException {
        AccountAdjustment old;try(var p=c.prepareStatement("SELECT payload FROM provider_account_adjustments WHERE event_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();old=PostgresRepositorySupport.deserialize(r.getBytes(1),AccountAdjustment.class);}}
        if(!old.reason().startsWith("Member reward:"))throw new IllegalArgumentException("Not a member reward.");
        BigDecimal usd=old.usdChange(),points=old.unitChange();
        try(var p=c.prepareStatement("SELECT usd_change,unit_change FROM provider_account_adjustments WHERE reason=?")){p.setString(1,"Member reward correction: "+id);try(var r=p.executeQuery()){while(r.next()){if(r.getBigDecimal(1)!=null)usd=usd==null?r.getBigDecimal(1):usd.add(r.getBigDecimal(1));if(r.getBigDecimal(2)!=null)points=points==null?r.getBigDecimal(2):points.add(r.getBigDecimal(2));}}}
        return fields("amount",usd,"points",points,"provider",old.provider(),"date",old.date(),"time",old.time());
    }
    public BigDecimal memberAmount(UUID id) {return (BigDecimal)memberReward(id).get("amount");}
    public void correctMemberAmount(UUID id,UUID correction,BigDecimal expected,BigDecimal amount,String actor) {correctMemberAmount(id,correction,expected,amount,null,null,actor);}
    public void correctMemberAmount(UUID id,UUID correction,BigDecimal expected,BigDecimal amount,BigDecimal expectedPoints,BigDecimal points,String actor) {
        if(amount==null||amount.signum()<0||points!=null&&points.signum()<0)throw new IllegalArgumentException("Enter nonnegative reward amounts.");
        PostgresRepositorySupport.transaction(db,c->{
            try(var p=c.prepareStatement("SELECT payload FROM provider_account_adjustments WHERE event_id=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){
                if(!r.next())throw new NoSuchElementException();var old=PostgresRepositorySupport.deserialize(r.getBytes(1),AccountAdjustment.class);var current=memberReward(c,id);
                BigDecimal actual=(BigDecimal)current.get("amount"),actualPoints=(BigDecimal)current.get("points");
                if(actual.compareTo(expected)!=0||actualPoints!=null&&(expectedPoints==null||actualPoints.compareTo(expectedPoints)!=0))throw new ConcurrentModificationException("Reward changed; reopen its editor.");
                if(actualPoints!=null&&points==null||actualPoints==null&&points!=null)throw new IllegalArgumentException("Preserve the reward's original points unit.");
                BigDecimal units=points==null?null:points.subtract(actualPoints);
                var value=new AccountAdjustment(correction,old.date(),old.provider(),units,amount.subtract(actual),"Member reward correction: "+id,actor,java.time.Instant.now(),old.expirationDate());
                try(var q=c.prepareStatement("INSERT INTO provider_account_adjustments(event_id,provider,event_date,unit_change,usd_change,reason,recorded_by,recorded_at,payload) VALUES(?,?,?,?,?,?,?,?,?)")){q.setObject(1,correction);q.setString(2,old.provider());q.setObject(3,old.date());q.setBigDecimal(4,units);q.setBigDecimal(5,value.usdChange());q.setString(6,value.reason());q.setString(7,actor);q.setTimestamp(8,java.sql.Timestamp.from(value.recordedAt()));q.setBytes(9,PostgresRepositorySupport.serialize(value));q.executeUpdate();}
            }}return null;});
    }
    public void deleteStandaloneReward(UUID id,String actor){new PostgresReceiptSupplementRepository(db).voidRecordedReward(id,"Owner deleted reward",actor);}
    public boolean earningMatched(UUID earning) {return PostgresRepositorySupport.transaction(db,c->PostgresReceiptState.read(c).rrUsageLinks().values().stream().anyMatch(link->link.sourceId().equals(earning)));}
    public void addStandaloneReward(coupons.RegisterReward reward) {new PostgresReceiptSupplementRepository(db).addStandaloneReward(reward);}
    public List<Map<String,Object>> standaloneRewards() {
        return transaction(db,c->{c.setReadOnly(true);var state=PostgresReceiptState.read(c);
            return state.rewards().values().stream().filter(r->r.getEarnedReceiptId()==null && !state.rewardVoids().containsKey(r.getRewardId())).sorted(Comparator.comparing(coupons.RegisterReward::getSourceDescription).thenComparing(coupons.RegisterReward::getRewardId)).map(r->fields("rewardId",r.getRewardId(),"description",r.getSourceDescription(),"amount",r.getFaceValue(),"earnedDate",r.getEarnedDate(),"expirationDate",r.getExpirationDate(),"status",state.rrUsageLinks().containsKey(r.getRewardId())?"MATCHED":state.redemptions().values().stream().anyMatch(use->use.getRewardId().equals(r.getRewardId()))?"REDEEMED":"UNUSED")).toList();});
    }
    public RRUsageLink addStandaloneMatch(UUID receipt,long revision,UUID use,UUID reward,String description,LocalDate earned,LocalDate expires,String reason,String actor) {
        return new PostgresReceiptSupplementRepository(db).addStandaloneMatch(receipt,revision,use,reward,description,earned,expires,reason,actor);
    }
    public List<ReceiptRRMatching.Use> rrOptions(UUID id) { return transaction(db,c->{c.setReadOnly(true);return ReceiptRRMatching.options(PostgresReceiptState.read(c),id);}); }
    public RRUsageLink matchRR(UUID id,long revision,UUID use,UUID source,boolean receiptEarning,String reason,String actor,boolean confirmUnknownHistory) {
        return new PostgresReceiptSupplementRepository(db).matchRR(id,revision,use,source,receiptEarning,reason,actor,confirmUnknownHistory);
    }
    public void savePayout(ProviderRedemption payout){new PostgresProviderAccountRepository(db).saveRedemption(payout);}
    public Map<String,Object> payout(UUID id){return transaction(db,c->{try(var p=c.prepareStatement("SELECT revision,payload FROM provider_redemptions_current WHERE redemption_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();return fields("revision",r.getLong(1),"payout",PostgresRepositorySupport.deserialize(r.getBytes(2),ProviderRedemption.class));}}});}
    public void finishPayout(UUID id,long revision,ProviderRedemption.Status status,LocalDate date){
        var repo=new PostgresProviderAccountRepository(db);var old=repo.findRedemption(id).orElseThrow();
        if(old.status()!=ProviderRedemption.Status.REQUESTED||status==ProviderRedemption.Status.REQUESTED)throw new IllegalArgumentException("Only a requested payout can be completed or cancelled.");
        repo.updateRedemption(id,revision,old.complete(status,date,null));
    }
    public void saveMemberReward(AccountAdjustment reward) { new PostgresProviderAccountRepository(db).saveAdjustment(reward); }
    public void saveOpening(AccountOpening opening) { new PostgresProviderAccountRepository(db).saveOpening(opening); }

    public List<AccountActivityView.RewardLink> rewardUses(UUID id) {
        return transaction(db,c -> {
            c.setReadOnly(true);
            var state=PostgresReceiptState.read(c);
            var records=state.receipts().values().stream().map(r->new ReceiptRecord(r.transactionId(),r.status(),r.revision(),r.contents().toTransaction(),r.metadata(),r.evidenceIds())).toList();
            return AccountActivityView.project(new ProviderBalanceSummary(PostgresReceiptState.rules(state)),records,state.rrUsageLinks().values(),state.rewards()).stream().filter(a->id.equals(a.movement().receiptId()) && a.movement().usd()!=null && a.movement().usd().signum()<0).flatMap(a->a.rewards().stream()).distinct().toList();
        });
    }

    public Map<String,Object> account(String provider,LocalDate from,LocalDate to) {
        if(to.isBefore(from)) throw new IllegalArgumentException("Invalid date range.");
        return transaction(db,c -> {
            c.setReadOnly(true);
            var state=PostgresReceiptState.read(c);
            var records=state.receipts().values().stream().map(r->new ReceiptRecord(r.transactionId(),r.status(),r.revision(),r.contents().toTransaction(),r.metadata(),r.evidenceIds())).toList();
            var summary=new ProviderBalanceSummary(PostgresReceiptState.rules(state));
            String name=ProviderBalanceSummary.accountName(provider);
            var activity=AccountActivityView.project(summary,records,state.rrUsageLinks().values(),state.rewards()).stream().filter(a->a.movement().provider().equals(name) && (a.movement().date()==null || !a.movement().date().isBefore(from) && !a.movement().date().isAfter(to))).toList();
            var start=summary.balance(from,name,records); var end=summary.balance(to,name,records);
            BigDecimal earned=BigDecimal.ZERO,spent=BigDecimal.ZERO,pe=BigDecimal.ZERO,ps=BigDecimal.ZERO;
            boolean complete=true;
            for(var a:activity) { var m=a.movement(); if(m.date()==null)complete=false; if(m.usd()==null)complete=false; else if(m.usd().signum()<0)spent=spent.subtract(m.usd());else earned=earned.add(m.usd());
                if(m.points()!=null) {if(m.points().signum()<0)ps=ps.subtract(m.points());else pe=pe.add(m.points());} }
            var conversions=new LinkedHashMap<UUID,ProviderRedemption>();
            state.balances().redemptionHistory().forEach(r->conversions.put(r.redemptionId(),r));
            return fields("conversions",conversions.values().stream().filter(r->ProviderBalanceSummary.accountName(r.provider()).equals(name) && !r.requestedDate().isBefore(from) && !r.requestedDate().isAfter(to)).toList(),"provider",name,"from",from,"to",to,"beginning",start,"ending",end,"earned",earned,"spent",spent,"earnedPoints",pe,"spentPoints",ps,"usdActivityComplete",complete,"activity",activity);
        });
    }

    public List<Object> receiptDrafts(String actor) {
        return transaction(db,c->{var rows=new ArrayList<Object>();try(var p=c.prepareStatement("SELECT draft_id,revision,store,receipt_number,updated_at FROM receipt_drafts WHERE owner_id=? ORDER BY updated_at DESC")){p.setString(1,actor);try(var r=p.executeQuery()){while(r.next())rows.add(fields("draftId",r.getObject(1),"revision",r.getLong(2),"store",r.getString(3),"receiptNumber",r.getString(4),"updatedAt",r.getTimestamp(5).toInstant()));}}return rows;});
    }
    public Map<String,Object> receiptDraft(UUID id,String actor) {
        return transaction(db,c->{try(var p=c.prepareStatement("SELECT revision,payload,updated_at FROM receipt_drafts WHERE draft_id=? AND owner_id=?")){p.setObject(1,id);p.setString(2,actor);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();return fields("draftId",id,"revision",r.getLong(1),"payload",r.getString(2),"updatedAt",r.getTimestamp(3).toInstant());}}});
    }
    public Map<String,Object> saveReceiptDraft(UUID id,long expected,String payload,String store,String number,String actor) {
        return transaction(db,c->{
            try(var p=c.prepareStatement("SELECT 1 FROM receipt_current WHERE transaction_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("This receipt is already saved.");}}
            long revision=Math.addExact(expected,1);var now=Instant.now();
            if(expected==0){try(var p=c.prepareStatement("INSERT INTO receipt_drafts VALUES(?,1,?,?,?,?,?) ON CONFLICT DO NOTHING")){p.setObject(1,id);p.setString(2,actor);p.setString(3,store);p.setString(4,number);p.setTimestamp(5,java.sql.Timestamp.from(now));p.setString(6,payload);if(p.executeUpdate()!=1)throw new ConcurrentModificationException("Draft exists; reopen the latest saved draft.");}}
            else try(var p=c.prepareStatement("UPDATE receipt_drafts SET revision=?,store=?,receipt_number=?,updated_at=?,payload=? WHERE draft_id=? AND owner_id=? AND revision=?")){p.setLong(1,revision);p.setString(2,store);p.setString(3,number);p.setTimestamp(4,java.sql.Timestamp.from(now));p.setString(5,payload);p.setObject(6,id);p.setString(7,actor);p.setLong(8,expected);if(p.executeUpdate()!=1)throw new ConcurrentModificationException("Draft changed; reopen the latest saved version.");}
            try(var p=c.prepareStatement("INSERT INTO receipt_draft_history VALUES(?,?,?,?)")){p.setObject(1,id);p.setLong(2,revision);p.setTimestamp(3,java.sql.Timestamp.from(now));p.setString(4,payload);p.executeUpdate();}
            return fields("draftId",id,"revision",revision,"updatedAt",now);
        });
    }
    public void deleteReceiptDraft(UUID id,long expected,String actor){transaction(db,c->{consumeReceiptDraft(c,id,expected,actor);return null;});}
    static void consumeReceiptDraft(Connection c,UUID id,long expected,String actor)throws SQLException {
        try(var p=c.prepareStatement("SELECT revision FROM receipt_drafts WHERE draft_id=? AND owner_id=? FOR UPDATE")){p.setObject(1,id);p.setString(2,actor);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException("Saved draft not found.");if(r.getLong(1)!=expected)throw new ConcurrentModificationException("Draft changed since review; reopen the latest saved version.");}}
        for(String table:List.of("receipt_draft_history","receipt_drafts"))try(var p=c.prepareStatement("DELETE FROM "+table+" WHERE draft_id=?")){p.setObject(1,id);p.executeUpdate();}
    }

    public Map<String,Object> importReceiptInventory(String actor){return new PostgresInventoryRepository(db).importReceipts(actor);}
    public void adjustReceiptInventory(UUID id,long expected,String action,int quantity,String location,String note,String actor,Integer lowStockLevel,Integer unitsPerPackage,String unitName,String countUnit){new PostgresInventoryRepository(db).adjust(id,expected,action,quantity,location,note,actor,lowStockLevel,unitsPerPackage,unitName,countUnit);}

    public Map<String, Object> workspace() { return workspace(null); }

    public Map<String, Object> workspace(LocalDate asOf) {
        return transaction(db, c -> {
            c.setReadOnly(true);
            var state = PostgresReceiptState.read(c);
            var records = state.receipts().values().stream().map(r -> new ReceiptRecord(r.transactionId(), r.status(), r.revision(), r.contents().toTransaction(), r.metadata(), r.evidenceIds()))
                    .sorted(Comparator.comparing((ReceiptRecord r) -> r.transaction().getObservations().purchaseDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(r -> ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElse(LocalTime.MIN), Comparator.reverseOrder())).toList();
            var summary = new ProviderBalanceSummary(PostgresReceiptState.rules(state));
            LocalDate day = asOf != null ? asOf : records.stream().map(r -> r.transaction().getObservations().purchaseDate()).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(LocalDate.now());
            List<Object> balances = new ArrayList<>();
            var balanceProviders=new ArrayList<String>();
            boolean targetSplit = state.balances().openings().stream().anyMatch(o -> !o.date().isAfter(day) && (o.provider().equals("Target Gift Card") || o.provider().equals("Target Circle")))
                    || summary.movements(records).stream().anyMatch(m -> m.date() != null && !m.date().isAfter(day) && (m.provider().equals("Target Gift Card") || m.provider().equals("Target Circle")));
            for (String name : summary.programs(records)) {
                if (name.equals("Target") && targetSplit) {
                    continue;
                }
                var balance = summary.balance(day, name, records);
                boolean known = balance.opening() != null || summary.movements(records).stream().anyMatch(m -> m.provider().equals(name));
                if (known) {
                    balances.add(fields("provider", name, "balance", balance));
                    balanceProviders.add(name);
                }
            }
            var plans = new PostgresShoppingPlanRepository(db);
            var purchases = new PostgresPlannedPurchaseRepository(db);
            List<Object> planRows = new ArrayList<>();
            for (UUID id : ids(c, "SELECT plan_id FROM shopping_plan_current ORDER BY shopping_date NULLS LAST,plan_id")) {
                var plan = plans.findById(id).orElseThrow();
                var items = purchases.findByPlanId(id);
                Map<UUID, AdvertisedDeal> planDeals = new HashMap<>();
                Map<UUID, ClippedCoupon> planCoupons = new HashMap<>();
                for (var item : items) {
                    if (item.advertisedDealId() != null) {
                        new PostgresDealRepository(db).findById(item.advertisedDealId()).ifPresent(d -> planDeals.put(d.dealId(), d));

                    }
                    for (var couponId : item.clippedCouponIds()) {
                        new PostgresClippedCouponRepository(db).findById(couponId).ifPresent(v -> planCoupons.put(v.couponId(), v));

                    }
                }
                var pricing = plan.scenario()==null?ShoppingEstimate.calculate(items, planDeals, planCoupons, plan.shoppingDate(), plan.retailer()):scenarioPricing(plan.scenario(),plan.retailer());
                var checkout = new PostgresPlanCheckoutRepository(db).view(id, pricing);
                planRows.add(fields("scenarioEstimate",plan.scenario()==null?null:plan.scenario().estimate(plan.retailer()),"pricing", pricing, "checkout", checkout, "plan", plan, "items", items, "estimatedMerchandise", plan.scenario()==null&&items.isEmpty() ? null : pricing.saleTotal(), "estimateComplete", (plan.scenario()!=null||!items.isEmpty()) && pricing.saleTotal() != null));
            }
            var couponRepo = new PostgresClippedCouponRepository(db);
            List<Object> coupons = new ArrayList<>();
            for (UUID id : ids(c, "SELECT coupon_id FROM clipped_coupon_current ORDER BY provider,coupon_id")) {
                coupons.add(couponFields(couponRepo.findById(id).orElseThrow()));
            }
            var dealRepo = new PostgresDealRepository(db);
            List<Object> deals = new ArrayList<>();
            for (UUID id : ids(c, "SELECT deal_id FROM deal_current ORDER BY valid_through NULLS LAST,deal_id")) {
                deals.add(dealRepo.findById(id).orElseThrow());
            }
            var rebateRepo = new PostgresRebateRepository(db);
            List<Object> rebates = new ArrayList<>();
            for (UUID id : ids(c, "SELECT claim_id FROM rebate_current ORDER BY provider,claim_id")) {
                rebates.add(rebateRepo.findById(id).orElseThrow());
            }
            List<Object> inventory = PostgresInventoryRepository.receiptInventory(c);
            List<Object> reported = new ArrayList<>();
            for (var r : records) {
                if (r.transaction().getObservations().walgreensCash() != null) {
                    reported.add(fields("transactionId", r.transactionId(), "receiptNumber", r.metadata() == null ? null : r.metadata().receiptNumber(), "date", r.transaction().getObservations().purchaseDate(), "status", r.status(), "values", r.transaction().getObservations().walgreensCash()));
                }
            }
            return fields("rewardsOverview", rewardsOverview(summary,records,day,balanceProviders),"productLookup", productLookup(records), "accountActivity", AccountActivityView.project(summary,records,state.rrUsageLinks().values(),state.rewards()), "receiptEarnings", records.stream().flatMap(r -> r.transaction().getObservations().earnings().stream().map(v -> fields("receiptId", r.transactionId(), "receiptNumber", r.metadata() == null ? null : r.metadata().receiptNumber(), "receiptStatus", r.status(), "earning", v))).toList(), "rewardOffers", historicalRewardOffers(records), "asOf", day, "receipts", records.stream().map(r -> fields("transactionId", r.transactionId(), "revision", r.revision(), "status", r.status(), "metadata", r.metadata(), "date", r.transaction().getObservations().purchaseDate(), "time", r.transaction().getObservations().printedTime(), "sortTime", ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).map(LocalTime::toString).orElse(null), "totals", totals(r))).toList(),
                    "receiptPeriods", receiptPeriods(records), "summaries", fields("all", savings(records), "day", savings(SavingsReport.day(records, day)), "week", savings(ReceiptWorksheetReport.week(records, day)), "month", savings(SavingsReport.month(records, YearMonth.from(day)))), "plans", planRows, "coupons", coupons, "deals", deals, "rebates", rebates, "inventory", inventory, "balances", balances, "reportedBalances", reported,
                    "accountHistory", state.balances(), "movements", summary.movements(records), "promotions", state.giftCardPromotions().values(), "rrMatches", state.rrUsageLinks().values(), "rewardRemovals", state.rewardVoids().values());
        });
    }

    /** Suggestions only: historical observations are never merged or mutated. */
    public static List<Object> historicalRewardOffers(List<ReceiptRecord> records) {
        Map<Object, Object> suggestions = new LinkedHashMap<>();
        for (var receipt : records) for (var earning : receipt.transaction().getObservations().earnings()) {
            if (earning.requirement() == null) continue;
            var benefit = earning.expected() == null ? earning.confirmed() : earning.expected();
            if (benefit == null) continue;
            var signature = Arrays.asList(earning.kind(), earning.provider(), earning.offerReference(),
                    benefit.kind(), benefit.amount().stripTrailingZeros(), benefit.currency(), benefit.program(), earning.requirement());
            suggestions.putIfAbsent(signature, fields("id", earning.earningId(), "title", earning.offerReference(),
                    "kind", earning.kind(), "provider", earning.provider(), "amount", benefit.amount(),
                    "form", benefit.kind(), "requirement", earning.requirement(),
                    "sourceReceiptId", receipt.transactionId(),
                    "sourceReceiptNumber", receipt.metadata() == null ? null : receipt.metadata().receiptNumber(),
                    "sourceDate", receipt.transaction().getObservations().purchaseDate()));
        }
        return List.copyOf(suggestions.values());
    }

    public Map<String, Object> receiptTotals(UUID id) {
        var receipt = new PostgresTransactionRepository(db).findById(id).or(() -> new PostgresTransactionRepository(db).findDeletedById(id)).orElseThrow();
        return totals(receipt);
    }

    public static Map<String, Object> totals(ReceiptRecord receipt) {
        return ReceiptViews.totals(receipt);
    }

    /** Classify only explicit recorded discount kinds; never infer from labels. */
    static Map<String,Object> discountBreakdown(coupons.CouponTransaction tx) {
        return ReceiptViews.discountBreakdown(tx);
    }

    /** Calendar browsing summaries; all active receipts participate before pagination/search. */
    public static Map<String,Object> rewardsOverview(ProviderBalanceSummary summary,List<ReceiptRecord> records,LocalDate day,List<String> providers) {
        BigDecimal available=BigDecimal.ZERO,expected=BigDecimal.ZERO;boolean availableComplete=true,expectedComplete=true;
        var missingBalances=new LinkedHashSet<String>();var missingValuations=new LinkedHashSet<String>();int expectedCount=0;
        for(var provider:new LinkedHashSet<>(providers)) {var value=summary.balance(day,provider,records).endUsd();if(value==null){availableComplete=false;missingBalances.add(provider);}else if(value.signum()>0)available=available.add(value);}
        for(var r:SavingsReport.latestActive(records)) {
            var date=r.transaction().getObservations().purchaseDate();if(date==null || date.isAfter(day))continue;
            for(var e:r.transaction().getObservations().earnings()) {
                if(e.status()==RebateStatus.REJECTED || e.status()==RebateStatus.CREDITED || e.status()==RebateStatus.PAID || e.expirationDate()!=null && e.expirationDate().isBefore(day))continue;
                expectedCount++;var benefit=e.expected()!=null?e.expected():e.confirmed();var valuation=SavingsReport.effectiveValuation(e);
                var dollars=benefit==null?null:benefit.kind()==BenefitKind.POINTS?valuation!=null && Currency.getInstance("USD").equals(valuation.currency())?valuation.amount():null:Currency.getInstance("USD").equals(benefit.currency())?benefit.amount():null;
                if(dollars==null){expectedComplete=false;missingValuations.add(e.provider());}else expected=expected.add(dollars);
            }
        }
        return fields("asOf",day,"available",availableComplete?available:null,"expected",expectedComplete?expected:null,"total",availableComplete&&expectedComplete?available.add(expected):null,"knownAvailable",available,"knownExpected",expected,"expectedCount",expectedCount,"missingBalances",missingBalances,"missingValuations",missingValuations);
    }

    /** Receipt history is a product reference, never an on-hand quantity import. */
    public static List<Map<String,Object>> productLookup(List<ReceiptRecord> records) {
        var result=new LinkedHashMap<String,Map<String,Object>>();
        SavingsReport.latestActive(records).stream().sorted(Comparator
                .comparing((ReceiptRecord r)->r.transaction().getObservations().purchaseDate(),Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r->ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElse(LocalTime.MIN),Comparator.reverseOrder())
                .thenComparing(r->r.transactionId().toString())).forEach(r->{
            for(var item:r.transaction().getPurchaseItems()) {
                String sku=item.getProductCode();
                if(sku==null || sku.isBlank()) continue;
                String key=sku.trim().toLowerCase(java.util.Locale.ROOT)+"\n"+item.getProductName().trim().toLowerCase(java.util.Locale.ROOT);
                result.putIfAbsent(key,fields("sku",sku.trim(),"description",item.getProductName(),"regularPrice",item.getRegularUnitPrice(),
                        "purchaseDate",r.transaction().getObservations().purchaseDate(),"receiptNumber",r.metadata()==null?null:r.metadata().receiptNumber()));
            }
        });
        return List.copyOf(result.values());
    }

    public Map<String,Object> receiptReport(String metric, LocalDate from, LocalDate to, String search, String sort) {
        return transaction(db,c->{
            c.setReadOnly(true);
            var records=PostgresReceiptState.read(c).receipts().values().stream().map(r->new ReceiptRecord(r.transactionId(),r.status(),r.revision(),r.contents().toTransaction(),r.metadata(),r.evidenceIds())).toList();
            return receiptReport(records,metric,from,to,search,sort);
        });
    }
    public static Map<String,Object> receiptReport(List<ReceiptRecord> records,String metric,LocalDate from,LocalDate to,String search,String sort) {
        if(!Set.of("regular","oop","saved","net","benefits").contains(metric)) throw new IllegalArgumentException("Choose a receipt total.");
        if(from!=null && to!=null && from.isAfter(to)) throw new IllegalArgumentException("Start date must precede end date.");
        var period=SavingsReport.latestActive(records).stream().filter(r->{var d=r.transaction().getObservations().purchaseDate();return (from==null && to==null) || d!=null && (from==null || !d.isBefore(from)) && (to==null || !d.isAfter(to));}).toList();
        var rows=new ArrayList<Map<String,Object>>();
        for(var r:period) {
            var tx=r.transaction();var totals=savings(List.of(r));
            var base=fields("revision",r.revision(),"receiptId",r.transactionId(),"receiptNumber",r.metadata()==null?null:r.metadata().receiptNumber(),"store",r.metadata()==null?null:r.metadata().retailer(),"date",tx.getObservations().purchaseDate(),"time",tx.getObservations().printedTime(),"sortTime",ReceiptTimeInput.parse(tx.getObservations().printedTime()).map(LocalTime::toString).orElse(null),"cash",totals.get("cash"),"card",totals.get("card"));
            if(metric.equals("benefits")) {
                for(var earning:tx.getObservations().earnings()) {
                    boolean confirmed=earning.confirmed()!=null && (earning.status()==RebateStatus.CREDITED || earning.status()==RebateStatus.PAID);
                    var benefit=confirmed?earning.confirmed():earning.expected()!=null?earning.expected():earning.confirmed();
                    var valuation=SavingsReport.effectiveValuation(earning);
                    BigDecimal amount=benefit==null?null:benefit.kind()==BenefitKind.POINTS?valuation==null||!Currency.getInstance("USD").equals(valuation.currency())?null:valuation.amount():Currency.getInstance("USD").equals(benefit.currency())?benefit.amount():null;
                    var row=new LinkedHashMap<>(base);row.putAll(fields("description",earning.provider()+" — "+Objects.toString(earning.offerReference(),"Receipt reward"),"provider",earning.provider(),"status",earning.status(),"group",confirmed?"Confirmed":earning.status()==RebateStatus.REJECTED?"Rejected":"Unconfirmed","earning",earning,"nativeAmount",benefit==null?null:benefit.amount(),"unit",benefit==null?null:benefit.kind()==BenefitKind.POINTS?"points":benefit.currency(),"amount",amount));rows.add(row);
                }
                var footer=tx.getObservations().walgreensCash();
                if(footer!=null && footer.earned()!=null && tx.getObservations().earnings().stream().noneMatch(e->e.kind()==ReceiptObservations.EarningKind.WALGREENS_CASH)) {
                    var row=new LinkedHashMap<>(base);row.putAll(fields("description","Walgreens Cash — printed receipt earnings (existing total contribution)","provider","Walgreens","status","RECORDED_FOOTER","group","Confirmed","nativeAmount",footer.earned(),"unit","USD","amount",footer.earned()));rows.add(row);
                }
            } else if(metric.equals("regular")) {
                for(var item:tx.getPurchaseItems()) {var row=new LinkedHashMap<>(base);row.putAll(fields("description",item.getProductName(),"sku",item.getProductCode(),"quantity",item.getQuantity(),"unitPrice",item.getRegularUnitPrice(),"amount",item.getRegularUnitPrice()==null?null:item.getRegularUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));rows.add(row);}
                var tax=new LinkedHashMap<>(base);tax.putAll(fields("description","Sales tax","amount",tx.getReceiptTotals().getTaxAmount()));rows.add(tax);
            } else if(metric.equals("oop")) {
                for(var payment:tx.getPayments()) if(Set.of(PaymentType.CASH,PaymentType.CARD,PaymentType.CREDIT_CARD,PaymentType.DEBIT_CARD).contains(payment.getPaymentType())) {var row=new LinkedHashMap<>(base);row.putAll(fields("description",payment.getPaymentType()==PaymentType.CASH?"Cash":"Card","amount",payment.getAmount()));rows.add(row);}
            } else {var row=new LinkedHashMap<>(base);row.putAll(fields("description",String.join(", ",tx.getPurchaseItems().stream().map(PurchaseItem::getProductName).toList()),"amount",totals.get(metric.equals("saved")?"totalSaved":"paidAfterRebates")));rows.add(row);}
        }
        String query=Objects.toString(search,"").trim().toLowerCase(Locale.ROOT);
        rows.removeIf(row->{String text=String.join(" ",List.of("store","receiptNumber","date","time","description","sku","status","group").stream().map(k->Objects.toString(row.get(k),"")).toList()).toLowerCase(Locale.ROOT);var day=(LocalDate)row.get("date");if(day!=null)text+=" "+day.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d yyyy MMM d yyyy M/d/yyyy",Locale.US)).toLowerCase(Locale.ROOT);String digits=query.replaceAll("[^a-zA-Z0-9]","");return !query.isEmpty() && !text.contains(query) && (digits.isEmpty() || !Objects.toString(row.get("receiptNumber"),"").replaceAll("[^a-zA-Z0-9]","").toLowerCase(Locale.ROOT).contains(digits));});
        Comparator<Map<String,Object>> chronological=Comparator.comparing((Map<String,Object> r)->Objects.toString(r.get("date"),"" )).thenComparing(r->Objects.toString(r.get("sortTime"),""));
        Comparator<Map<String,Object>> comparator=switch(Objects.toString(sort,"newest")){case "oldest"->chronological;case "store"->Comparator.comparing((Map<String,Object> r)->Objects.toString(r.get("store"),""),String.CASE_INSENSITIVE_ORDER).thenComparing(chronological);case "item"->Comparator.comparing((Map<String,Object> r)->Objects.toString(r.get("description"),""),String.CASE_INSENSITIVE_ORDER).thenComparing(chronological);default->chronological.reversed();};
        rows.sort(comparator.thenComparing(r->Objects.toString(r.get("receiptId"),"")));
        BigDecimal subtotal=BigDecimal.ZERO,cash=BigDecimal.ZERO,card=BigDecimal.ZERO,unconfirmed=BigDecimal.ZERO;boolean complete=true,unconfirmedComplete=true;
        for(var row:rows){var value=(BigDecimal)row.get("amount");if(metric.equals("benefits") && !"Confirmed".equals(row.get("group"))){if("Unconfirmed".equals(row.get("group"))){if(value==null)unconfirmedComplete=false;else unconfirmed=unconfirmed.add(value);}continue;}if(value==null)complete=false;else{subtotal=subtotal.add(value);if(metric.equals("oop")){if(row.get("description").equals("Cash"))cash=cash.add(value);else card=card.add(value);}}}
        return fields("metric",metric,"summary",savings(period),"rows",rows,"matchedTotal",complete?subtotal:null,"matchedCash",cash,"matchedCard",card,"unconfirmedTotal",unconfirmedComplete?unconfirmed:null);
    }

    public Map<String, Object> receiptRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Choose a valid start and end date.");
        return transaction(db, c -> {
            c.setReadOnly(true);
            var records = PostgresReceiptState.read(c).receipts().values().stream()
                    .map(r -> new ReceiptRecord(r.transactionId(), r.status(), r.revision(), r.contents().toTransaction(), r.metadata(), r.evidenceIds())).toList();
            return savings(SavingsReport.range(records, from, to));
        });
    }

    static Map<String, Object> receiptPeriods(List<ReceiptRecord> records) {
        var active = SavingsReport.latestActive(records);
        Map<String, List<ReceiptRecord>> groups = new LinkedHashMap<>();
        for (var receipt : active) {
            var date = receipt.transaction().getObservations().purchaseDate();
            if (date == null) continue;
            String month = YearMonth.from(date).toString();
            for (String key : List.of("day:" + date, "month:" + month,
                    "week:" + month + ":" + ((date.getDayOfMonth() - 1) / 7 + 1),
                    "shoppingWeek:" + date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)))) {
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(receipt);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("empty", savings(List.of()));
        groups.forEach((key, receipts) -> result.put(key, savings(receipts)));
        return result;
    }

    private static Map<String, Object> savings(List<ReceiptRecord> records) {
        return ReceiptViews.savings(records);
    }

    public List<ReceiptHistoryEntry> history(UUID id) {
        var history = new PostgresTransactionRepository(db).findHistoryByReceiptId(id);
        if (history.isEmpty()) {
            throw new NoSuchElementException();

        }
        return history;
    }

    public ReceiptEvidence evidence(UUID receipt, UUID evidence) {
        var artifact = new PostgresTransactionRepository(db).findEvidenceById(evidence).orElseThrow();
        if (!receipt.equals(artifact.getTransactionId())) {
            throw new NoSuchElementException();

        }
        return artifact;
    }

    public PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal target,
            UUID deal, List<UUID> coupons, long expected, String actor) {
        var planRepo = new PostgresShoppingPlanRepository(db);
        planRepo.findById(planId).orElseThrow();
        if (deal != null) {
            new PostgresDealRepository(db).findById(deal).orElseThrow();
        }
        var couponRepo = new PostgresClippedCouponRepository(db);
        for (var coupon : coupons) {
            couponRepo.findById(coupon).orElseThrow();
        }
        var repo = new PostgresPlannedPurchaseRepository(db);
        if (expected > 0) {
            var old = repo.findById(id).orElseThrow();
            if (!old.planId().equals(planId)) {
                throw new IllegalArgumentException("Purchase belongs to another plan.");

            }
        }
        var next = new PlannedPurchase(id, planId, name, code, quantity, target, deal, coupons, expected == 0 ? 1 : Math.addExact(expected, 1));
        if (expected == 0) {
            repo.save(next, actor);
            return next;
        }
        return repo.update(next, expected, actor, "Web plan item update");
    }

    /** Permanently remove a plan and all its planning-only revisions and checkout drafts. */
    public void deleteShoppingPlan(UUID id, long expected, String actor) {
        PostgresRepositorySupport.requireActor(actor, "Deleted by");
        PostgresRepositorySupport.transaction(db, c -> {
            try (var p = c.prepareStatement("SELECT revision FROM shopping_plan_current WHERE plan_id=? FOR UPDATE")) {
                p.setObject(1, id);
                try (var r = p.executeQuery()) {
                    if (!r.next()) throw new NoSuchElementException();
                    if (r.getLong(1) != expected) throw new java.util.ConcurrentModificationException("Plan changed; refresh before deleting.");
                }
            }
            try (var p = c.prepareStatement("DELETE FROM planned_purchase_history WHERE purchase_id IN (SELECT purchase_id FROM planned_purchase_current WHERE plan_id=?)")) {
                p.setObject(1, id); p.executeUpdate();
            }
            for (String table : List.of("shopping_plan_checkout_history", "shopping_plan_checkout_current", "planned_purchase_current", "shopping_plan_history", "shopping_plan_current")) {
                try (var p = c.prepareStatement("DELETE FROM " + table + " WHERE plan_id=?")) {
                    p.setObject(1, id); p.executeUpdate();
                }
            }
            return null;
        });
    }

    /** Explicit permanent cleanup; refuse redeemed coupons and detach current plan references. */
    public void deleteCatalogRecord(boolean deal, UUID id, long expected, String actor) {
        PostgresRepositorySupport.transaction(db,c->{
            String table=deal?"deal_current":"clipped_coupon_current",key=deal?"deal_id":"coupon_id";
            try(var p=c.prepareStatement("SELECT revision,"+(deal?"NULL":"status")+" FROM "+table+" WHERE "+key+"=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();if(r.getLong(1)!=expected)throw new java.util.ConcurrentModificationException("Record changed; refresh before deleting.");if(!deal&&"APPLIED".equals(r.getString(2)))throw new IllegalArgumentException("Used coupons cannot be permanently deleted.");}}
            if(!deal)try(var p=c.prepareStatement("SELECT 1 FROM clipped_coupon_redemptions WHERE coupon_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("Used coupons cannot be permanently deleted.");}}
            var purchases=new ArrayList<PlannedPurchase>();
            try(var p=c.prepareStatement("SELECT payload FROM planned_purchase_current FOR UPDATE");var r=p.executeQuery()){while(r.next())purchases.add(PostgresRepositorySupport.deserialize(r.getBytes(1),PlannedPurchase.class));}
            for(var old:purchases)if(deal?id.equals(old.advertisedDealId()):old.clippedCouponIds().contains(id)){
                var next=new PlannedPurchase(old.plannedPurchaseId(),old.planId(),old.productName(),old.productCode(),old.quantity(),old.targetUnitPrice(),deal?null:old.advertisedDealId(),deal?old.clippedCouponIds():old.clippedCouponIds().stream().filter(x->!x.equals(id)).toList(),old.revision()+1,old.regularUnitPrice(),old.saleUnitPrice(),deal&&"DEAL".equals(old.priceSource())?"MANUAL":old.priceSource());
                try(var p=c.prepareStatement("UPDATE planned_purchase_current SET revision=?,payload=? WHERE purchase_id=?")){p.setLong(1,next.revision());p.setBytes(2,PostgresRepositorySupport.serialize(next));p.setObject(3,next.plannedPurchaseId());p.executeUpdate();}
                try(var p=c.prepareStatement("INSERT INTO planned_purchase_history(purchase_id,revision,changed_by,reason,payload) VALUES (?,?,?,?,?)")){p.setObject(1,next.plannedPurchaseId());p.setLong(2,next.revision());p.setString(3,actor);p.setString(4,"Removed deleted catalog association");p.setBytes(5,PostgresRepositorySupport.serialize(next));p.executeUpdate();}
            }
            var sources=new HashSet<UUID>();
            if(deal)try(var p=c.prepareStatement("SELECT artifact_id FROM deal_sources WHERE deal_id=? UNION SELECT artifact_id FROM deal_history_sources WHERE deal_id=?")){p.setObject(1,id);p.setObject(2,id);try(var r=p.executeQuery()){while(r.next())sources.add((UUID)r.getObject(1));}}
            for(String t:deal?List.of("deal_history_sources","deal_sources","deal_history","deal_current"):List.of("clipped_coupon_history","clipped_coupon_current"))try(var p=c.prepareStatement("DELETE FROM "+t+" WHERE "+key+"=?")){p.setObject(1,id);p.executeUpdate();}
            for(var source:sources)try(var p=c.prepareStatement("DELETE FROM source_artifacts WHERE artifact_id=? AND NOT EXISTS(SELECT 1 FROM deal_sources WHERE artifact_id=?) AND NOT EXISTS(SELECT 1 FROM deal_history_sources WHERE artifact_id=?) AND NOT EXISTS(SELECT 1 FROM rebate_sources WHERE artifact_id=?) AND NOT EXISTS(SELECT 1 FROM rebate_history_sources WHERE artifact_id=?)")){for(int i=1;i<=5;i++)p.setObject(i,source);p.executeUpdate();}
            return null;
        });
    }

    public void saveCoupons(List<ClippedCoupon> copies, String actor) {
        new PostgresClippedCouponRepository(db).saveAll(copies, actor);
    }

    public void saveDeal(AdvertisedDeal deal, String actor, SourceArtifact source) {
        new PostgresDealRepository(db).save(deal, actor, source);
    }

    public PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal regular, BigDecimal sale, String source,
            UUID deal, List<UUID> coupons, long expected, String actor) {
        new PostgresShoppingPlanRepository(db).findById(planId).orElseThrow();
        if (deal != null) {
            new PostgresDealRepository(db).findById(deal).orElseThrow();
        }
        for (var coupon : coupons) {
            new PostgresClippedCouponRepository(db).findById(coupon).orElseThrow();
        }
        var repo = new PostgresPlannedPurchaseRepository(db);
        if (expected > 0 && !repo.findById(id).orElseThrow().planId().equals(planId)) {
            throw new IllegalArgumentException("Purchase belongs to another plan.");
        }
        var next = new PlannedPurchase(id, planId, name, code, quantity, sale != null ? sale : regular, deal, coupons, expected == 0 ? 1 : Math.addExact(expected, 1), regular, sale, source);
        if (expected == 0) {
            repo.save(next, actor);
            return next;
        }
        return repo.update(next, expected, actor, "Web plan pricing update");
    }

    private static List<UUID> ids(Connection c, String sql) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (var p = c.prepareStatement(sql); var r = p.executeQuery()) {
            while (r.next()) {
                out.add(r.getObject(1, UUID.class));

            }
        }
        return out;
    }

    public static Map<String, Object> couponFields(ClippedCoupon c) {
        return ReceiptViews.couponFields(c);
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);

        }
        return out;
    }


    private static ShoppingEstimate scenarioPricing(ShoppingPlan.Scenario scenario,String retailer){
        var result=scenario.estimate(retailer);var regular=(BigDecimal)result.get("regularTotal");var sale=(BigDecimal)result.get("saleTotal");var coupons=(BigDecimal)result.get("couponTotal");var rewards=(BigDecimal)result.get("potentialRewards");var register=(BigDecimal)result.get("merchandiseAfterCoupons");
        return new ShoppingEstimate(regular,sale,regular.subtract(sale),coupons,rewards,register,register.subtract(rewards),List.of("Scenario qualifications use owner-entered terms; possible rewards are not earned."));
    }

    public Map<String, Object> checkout(UUID planId) {
        var plan = new PostgresShoppingPlanRepository(db)
                .findById(planId)
                .orElseThrow();

        var items = new PostgresPlannedPurchaseRepository(db)
                .findByPlanId(planId);

        Map<UUID, AdvertisedDeal> deals = new HashMap<>();
        Map<UUID, ClippedCoupon> coupons = new HashMap<>();

        var dealRepository = new PostgresDealRepository(db);
        var couponRepository = new PostgresClippedCouponRepository(db);

        for (var item : items) {
            if (item.advertisedDealId() != null) {
                dealRepository.findById(item.advertisedDealId())
                        .ifPresent(d -> deals.put(d.dealId(), d));
            }

            for (var couponId : item.clippedCouponIds()) {
                couponRepository.findById(couponId)
                        .ifPresent(c -> coupons.put(c.couponId(), c));
            }
        }

        var pricing = ShoppingEstimate.calculate(items, deals, coupons, plan.shoppingDate(), plan.retailer());
        return new PostgresPlanCheckoutRepository(db)
                .view(planId, pricing);
    }

    public Map<String, Object> saveCheckout(
            UUID planId,
            long expectedRevision,
            Map<String, Object> values,
            String actor) {

        new PostgresPlanCheckoutRepository(db)
                .save(planId, expectedRevision, values, actor);

        return checkout(planId);
    }


/** Available sources for existing RR deductions; selecting a source never posts another amount. */
public static final class ReceiptRRMatching {
    public record Option(UUID sourceId,boolean receiptEarning,BigDecimal amount,String promotion,UUID sourceReceiptId,String sourceReceiptNumber,LocalDate earnedDate,LocalDate expires,boolean historyUnknown) { }
    public record Use(UUID eventId,BigDecimal amount,String description,boolean matched,boolean needsReview,List<Option> options) { }
    static List<Use> options(InMemoryStore.State state,UUID id) {
        var spent=state.receipts().get(id);
        if(spent==null || spent.status()!=ReceiptStatus.ACTIVE) throw new IllegalArgumentException("Open an active receipt before matching RR.");
        var rows=new ArrayList<Use>();
        for(var use:spent.contents().observations().unidentifiedRR()) {
            boolean matched=state.rrUsageLinks().values().stream().anyMatch(l->l.usageEventId().equals(use.eventId()));
            var options=new ArrayList<Option>();
            if(!matched) {
                for(var source:state.receipts().values()) for(var earning:source.contents().observations().earnings()) {
                    if(earning.kind()!=ReceiptObservations.EarningKind.REGISTER_REWARD || earning.confirmed()==null)continue;
                    var option=new Option(earning.earningId(),true,earning.confirmed().amount(),RegisterRewardLabel.offer(source.contents().toTransaction(),earning).replace(" — buy "," — Buy ").replace(", get $",", Get $"),source.transactionId(),source.metadata()==null?null:source.metadata().receiptNumber(),source.contents().observations().purchaseDate(),earning.expirationDate(),false);
                    if(eligible(state,spent,use,option))options.add(option);
                }
                for(var source:state.rewards().values()) {
                    var origin=source.getEarnedReceiptId()==null?null:state.receipts().get(source.getEarnedReceiptId());
                    var option=new Option(source.getRewardId(),false,source.getFaceValue(),source.getSourceDescription(),source.getEarnedReceiptId(),origin==null||origin.metadata()==null?null:origin.metadata().receiptNumber(),source.getEarnedDate(),source.getExpirationDate(),source.getEarnedReceiptId()==null && (source.getEarnedDate()==null || source.getEarnedDate().equals(spent.contents().observations().purchaseDate())));
                    if(eligible(state,spent,use,option))options.add(option);
                }
            }
            options.sort(Comparator.comparing(Option::earnedDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Option::sourceId));
            boolean needsReview=state.rrUsageLinks().values().stream().filter(l->l.usageEventId().equals(use.eventId())).anyMatch(l->PostgresReceiptState.rules(state).rrUsageNeedsReview(l));
            rows.add(new Use(use.eventId(),use.amount(),use.recordedDescription(),matched,needsReview,List.copyOf(options)));
        }
        return List.copyOf(rows);
    }
    static Option select(InMemoryStore.State state,UUID receipt,UUID use,UUID source,boolean receiptEarning,boolean confirmUnknownHistory) {
        var row=options(state,receipt).stream().filter(u->u.eventId().equals(use)).findFirst().orElseThrow(()->new IllegalArgumentException("RR use no longer exists."));
        var option=row.options().stream().filter(o->o.sourceId().equals(source) && o.receiptEarning()==receiptEarning).findFirst().orElseThrow(()->new IllegalArgumentException("RR source is unavailable."));
        if(option.historyUnknown() && !confirmUnknownHistory)throw new IllegalArgumentException("Confirm the RR was available for this purchase despite unknown earning history.");
        return option;
    }
    public record StandaloneMatch(RegisterReward reward,RRUsageLink link) { }
    /** Creates source evidence for an already recorded use, without inventing an earning or receipt. */
    static StandaloneMatch standalone(InMemoryStore.State state,UUID receiptId,long revision,UUID usageId,UUID rewardId,
            String description,LocalDate earned,LocalDate expires,String reason,String actor) {
        var receipt=state.receipts().get(receiptId);
        if(receipt==null)throw new NoSuchElementException();
        if(receipt.revision()!=revision)throw new ConcurrentModificationException();
        if(receipt.status()!=ReceiptStatus.ACTIVE)throw new IllegalArgumentException("Open an active receipt.");
        var use=receipt.contents().observations().unidentifiedRR().stream().filter(u->u.eventId().equals(usageId)).findFirst().orElseThrow(()->new IllegalArgumentException("Recorded RR use not found."));
        if(state.rrUsageLinks().values().stream().anyMatch(l->l.usageEventId().equals(usageId)))throw new IllegalArgumentException("This RR use is already matched.");
        var day=receipt.contents().observations().purchaseDate();
        if(day!=null && (earned!=null && earned.isAfter(day) || expires!=null && expires.isBefore(day)))throw new IllegalArgumentException("Known RR dates conflict with the purchase date.");
        var reward=new RegisterReward(rewardId,description,use.amount(),earned,expires,null);
        var link=new RRUsageLink(rewardId,false,usageId,receiptId,use.amount(),reason,actor,Instant.now());
        var rules=PostgresReceiptState.rules(state);rules.issuedRewards().save(reward);rules.linkRRUsage(link);
        return new StandaloneMatch(reward,link);
    }
    private static boolean eligible(InMemoryStore.State state,ReceiptRevSnap spent,ReceiptObservations.UnidentifiedRR use,Option option) {
        var day=spent.contents().observations().purchaseDate();
        if(day==null || option.earnedDate()==null && !option.historyUnknown() || option.earnedDate()!=null && option.earnedDate().isAfter(day) || option.expires()!=null && option.expires().isBefore(day))return false;
        var origin=option.sourceReceiptId()==null?null:state.receipts().get(option.sourceReceiptId());
        if(day.equals(option.earnedDate()) && !option.historyUnknown()) {
            var a=origin==null?Optional.<LocalTime>empty():ReceiptTimeInput.parse(origin.contents().observations().printedTime());
            var b=ReceiptTimeInput.parse(spent.contents().observations().printedTime());
            if(a.isEmpty() || b.isEmpty() || !a.orElseThrow().isBefore(b.orElseThrow()))return false;
        }
        try {
            PostgresReceiptState.rules(state).linkRRUsage(new RRUsageLink(option.sourceId(),option.receiptEarning(),use.eventId(),spent.transactionId(),use.amount(),"Availability check","preview",Instant.EPOCH));
            return true;
        } catch(IllegalArgumentException e) { return false; }
    }
}
}
