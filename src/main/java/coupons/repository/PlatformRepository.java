package coupons.repository;

import coupons.promotions.AdvertisedDeal;
import coupons.promotions.ClippedCoupon;
import coupons.shopping.PlannedPurchase;
import coupons.promotions.SourceArtifact;

import coupons.rewards.ProviderBalanceSummary.AccountActivityView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Storage-independent web workspace and linked account operations.
 * Implementations retain ownership of their atomic persistence boundaries.
 */
public interface PlatformRepository {
    void savePromotion(UUID id,String name,BigDecimal threshold,BigDecimal value,LocalDate start,LocalDate end,String terms,String status,String expectedToken);
    default void savePromotion(UUID id,String name,BigDecimal threshold,BigDecimal value,LocalDate start,LocalDate end,String terms,String status,String expectedToken,Map<String,String> rules){if(rules!=null&&!rules.isEmpty())throw new UnsupportedOperationException("Booster storage requires promotion support.");savePromotion(id,name,threshold,value,start,end,terms,status,expectedToken);}
    void deletePromotion(UUID id,String expectedToken);

    void unlinkRebateReceipt(UUID rebate,UUID receipt,String expectedContribution);

    Map<String,Object> saveRebateReceipt(UUID rebate,UUID receipt,long receiptRevision,List<UUID> lines,BigDecimal netAmount,
        String note,boolean submitted,LocalDate submittedOn,String reference,String expectedContribution);

    void correctStandaloneAmount(UUID id,BigDecimal expected,BigDecimal amount);
    void correctStandaloneAmount(UUID id,BigDecimal expected,BigDecimal amount,Map<String,Object> metadata);

    Map<String,Object> memberReward(UUID id);

    BigDecimal memberAmount(UUID id);

    void correctMemberAmount(UUID id,UUID correction,BigDecimal expected,BigDecimal amount,String actor);

    void correctMemberAmount(UUID id,UUID correction,BigDecimal expected,BigDecimal amount,BigDecimal expectedPoints,BigDecimal points,String actor);

    void deleteStandaloneReward(UUID id,String actor);

    boolean earningMatched(UUID earning);

    void addStandaloneReward(coupons.rewards.RegisterReward reward);

    List<Map<String,Object>> standaloneRewards();

    RRUsageLink addStandaloneMatch(UUID receipt,long revision,UUID use,UUID reward,String description,LocalDate earned,LocalDate expires,String reason,String actor);

    List<?> rrOptions(UUID id);

    RRUsageLink matchRR(UUID id,long revision,UUID use,UUID source,boolean receiptEarning,String reason,String actor,boolean confirmUnknownHistory);

    void savePayout(ProviderRedemption payout);
    default Map<String,Object> reconcilePayoutCards(String actor){throw new UnsupportedOperationException();}

    Map<String,Object> payout(UUID id);
    default void correctPayout(UUID id,long revision,Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}

    void finishPayout(UUID id,long revision,ProviderRedemption.Status status,LocalDate date);

    void saveMemberReward(AccountAdjustment reward);
    void recordUnavailableReward(UUID event,String provider,LocalDate date,BigDecimal amount,BigDecimal points,UUID source,String category,String reason,String actor);

    void saveOpening(AccountOpening opening);

    List<AccountActivityView.RewardLink> rewardUses(UUID id);

    Map<String,Object> account(String provider,LocalDate from,LocalDate to);

    List<Object> receiptDrafts(String actor);

    Map<String,Object> receiptDraft(UUID id,String actor);

    Map<String,Object> saveReceiptDraft(UUID id,long expected,String payload,String store,String number,String actor);

    void deleteReceiptDraft(UUID id,long expected,String actor);

    Map<String,Object> importReceiptInventory(String actor);

    void adjustReceiptInventory(UUID id,long expected,String action,int quantity,String location,String note,String actor,Integer lowStockLevel,Integer unitsPerPackage,String unitName,String countUnit);
    default void adjustReceiptInventory(UUID id,long expected,String action,int quantity,String location,String note,String actor,Integer lowStockLevel,Integer unitsPerPackage,String unitName,String countUnit,String category){if(category!=null)throw new UnsupportedOperationException("Inventory categories require persistent inventory storage.");adjustReceiptInventory(id,expected,action,quantity,location,note,actor,lowStockLevel,unitsPerPackage,unitName,countUnit); }
    default void adjustReceiptInventory(UUID id,long expected,String action,int quantity,String location,String note,String actor,Integer lowStockLevel,Integer unitsPerPackage,String unitName,String countUnit,String category,java.math.BigDecimal comparisonSize,String comparisonUnit){if(comparisonUnit!=null)throw new UnsupportedOperationException("Measured unit prices require persistent inventory storage.");adjustReceiptInventory(id,expected,action,quantity,location,note,actor,lowStockLevel,unitsPerPackage,unitName,countUnit,category); }

    Map<String, Object> workspace();

    Map<String, Object> workspace(LocalDate asOf);

    Map<String, Object> receiptTotals(UUID id);

    Map<String,Object> receiptReport(String metric, LocalDate from, LocalDate to, String search, String sort);

    Map<String, Object> receiptRange(LocalDate from, LocalDate to);

    List<ReceiptHistoryEntry> history(UUID id);

    ReceiptEvidence evidence(UUID receipt, UUID evidence);

    PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal target,
            UUID deal, List<UUID> coupons, long expected, String actor);

    void deleteShoppingPlan(UUID id, long expected, String actor);
    void linkShoppingPlanReceipt(UUID planId,long planRevision,UUID receiptId,long receiptRevision,String actor);

    void deleteCatalogRecord(boolean deal, UUID id, long expected, String actor);

    void saveCoupons(List<ClippedCoupon> copies, String actor);

    void saveDeal(AdvertisedDeal deal, String actor, SourceArtifact source);

    PlannedPurchase savePurchase(UUID planId, UUID id, String name, String code, int quantity, BigDecimal regular, BigDecimal sale, String source,
            UUID deal, List<UUID> coupons, long expected, String actor);

    Map<String, Object> checkout(UUID planId);

    Map<String, Object> saveCheckout(
            UUID planId,
            long expectedRevision,
            Map<String, Object> values,
            String actor);

    default RRUsageLink correctRRMatch(UUID receipt,long revision,UUID use,UUID expectedSource,UUID source,boolean earning,String reason,String actor,boolean unknown){throw new UnsupportedOperationException();}
    default void correctMemberDetails(UUID id,UUID correction,java.math.BigDecimal expected,java.math.BigDecimal amount,java.math.BigDecimal expectedPoints,java.math.BigDecimal points,Map<String,String> details,String actor){throw new UnsupportedOperationException();}
    default Map<String,Object> linkPayoutCard(UUID id,long expected,Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}
    default List<Map<String,Object>> rewardInstruments(){throw new UnsupportedOperationException();}
    default Map<String,Object> rewardInstrument(UUID id){throw new UnsupportedOperationException();}
    default Map<String,Object> createRewardInstrument(Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}
    default Map<String,Object> correctRewardInstrument(UUID id,long expected,Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}
    default Map<String,Object> rewardInstrumentEvent(UUID id,long expected,Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}
    default Map<String,Object> exchangeRewardInstrument(UUID id,long expected,Map<String,Object> fields,String actor){throw new UnsupportedOperationException();}
}
