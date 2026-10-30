package coupons.promotions;

/**
 * Identifies the recorded processing state of a rebate claim.
 *
 * <p>These are possible states, not a mandatory processing sequence.
 * Provider-specific status text and supporting evidence must be
 * preserved separately from this internal classification.
 *
 * <p>Status does not establish a benefit's amount, unit, practical
 * value, or availability. Cash, points, and gift cards must remain
 * distinguishable in the rebate claim model.
 */
public enum RebateStatus {

    /**
     * An anticipated rebate associated with a planned or completed
     * purchase that has not been submitted.
     *
     * <p>Eligibility and receipt of the benefit remain unconfirmed.
     * An advertised offer alone must not create an expected claim.
     */
    EXPECTED,

    /** A submission was recorded, but its processing outcome is unknown. */
    SUBMITTED,

    /** The provider reports that the claim is awaiting processing or review. */
    PENDING,

    /**
     * The provider approved the claim, but credit or final fulfillment
     * has not been confirmed.
     */
    APPROVED,

    /**
     * The provider confirmed value was credited to its account balance.
     *
     * <p>This does not establish that cash was paid externally or that
     * a gift card was delivered.
     */
    CREDITED,

    /**
     * Final fulfillment attributable to the claim has been confirmed,
     * such as an external cash payment or delivery of a gift card.
     *
     * <p>A provider-account credit alone does not establish final
     * fulfillment. Combined payouts require explicit allocation
     * to individual claims.
     */
    PAID,

    /**
     * The provider rejected the claim.
     *
     * <p>Rejection history must remain available if a later appeal
     * or correction changes the outcome.
     */
    REJECTED
}
