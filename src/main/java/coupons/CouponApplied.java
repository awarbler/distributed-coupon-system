package coupons;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CouponApplied Represents a coupon discount actually deducted on a receipt.
 * The amount is the total deduction, not a per-unit amount. An empty
 * lineItemIds list means the affected lines are unknown. An advertised coupon
 * that was not redeemed does not have a CouponApplied record.
 *
 */
public final class CouponApplied {

    private final UUID applicationId; // Store a unique identifier for this coupon application.
    private final String printedLabel;
    private final BigDecimal appliedAmount; // Store the total deduction amount for this coupon.
    private final List<UUID> lineItemIds; // Store the identifiers of the receipt lines

    /**
     * Creates an applied coupon with a generated identifier.
     */
    public CouponApplied(
            String printedLabel,
            BigDecimal appliedAmount,
            List<UUID> lineItemIds) {

        this(
                UUID.randomUUID(),
                printedLabel,
                appliedAmount,
                lineItemIds
        );
    }

    /**
     * Reconstructs or corrects an applied coupon using its existing ID.
     *
     * @throws NullPointerException if any argument or line ID is null
     * @throws IllegalArgumentException if the label is blank, the amount is not
     * positive, or line IDs contain duplicates
     */
    public CouponApplied(
            UUID applicationId,
            String printedLabel,
            BigDecimal appliedAmount,
            List<UUID> lineItemIds) {

        this.applicationId = Objects.requireNonNull(
                applicationId,
                "Application ID cannot be null."
        );

        Objects.requireNonNull(
                printedLabel,
                "Printed label cannot be null."
        );

        if (printedLabel.isBlank()) {
            throw new IllegalArgumentException(
                    "Printed label cannot be blank."
            );
        }

        // Preserve the receipt label exactly as supplied.
        this.printedLabel = printedLabel;

        this.appliedAmount = Objects.requireNonNull(
                appliedAmount,
                "Applied amount cannot be null."
        );

        if (appliedAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Applied amount must be greater than zero."
            );
        }

        Objects.requireNonNull(
                lineItemIds,
                "Line item IDs cannot be null."
        );

        // Copy the list and reject null elements.
        List<UUID> copiedIds = List.copyOf(lineItemIds);

        if (new HashSet<>(copiedIds).size() != copiedIds.size()) {
            throw new IllegalArgumentException(
                    "Duplicate line item IDs are not allowed."
            );
        }

        this.lineItemIds = copiedIds;
    }

    /**
     * Compares all recorded values, including identity and monetary scale.
     *
     * @param other object to compare
     * @return true if all recorded fields match
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CouponApplied that)) {
            return false;
        }
        return applicationId.equals(that.applicationId)
                && printedLabel.equals(that.printedLabel)
                && appliedAmount.equals(that.appliedAmount)
                && lineItemIds.equals(that.lineItemIds);
    }

    /**
     * Calculates a hash from all recorded values.
     *
     * @return a hash consistent with value equality
     */
    @Override
    public int hashCode() {
        return Objects.hash(applicationId, printedLabel, appliedAmount, lineItemIds);
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getPrintedLabel() {
        return printedLabel;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public List<UUID> getLineItemIds() {
        return lineItemIds;
    }


}
