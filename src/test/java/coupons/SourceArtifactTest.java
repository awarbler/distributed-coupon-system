package coupons;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests original source preservation and isolation. */
class SourceArtifactTest {
    /** Verifies exact text bytes and provenance are retained. */
    @Test
    void preservesOriginalTextAndMetadata() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        byte[] text = "Tide PODS\r\nSKU: 00123465\r\n".getBytes(StandardCharsets.UTF_8);
        SourceArtifact source = new SourceArtifact(id, " Imported notes ",
                "text/plain; charset=UTF-8", "spreadsheet:Sheet1!A2", time, text);
        assertEquals(id, source.getArtifactId());
        assertEquals(" Imported notes ", source.getLabel());
        assertEquals("text/plain; charset=UTF-8", source.getMediaType());
        assertEquals("spreadsheet:Sheet1!A2", source.getSourceReference());
        assertEquals(time, source.getCapturedAt());
        assertArrayEquals(text, source.getContent());
    }

    /** Verifies no mutable content arrays are shared. */
    @Test
    void defensivelyCopiesContent() {
        byte[] supplied = {1, 2, 3};
        SourceArtifact source = new SourceArtifact(UUID.randomUUID(), "Example attachment",
                "application/octet-stream", null, Instant.parse("2026-10-16T15:00:00Z"), supplied);
        supplied[0] = 9;
        byte[] returned = source.getContent();
        returned[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, source.getContent());
        assertNull(source.getSourceReference());
    }

    /** Verifies reconstructed artifacts compare by value. */
    @Test
    void comparesReconstructedArtifactsByValue() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        SourceArtifact original = new SourceArtifact(id, "Example", "application/octet-stream", null, time, new byte[]{1, 2, 3});
        SourceArtifact copy = new SourceArtifact(id, "Example", "application/octet-stream", null, time, new byte[]{1, 2, 3});
        SourceArtifact changed = new SourceArtifact(id, "Example", "application/octet-stream", null, time, new byte[]{1, 2, 4});
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertNotEquals(original, changed);
    }

    /** Verifies required metadata and content cannot be missing. */
    @Test
    void rejectsNullRequiredFields() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        byte[] bytes = {1};
        assertThrows(NullPointerException.class, () -> new SourceArtifact(null, "Example", "text/plain", null, time, bytes));
        assertThrows(NullPointerException.class, () -> new SourceArtifact(id, null, "text/plain", null, time, bytes));
        assertThrows(NullPointerException.class, () -> new SourceArtifact(id, "Example", null, null, time, bytes));
        assertThrows(NullPointerException.class, () -> new SourceArtifact(id, "Example", "text/plain", null, null, bytes));
        assertThrows(NullPointerException.class, () -> new SourceArtifact(id, "Example", "text/plain", null, time, null));
    }

    /** Verifies rejection of blank text and empty content. */
    @Test
    void rejectsBlankFieldsAndEmptyContent() {
        UUID id = UUID.randomUUID();
        Instant time = Instant.parse("2026-10-16T15:00:00Z");
        byte[] bytes = {1};
        assertThrows(IllegalArgumentException.class, () -> new SourceArtifact(id, "   ", "text/plain", null, time, bytes));
        assertThrows(IllegalArgumentException.class, () -> new SourceArtifact(id, "Example", "   ", null, time, bytes));
        assertThrows(IllegalArgumentException.class, () -> new SourceArtifact(id, "Example", "text/plain", "   ", time, bytes));
        assertThrows(IllegalArgumentException.class, () -> new SourceArtifact(id, "Example", "text/plain", null, time, new byte[0]));
    }
}
