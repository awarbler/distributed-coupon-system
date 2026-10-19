package coupons.repository;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Offer progress only; neither qualifying spend nor approval creates an available gift card. */
public record GiftCardPromotion(UUID id, String name, BigDecimal threshold, BigDecimal giftCardValue,
        LocalDate start, LocalDate end, Basis basis, String terms, Status status,
        Map<UUID, Contribution> contributions) implements Serializable {
    public enum Basis { SALE_BEFORE_COUPONS, REVIEWED_ELIGIBLE_AMOUNT }
    public enum Status { TRACKING, SUBMITTED, APPROVED, RECEIVED }
    public record Contribution(UUID receiptId, long reviewedRevision, List<UUID> lineIds,
            BigDecimal reviewedAmount, String note) implements Serializable {
        public Contribution {
            Objects.requireNonNull(receiptId);
            lineIds = List.copyOf(lineIds);
            if (reviewedRevision < 1 || lineIds.isEmpty() || new HashSet<>(lineIds).size() != lineIds.size())
                throw new IllegalArgumentException("Select distinct eligible products and a saved revision.");
            if (reviewedAmount != null && reviewedAmount.signum() < 0) throw new IllegalArgumentException("Eligible amount cannot be negative.");
            Objects.requireNonNull(note);
        }
    }
    public GiftCardPromotion {
        Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(threshold);
        Objects.requireNonNull(giftCardValue); Objects.requireNonNull(start); Objects.requireNonNull(end);
        Objects.requireNonNull(basis); Objects.requireNonNull(terms); Objects.requireNonNull(status);
        if (name.isBlank() || terms.isBlank() || threshold.signum() <= 0 || giftCardValue.signum() <= 0 || end.isBefore(start))
            throw new IllegalArgumentException("Provide offer name, terms, positive amounts and valid dates.");
        contributions = Map.copyOf(contributions);
        contributions.forEach((key, value) -> {
            if (!key.equals(value.receiptId())) throw new IllegalArgumentException("Contribution receipt mismatch.");
            // An unresolved contribution is retained for review, not counted as confirmed spend.
            // Receipt persistence must not depend on completion of a separate rebate review.
        });
    }

    /** Empty means eligibility must be reviewed; never substitutes zero for an unresolved contribution. */
    public Optional<BigDecimal> eligibleAmount(Contribution contribution, ReceiptRecord receipt) {
        if (receipt == null || !receipt.transactionId().equals(contribution.receiptId()) || receipt.status() != ReceiptStatus.ACTIVE)
            return Optional.empty();
        var tx = receipt.transaction();
        LocalDate date = tx.getObservations().purchaseDate();
        if (date == null || date.isBefore(start) || date.isAfter(end)) return Optional.empty();
        var lines = tx.getPurchaseItems().stream().filter(line -> contribution.lineIds().contains(line.getLineItemId())).toList();
        if (lines.size() != contribution.lineIds().size()) return Optional.empty();
        if (basis == Basis.REVIEWED_ELIGIBLE_AMOUNT) return receipt.revision() == contribution.reviewedRevision()
                ? Optional.ofNullable(contribution.reviewedAmount()) : Optional.empty();
        return Optional.ofNullable(coupons.QualifyingSpend.calculate(tx, contribution.lineIds()).amount());
    }

    public GiftCardPromotion withContribution(Contribution contribution) {
        var values = new HashMap<>(contributions);
        values.put(contribution.receiptId(), contribution); // same receipt replaces, never appends twice
        return new GiftCardPromotion(id, name, threshold, giftCardValue, start, end, basis, terms, status, values);
    }
    public GiftCardPromotion withStatus(Status replacement) {
        return new GiftCardPromotion(id, name, threshold, giftCardValue, start, end, basis, terms, replacement, contributions);
    }
}
