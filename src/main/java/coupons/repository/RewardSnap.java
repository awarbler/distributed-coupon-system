package coupons.repository;

import java.util.Objects;

import coupons.rewards.Reward;

/**
 * Preserves values from the legacy generic Reward model.
 *
 * <p>The original double amount and cash-equivalent flag are retained
 * without conversion or financial interpretation.
 *
 * <p>The source may be null because the existing Reward constructor
 * permits it. This snapshot does not impose new validation on legacy data.
 *
 * @param source recorded reward source, possibly null
 * @param amount recorded amount using the legacy representation
 * @param cashEquivalent recorded cash-equivalent classification
 */
public record RewardSnap(
        String source,
        double amount,
        boolean cashEquivalent
) implements java.io.Serializable {

    /**
     * Captures a generic reward's current values.
     *
     * @param reward generic reward to capture
     * @return an immutable snapshot
     * @throws NullPointerException if reward is null
     */
    public static RewardSnap from(Reward reward) {
        Objects.requireNonNull(reward, "Reward cannot be null.");

        return new RewardSnap(
                reward.getSource(),
                reward.getAmount(),
                reward.isCashEquivalent()
        );
    }
}