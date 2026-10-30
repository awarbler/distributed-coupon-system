package coupons.repository;

import coupons.promotions.SourceArtifact;
import java.sql.*;
import java.util.*;

/** PostgreSQL implementation of immutable source-artifact storage. */
public final class PostgresSourceArtifactRepository implements SourceArtifactRepository {
    private final PostgresConnectionFactory connections;
    public PostgresSourceArtifactRepository(PostgresConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }
    @Override public void save(SourceArtifact artifact) {
        Objects.requireNonNull(artifact);
        PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO source_artifacts VALUES (?,?,?,?,?,?) ON CONFLICT (artifact_id) DO NOTHING")) {
                p.setObject(1, artifact.getArtifactId()); p.setString(2, artifact.getLabel());
                p.setString(3, artifact.getMediaType()); p.setString(4, artifact.getSourceReference());
                p.setTimestamp(5, Timestamp.from(artifact.getCapturedAt())); p.setBytes(6, artifact.getContent());
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT label,media_type,source_reference,captured_at,content FROM source_artifacts WHERE artifact_id=?")) {
                p.setObject(1, artifact.getArtifactId());
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalStateException("Artifact insert failed.");
                    SourceArtifact stored = read(artifact.getArtifactId(), r);
                    if (!stored.equals(artifact)) throw new IllegalArgumentException(
                            "Artifact ID already exists with different recorded values.");
                }
            }
            return null;
        });
    }
    @Override public Optional<SourceArtifact> findById(UUID id) {
        Objects.requireNonNull(id);
        return PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT label,media_type,source_reference,captured_at,content FROM source_artifacts WHERE artifact_id=?")) {
                p.setObject(1, id);
                try (ResultSet r = p.executeQuery()) { return r.next() ? Optional.of(read(id, r)) : Optional.empty(); }
            }
        });
    }
    private static SourceArtifact read(UUID id, ResultSet r) throws SQLException {
        return new SourceArtifact(id, r.getString(1), r.getString(2), r.getString(3),
                r.getTimestamp(4).toInstant(), r.getBytes(5));
    }
}
