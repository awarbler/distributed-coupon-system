package coupons.repository;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Preserves original receipt-source bytes independently of corrections.
 *
 * <p>Content is defensively copied. Media type describes the supplied
 * content but does not validate its actual format.
 */
public final class ReceiptEvidence implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    /** Stable identifier of this source artifact. */
    private final UUID evidenceId;
    /** Identifier of the associated receipt. */
    private final UUID transactionId;
    /** Recorded media type. */
    private final String mediaType;
    /** Original bytes, never exposed directly. */
    private final byte[] content;

    /**
     * Creates or reconstructs an immutable source artifact.
     *
     * <p>Preserve identifiers when reconstructing saved evidence.
     * No decoding, conversion, or other transformation occurs.
     *
     * @param evidenceId stable artifact identifier
     * @param transactionId associated receipt identifier
     * @param mediaType recorded media type
     * @param content original source bytes
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if mediaType is blank or content empty
     */
    public ReceiptEvidence(UUID evidenceId, UUID transactionId,
            String mediaType, byte[] content) {
        this.evidenceId = Objects.requireNonNull(evidenceId, "Evidence ID cannot be null.");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null.");
        Objects.requireNonNull(mediaType, "Media type cannot be null.");
        Objects.requireNonNull(content, "Content cannot be null.");
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("Media type cannot be blank.");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("Evidence content cannot be empty.");
        }
        this.mediaType = mediaType;
        this.content = content.clone();
    }

    /**
     * Returns the artifact identifier.
     * @return stable evidence ID
     */
    public UUID getEvidenceId() {
        return evidenceId;
    }

    /**
     * Returns the associated receipt identifier.
     * @return stable receipt ID
     */
    public UUID getTransactionId() {
        return transactionId;
    }

    /**
     * Returns the recorded media type.
     * @return media type exactly as supplied
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Returns a defensive copy of the original bytes.
     * @return independently mutable copy of the source content
     */
    public byte[] getContent() {
        return content.clone();
    }

    /**
     * Compares identifiers, media type, and exact content bytes.
     * @param other object to compare
     * @return true if all recorded values match
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReceiptEvidence that)) {
            return false;
        }
        return evidenceId.equals(that.evidenceId)
                && transactionId.equals(that.transactionId)
                && mediaType.equals(that.mediaType)
                && Arrays.equals(content, that.content);
    }

    /**
     * Calculates a hash consistent with value equality.
     * @return hash of recorded values
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hash(evidenceId, transactionId, mediaType)
                + Arrays.hashCode(content);
    }
}
