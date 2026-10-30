package coupons.repository;

import coupons.serialization.LegacyObjectStreams;

import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class PostgresRepositorySupport {
    private PostgresRepositorySupport() {}

    @FunctionalInterface
    interface Work<T> { T run(Connection connection) throws Exception; }

    static <T> T transaction(PostgresConnectionFactory factory, Work<T> work) {
        try (Connection c = factory.open()) {
            boolean old = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T value = work.run(c);
                c.commit();
                c.setAutoCommit(old);
                return value;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof RuntimeException r) throw r;
                if (e instanceof SQLException s) throw new IllegalStateException(s.getMessage(), s);
                throw new IllegalStateException(e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("PostgreSQL operation failed.", e);
        }
    }

    static void requireActor(String actor, String label) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException(label + " cannot be blank.");
    }

    static byte[] serialize(Object value) {
        try (var out = new ByteArrayOutputStream(); var stream = LegacyObjectStreams.output(out)) {
            stream.writeObject(value);
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not serialize repository payload.", e); }
    }

    @SuppressWarnings("unchecked")
    static <T> T deserialize(byte[] bytes, Class<T> type) {
        try (var stream = LegacyObjectStreams.input(new ByteArrayInputStream(bytes))) {
            return (T) stream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Could not deserialize repository payload.", e);
        }
    }

    static Set<UUID> sourceIds(Connection c, String table, String idColumn, UUID id) throws SQLException {
        Set<UUID> ids = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement("SELECT artifact_id FROM " + table
                + " WHERE " + idColumn + "=?")) {
            p.setObject(1, id);
            try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add((UUID) r.getObject(1)); }
        }
        return ids;
    }
        static Set<UUID> sourceIds(Connection c, String table, String idColumn, UUID id, long revision) throws SQLException {
            Set<UUID> ids = new HashSet<>();
            try (PreparedStatement p = c.prepareStatement("SELECT artifact_id FROM " + table
                    + " WHERE " + idColumn + "=? AND revision=?")) {
                p.setObject(1, id); p.setLong(2, revision);
                try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add((UUID) r.getObject(1)); }
            }
            return ids;
        }

    static void validateSources(Connection c, Collection<UUID> ids) throws SQLException {
        for (UUID id : ids) {
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM source_artifacts WHERE artifact_id=?")) {
                p.setObject(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Source artifact does not exist: " + id);
                }
            }
        }
    }

    static void insertSources(Connection c, String table, String idColumn, UUID id,
            Collection<UUID> sources) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + table
                + "(" + idColumn + ", artifact_id) VALUES (?,?)")) {
            for (UUID source : sources) {
                p.setObject(1, id); p.setObject(2, source); p.addBatch();
            }
            p.executeBatch();
        }
    }

    static Instant instant(ResultSet r, String column) throws SQLException { return r.getTimestamp(column).toInstant(); }
}
