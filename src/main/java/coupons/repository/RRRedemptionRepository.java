package coupons.repository;

import coupons.rewards.Reward;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import coupons.rewards.RegisterRewardRedemption;

/**
 * Defines database-independent lookup of Register Reward redemptions.
 *
 * <p>RR stands for Register Reward.
 *
 * <p>Redemption writes belong to the atomic receipt-write operation.
 * This interface does not provide independent save or delete methods.
 *
 * <p>Soft-deleting a receipt must not hide its redemption records
 * or make its rewards available again.
 */
public interface RRRedemptionRepository {

    /**
     * Finds a redemption by its stable identifier.
     *
     * @param redemptionId identifier of the redemption
     * @return the redemption, or Optional.empty() if absent
     * @throws NullPointerException if redemptionId is null
     */
    Optional<RegisterRewardRedemption> findById(UUID redemptionId);

    /**
     * Finds the recorded redemption of a specific reward.
     *
     * <p>This contract permits at most one redemption per reward.
     * Absence alone does not establish availability; expiration,
     * voiding, and other applicable conditions must also be checked.
     *
     * @param rewardId identifier of the issued reward
     * @return its redemption, or Optional.empty() if none is recorded
     * @throws NullPointerException if rewardId is null
     */
    Optional<RegisterRewardRedemption> findByRewardId(UUID rewardId);

    /**
     * Finds redemptions associated with a receipt.
     *
     * <p>Results include redemptions for soft-deleted receipts.
     * The returned list is unmodifiable and ordered by redemption ID
     * using UUID natural ordering. This is not chronological ordering.
     *
     * @param receiptId identifier of the receipt
     * @return matching redemptions, or an empty list if none exist
     * @throws NullPointerException if receiptId is null
     */
    List<RegisterRewardRedemption> findByReceiptId(UUID receiptId);
}