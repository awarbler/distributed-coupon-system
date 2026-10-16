package coupons;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Preserves original source bytes separately from their interpretation.
 *
 * <p>No decoding, OCR, parsing, or normalization occurs. This immutable
 * object does not itself persist content or enforce storage immutability.
 */
public final class SourceArtifact {
    /** Stable artifact identifier. */
    private final UUID artifactId;
    /** Recorded filename or descriptive label. */
    private final String label;
    /** Recorded media type without format verification. */
    private final String mediaType;
    /** Original source reference, if known. */
    private final String sourceReference;
    /** Application capture time, not offer validity time. */
    private final Instant capturedAt;
    /** Original bytes, never exposed directly. */
    private final byte[] content;

    /**
     * Creates or reconstructs an immutable source artifact.
     *
     * @param artifactId stable artifact identifier
     * @param label recorded filename or descriptive label
     * @param mediaType recorded media type
     * @param sourceReference provenance reference, null if unknown
     * @param capturedAt original application capture timestamp
     * @param content original source bytes
     * @throws NullPointerException if a required argument is null
     * @throws IllegalArgumentException if required text is blank, a supplied
     *         source reference is blank, or content is empty
     */
    public SourceArtifact(UUID artifactId, String label, String mediaType,
            String sourceReference, Instant capturedAt, byte[] content) {
        this.artifactId = Objects.requireNonNull(artifactId, "Artifact ID cannot be null.");
        Objects.requireNonNull(label, "Label cannot be null.");
        Objects.requireNonNull(mediaType, "Media type cannot be null.");
        if (label.isBlank() || mediaType.isBlank()) {
            throw new IllegalArgumentException("Label and media type cannot be blank.");
        }
        if (sourceReference != null && sourceReference.isBlank()) {
            throw new IllegalArgumentException("Source reference must be null or nonblank.");
        }
        this.capturedAt = Objects.requireNonNull(capturedAt, "Capture timestamp cannot be null.");
        Objects.requireNonNull(content, "Content cannot be null.");
        if (content.length == 0) {
            throw new IllegalArgumentException("Source content cannot be empty.");
        }
        this.label = label;
        this.mediaType = mediaType;
        this.sourceReference = sourceReference;
        this.content = content.clone();
    }

    /**
     * Returns artifact identity.
     * @return stable artifact ID
     */
    public UUID getArtifactId() { return artifactId; }

    /**
     * Returns the recorded label.
     * @return label exactly as supplied
     */
    public String getLabel() { return label; }

    /**
     * Returns the recorded media type.
     * @return media type exactly as supplied
     */
    public String getMediaType() { return mediaType; }

    /**
     * Returns provenance information.
     * @return original reference, or null if unknown
     */
    public String getSourceReference() { return sourceReference; }

    /**
     * Returns the application's capture timestamp.
     * @return capture time
     */
    public Instant getCapturedAt() { return capturedAt; }

    /**
     * Returns an independent copy of original content.
     * @return copied source bytes
     */
    public byte[] getContent() { return content.clone(); }

    /**
     * Compares all metadata and exact source bytes.
     * @param other object to compare
     * @return true if all recorded values match
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof SourceArtifact that)) { return false; }
        return artifactId.equals(that.artifactId)
                && label.equals(that.label)
                && mediaType.equals(that.mediaType)
                && Objects.equals(sourceReference, that.sourceReference)
                && capturedAt.equals(that.capturedAt)
                && Arrays.equals(content, that.content);
    }

    /**
     * Calculates a hash consistent with equality.
     * @return hash of metadata and bytes
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hash(artifactId, label, mediaType, sourceReference, capturedAt)
                + Arrays.hashCode(content);
    }
}
