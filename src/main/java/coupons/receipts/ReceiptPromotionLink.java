package coupons.receipts;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Receipt-side eligibility observation committed with its promotion contribution. */
public record ReceiptPromotionLink(UUID promotionId, List<UUID> eligibleLineIds,
        BigDecimal reviewedEligibleAmount, String eligibilityNote, Instant reviewedAt) implements Serializable {
    public ReceiptPromotionLink {
        Objects.requireNonNull(promotionId); Objects.requireNonNull(eligibilityNote); Objects.requireNonNull(reviewedAt);
        eligibleLineIds = List.copyOf(eligibleLineIds);
        if (eligibleLineIds.isEmpty() || new HashSet<>(eligibleLineIds).size() != eligibleLineIds.size()
                || eligibilityNote.isBlank() || reviewedEligibleAmount != null && reviewedEligibleAmount.signum() < 0)
            throw new IllegalArgumentException("Select distinct eligible products and provide an eligibility note and nonnegative amount.");
    }
}
