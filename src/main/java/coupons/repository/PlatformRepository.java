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
    void deletePromotion(UUID id,String expectedToken);

    void unlinkRebateReceipt(UUID rebate,UUID receipt,String expectedContribution);

    Map<String,Object> saveRebateReceipt(UUID rebate,UUID receipt,long receiptRevision,List<UUID> lines,BigDecimal netAmount,
        String note,boolean submitted,LocalDate submittedOn,String reference,String expectedContribution);

    void correctStandaloneAmount(UUID id,BigDecimal expected,BigDecimal amount);

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

    Map<String,Object> payout(UUID id);

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

}
