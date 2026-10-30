package coupons.repository;

import coupons.rewards.Reward;

import java.util.Optional;
import java.util.UUID;

import coupons.rewards.RegisterReward;

/**
 * Defines database-independent storage for issued Register Rewards.
 *
 * <p>Each reward ID identifies one issued instrument. Rewards with
 * identical descriptions, values, and dates may have different IDs.
 *
 * <p>This interface does not record redemptions or determine availability.
 */
public interface RegisterRewardRepository {

    /**
     * Saves a previously unrecorded Register Reward.
     *
     * <p>An existing reward ID must not be overwritten.
     * Identical reward details with different IDs are allowed.
     *
     * <p>If an earning receipt ID is supplied, implementations must
     * verify that the receipt exists in the committed state, including
     * receipts created within the same atomic operation.
     * An unknown earning receipt ID may remain null.
     *
     * @param reward issued reward to save
     * @throws NullPointerException if reward is null
     * @throws IllegalArgumentException if the reward ID already exists
     *         or a supplied earning receipt reference is invalid
     */
    void save(RegisterReward reward);

    /**
     * Finds an issued reward by its stable identifier.
     *
     * <p>Redeemed and expired rewards remain retrievable.
     * Finding a reward does not establish that it is available.
     *
     * @param rewardId identifier of the issued reward
     * @return the reward, or Optional.empty() if it does not exist
     * @throws NullPointerException if rewardId is null
     */
    Optional<RegisterReward> findById(UUID rewardId);
}