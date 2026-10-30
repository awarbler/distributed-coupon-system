package coupons.promotions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests rebate claim values, associations, and collection isolation. */
class RebateClaimTest {
    /** Creates a recorded USD cash benefit. */
    private RebateBenefit cash(String amount) {
        return new RebateBenefit(BenefitKind.CASH, new BigDecimal(amount),
                Currency.getInstance("USD"), null);
    }

    /** Creates a claim with configurable validation inputs. */
    private RebateClaim claim(UUID id, String provider, String externalRef,
            UUID plannedId, UUID receiptId, RebateStatus status, String rawStatus,
            List<RebateBenefit> expected, List<RebateBenefit> confirmed,
            List<UUID> sources, long revision) {
        return new RebateClaim(id, provider, externalRef, plannedId, receiptId,
                status, rawStatus, expected, confirmed, sources, null, null, revision);
    }

    /** Verifies a planned claim does not require a completed receipt. */
    @Test
    void acceptsPlannedPurchaseWithoutReceipt() {
        UUID planned = UUID.randomUUID();
        RebateClaim value = claim(UUID.randomUUID(), "Provider", null, planned,
                null, RebateStatus.EXPECTED, null, List.of(cash("3.00")),
                List.of(), List.of(), 1);
        assertEquals(planned, value.plannedPurchaseId());
        assertNull(value.transactionId());
        assertTrue(value.confirmedBenefits().isEmpty());
    }

    /** Verifies confirmed entitlement remains distinct from expectation. */
    @Test
    void preservesBenefitFormsAndOriginalText() {
        RebateBenefit gift = new RebateBenefit(BenefitKind.GIFT_CARD,
                new BigDecimal("2.00"), Currency.getInstance("USD"), " Card program ");
        RebateClaim value = claim(UUID.randomUUID(), " Provider ", " REF ", null,
                UUID.randomUUID(), RebateStatus.APPROVED, " Approved ",
                List.of(cash("3.00")), List.of(gift), List.of(), 1);
        assertEquals(List.of(cash("3.00")), value.expectedBenefits());
        assertEquals(List.of(gift), value.confirmedBenefits());
        assertEquals(" Provider ", value.provider());
        assertEquals(" REF ", value.externalClaimRef());
        assertEquals(" Approved ", value.providerStatus());
        assertNull(value.fulfilledOn());
    }

    /** Verifies defensive copying for all collections. */
    @Test
    void copiesAndProtectsCollections() {
        List<RebateBenefit> expected = new ArrayList<>(List.of(cash("3.00")));
        List<RebateBenefit> confirmed = new ArrayList<>(List.of(cash("2.00")));
        UUID source = UUID.randomUUID();
        List<UUID> sources = new ArrayList<>(List.of(source));
        RebateClaim value = claim(UUID.randomUUID(), "Provider", null, null,
                UUID.randomUUID(), RebateStatus.APPROVED, null,
                expected, confirmed, sources, 1);
        expected.clear();
        confirmed.clear();
        sources.clear();
        assertEquals(List.of(cash("3.00")), value.expectedBenefits());
        assertEquals(List.of(cash("2.00")), value.confirmedBenefits());
        assertEquals(List.of(source), value.sourceArtifactIds());
        assertThrows(UnsupportedOperationException.class, () -> value.expectedBenefits().clear());
        assertThrows(UnsupportedOperationException.class, () -> value.confirmedBenefits().clear());
        assertThrows(UnsupportedOperationException.class, () -> value.sourceArtifactIds().clear());
    }

    /** Verifies unassociated imports cannot masquerade as purchase claims. */
    @Test
    void rejectsMissingPurchaseAssociation() {
        assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(),
                "Provider", null, null, null, RebateStatus.EXPECTED, null,
                List.of(), List.of(), List.of(), 1));
    }

    /** Verifies required identity, provider, and status. */
    @Test
    void rejectsNullRequiredFields() {
        UUID receipt = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> claim(null, "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, List.of(), List.of(), List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), null, null,
                null, receipt, RebateStatus.EXPECTED, null, List.of(), List.of(), List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, null, null, List.of(), List.of(), List.of(), 1));
    }

    /** Verifies blank required and optional supplied text is rejected. */
    @Test
    void rejectsBlankText() {
        UUID receipt = UUID.randomUUID();
        for (String blank : List.of("", "   ")) {
            assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(), blank,
                    null, null, receipt, RebateStatus.EXPECTED, null,
                    List.of(), List.of(), List.of(), 1));
            assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(), "Provider",
                    blank, null, receipt, RebateStatus.EXPECTED, null,
                    List.of(), List.of(), List.of(), 1));
            assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(), "Provider",
                    null, null, receipt, RebateStatus.EXPECTED, blank,
                    List.of(), List.of(), List.of(), 1));
        }
    }

    /** Verifies null lists and null elements are rejected for every collection. */
    @Test
    void rejectsNullCollectionsAndElements() {
        UUID receipt = UUID.randomUUID();
        List<RebateBenefit> nullBenefit = Arrays.asList((RebateBenefit) null);
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, null, List.of(), List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, List.of(), null, List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, List.of(), List.of(), null, 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, nullBenefit, List.of(), List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null, List.of(), nullBenefit, List.of(), 1));
        assertThrows(NullPointerException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null,
                List.of(), List.of(), Arrays.asList((UUID) null), 1));
    }

    /** Verifies source uniqueness and positive revisions. */
    @Test
    void rejectsDuplicateSourcesAndInvalidRevisions() {
        UUID source = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                null, receipt, RebateStatus.EXPECTED, null,
                List.of(), List.of(), List.of(source, source), 1));
        for (long revision : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> claim(UUID.randomUUID(), "Provider", null,
                    null, receipt, RebateStatus.EXPECTED, null,
                    List.of(), List.of(), List.of(), revision));
        }
    }

    /** Verifies dates do not silently change status or erase anomalies. */
    @Test
    void preservesRecordedDatesWithoutInferringStatus() {
        LocalDate submitted = LocalDate.of(2026, 10, 16);
        LocalDate fulfilled = submitted.minusDays(1);
        RebateClaim value = new RebateClaim(UUID.randomUUID(), "Provider", null,
                null, UUID.randomUUID(), RebateStatus.PENDING, "Under review",
                List.of(cash("3.00")), List.of(), List.of(), submitted, fulfilled, 1);
        assertEquals(submitted, value.submittedOn());
        assertEquals(fulfilled, value.fulfilledOn());
        assertEquals(RebateStatus.PENDING, value.status());
    }

    /** Verifies new interpretations leave the original value unchanged. */
    @Test
    void correctionPreservesOriginalValues() {
        UUID id = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        RebateClaim original = claim(id, "Provider", null, null, receipt,
                RebateStatus.EXPECTED, null, List.of(cash("3.00")), List.of(), List.of(), 1);
        RebateClaim revised = claim(id, "Provider", null, null, receipt,
                RebateStatus.APPROVED, "Approved", original.expectedBenefits(),
                List.of(cash("2.00")), List.of(), 2);
        assertEquals(original.claimId(), revised.claimId());
        assertEquals(List.of(cash("3.00")), original.expectedBenefits());
        assertTrue(original.confirmedBenefits().isEmpty());
        assertEquals(List.of(cash("2.00")), revised.confirmedBenefits());
        assertEquals(1L, original.revision());
        assertEquals(2L, revised.revision());
    }
}
