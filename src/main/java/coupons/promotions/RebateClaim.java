package coupons.promotions;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents one anticipated or recorded rebate claim.
 *
 * <p>An advertised offer alone does not establish a claim. Incomplete imports
 * without a purchase association must remain separate evidence until reconciled.
 *
 * <p>Expected benefits describe anticipated value; confirmed benefits describe
 * provider-confirmed entitlement, not necessarily received or spendable value.
 * CREDITED and PAID describe progress of the same benefit, not extra earnings.
 *
 * <p>Provider-account balances belong to separate account/ledger functionality.
 * Credit does not establish redemption eligibility. Provider thresholds and
 * redemption restrictions require changeable, provider-specific policies.
 * Points retain their original units until a documented conversion or redemption.
 * Exchanging points for a gift card must not create a second reward earning.
 * Withdrawal requests and completed payouts must remain distinguishable.
 *
 * <p>Dates and text are preserved as recorded. Dates do not determine status;
 * inconsistent observations require review rather than silent correction.
 *
 * @param claimId stable claim identifier
 * @param provider recorded provider
 * @param externalClaimRef provider reference, or null when unknown
 * @param plannedPurchaseId optional planning reference; identity validation
 *        awaits integration with the planned-purchase workflow
 * @param transactionId completed receipt identifier, or null
 * @param status interpreted processing state
 * @param providerStatus original provider status text, or null
 * @param expectedBenefits anticipated benefits; empty when unquantified
 * @param confirmedBenefits confirmed entitlement; empty when unquantified
 * @param sourceArtifactIds supporting artifacts; empty when unavailable
 * @param submittedOn recorded submission date, or null
 * @param fulfilledOn recorded final fulfillment date, or null
 * @param revision positive claim revision
 */
public record RebateClaim(
        UUID claimId,
        String provider,
        String externalClaimRef,
        UUID plannedPurchaseId,
        UUID transactionId,
        RebateStatus status,
        String providerStatus,
        List<RebateBenefit> expectedBenefits,
        List<RebateBenefit> confirmedBenefits,
        List<UUID> sourceArtifactIds,
        LocalDate submittedOn,
        LocalDate fulfilledOn,
        long revision
) {
    /**
     * Validates structure and copies collections without interpreting evidence.
     *
     * @throws NullPointerException if a required reference or list element is null
     * @throws IllegalArgumentException if supplied text is blank, both purchase
     *         references are absent, source IDs repeat, or revision is not positive
     */
    public RebateClaim {
        Objects.requireNonNull(claimId, "Claim ID cannot be null.");
        Objects.requireNonNull(provider, "Provider cannot be null.");
        Objects.requireNonNull(status, "Status cannot be null.");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("Provider cannot be blank.");
        }
        requireOptionalText(externalClaimRef, "External claim reference");
        requireOptionalText(providerStatus, "Provider status");
        if (plannedPurchaseId == null && transactionId == null) {
            throw new IllegalArgumentException(
                    "A planned or completed purchase reference is required.");
        }
        expectedBenefits = List.copyOf(expectedBenefits);
        confirmedBenefits = List.copyOf(confirmedBenefits);
        sourceArtifactIds = List.copyOf(sourceArtifactIds);
        if (new HashSet<>(sourceArtifactIds).size() != sourceArtifactIds.size()) {
            throw new IllegalArgumentException("Source artifact IDs cannot repeat.");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("Revision must be at least one.");
        }
    }

    /**
     * Rejects blank supplied text without modifying its value.
     *
     * @param value optional recorded text
     * @param fieldName field description
     * @throws IllegalArgumentException if supplied text is blank
     */
    private static void requireOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be null or nonblank.");
        }
    }
}
