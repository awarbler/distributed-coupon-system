package coupons.promotions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests advertised-offer values, revisions, and source associations. */
class AdvertisedDealTest {

    /** Verifies multiple sources, unknown dates, and exact text preservation. */
    @Test
    void preservesTermsAndMultipleSources() {
        UUID id = UUID.randomUUID();
        List<UUID> sources = List.of(UUID.randomUUID(), UUID.randomUUID());
        String terms = " Buy two; coupon and reward conditions need verification. ";
        AdvertisedDeal deal = new AdvertisedDeal(id, " Dove offer ", terms,
                " Walgreens ", null, null, sources, 1);
        assertEquals(id, deal.dealId());
        assertEquals(" Dove offer ", deal.title());
        assertEquals(terms, deal.interpretedTerms());
        assertEquals(" Walgreens ", deal.retailer());
        assertEquals(sources, deal.sourceArtifactIds());
        assertEquals(1L, deal.revision());
        assertNull(deal.validFrom());
        assertNull(deal.validThrough());
    }

    /** Verifies one artifact may support multiple distinct offers. */
    @Test
    void allowsOneArtifactToSupportMultipleOffers() {
        UUID source = UUID.randomUUID();
        AdvertisedDeal first = new AdvertisedDeal(UUID.randomUUID(), "Dove", "Recorded terms",
                null, null, null, List.of(source), 1);
        AdvertisedDeal second = new AdvertisedDeal(UUID.randomUUID(), "Colgate", "Recorded terms",
                null, null, null, List.of(source), 1);
        assertNotEquals(first.dealId(), second.dealId());
        assertEquals(first.sourceArtifactIds(), second.sourceArtifactIds());
        assertNull(first.retailer());
    }

    /** Verifies defensive copying and unmodifiable source associations. */
    @Test
    void defensivelyCopiesSources() {
        UUID source = UUID.randomUUID();
        List<UUID> supplied = new ArrayList<>(List.of(source));
        AdvertisedDeal deal = new AdvertisedDeal(UUID.randomUUID(), "Example", "Recorded terms",
                null, null, null, supplied, 1);
        supplied.clear();
        assertEquals(List.of(source), deal.sourceArtifactIds());
        assertThrows(UnsupportedOperationException.class, () -> deal.sourceArtifactIds().clear());
    }

    /** Verifies unknown individual date bounds and same-day offers remain representable. */
    @Test
    void acceptsKnownAndUnknownDateBounds() {
        LocalDate date = LocalDate.of(2026, 10, 16);
        UUID source = UUID.randomUUID();
        AdvertisedDeal startOnly = new AdvertisedDeal(UUID.randomUUID(), "Example", "Terms",
                null, date, null, List.of(source), 1);
        AdvertisedDeal endOnly = new AdvertisedDeal(UUID.randomUUID(), "Example", "Terms",
                null, null, date, List.of(source), 1);
        AdvertisedDeal sameDay = new AdvertisedDeal(UUID.randomUUID(), "Example", "Terms",
                null, date, date, List.of(source), 1);
        assertEquals(date, startOnly.validFrom());
        assertNull(startOnly.validThrough());
        assertNull(endOnly.validFrom());
        assertEquals(date, endOnly.validThrough());
        assertEquals(date, sameDay.validThrough());
    }

    /** Verifies empty and duplicate source references are rejected. */
    @Test
    void rejectsEmptyAndDuplicateSources() {
        UUID source = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null, null, null, List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null, null, null, List.of(source, source), 1));
    }

    /** Verifies null source collections and null source IDs are rejected. */
    @Test
    void rejectsNullSourceListAndElements() {
        assertThrows(NullPointerException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null, null, null, null, 1));
        assertThrows(NullPointerException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null, null, null,
                Arrays.asList(UUID.randomUUID(), null), 1));
    }

    /** Verifies required identity and text references are present. */
    @Test
    void rejectsMissingRequiredFields() {
        List<UUID> sources = List.of(UUID.randomUUID());
        assertThrows(NullPointerException.class, () -> new AdvertisedDeal(
                null, "Example", "Terms", null, null, null, sources, 1));
        assertThrows(NullPointerException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), null, "Terms", null, null, null, sources, 1));
        assertThrows(NullPointerException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", null, null, null, null, sources, 1));
    }

    /** Verifies blank required text and blank supplied retailers are rejected. */
    @Test
    void rejectsBlankText() {
        List<UUID> sources = List.of(UUID.randomUUID());
        for (String blank : new String[]{"", "   "}) {
            assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                    UUID.randomUUID(), blank, "Terms", null, null, null, sources, 1));
            assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                    UUID.randomUUID(), "Example", blank, null, null, null, sources, 1));
            assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                    UUID.randomUUID(), "Example", "Terms", blank, null, null, sources, 1));
        }
    }

    /** Verifies reversed date ranges and invalid revisions are rejected. */
    @Test
    void rejectsInvalidDatesAndRevision() {
        List<UUID> sources = List.of(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null, null, null, sources, 0));
        assertThrows(IllegalArgumentException.class, () -> new AdvertisedDeal(
                UUID.randomUUID(), "Example", "Terms", null,
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 11), sources, 1));
    }

    /** Verifies a revised interpretation does not change the original offer revision. */
    @Test
    void preservesOriginalInterpretation() {
        UUID id = UUID.randomUUID();
        List<UUID> sources = List.of(UUID.randomUUID());
        AdvertisedDeal before = new AdvertisedDeal(id, "Example", "Initial reading",
                null, null, null, sources, 1);
        AdvertisedDeal after = new AdvertisedDeal(id, before.title(), "Corrected reading",
                null, null, null, sources, 2);
        assertEquals(before.dealId(), after.dealId());
        assertEquals("Initial reading", before.interpretedTerms());
        assertEquals("Corrected reading", after.interpretedTerms());
        assertEquals(1L, before.revision());
        assertEquals(2L, after.revision());
    }
}
