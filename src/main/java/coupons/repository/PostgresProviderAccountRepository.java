package coupons.repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL adapter for provider-account observations and external movements. */
public final class PostgresProviderAccountRepository implements ProviderAccountRepository {
    private final PostgresConnectionFactory connections;

    public PostgresProviderAccountRepository(PostgresConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override public void saveOpening(AccountOpening value) {
        Objects.requireNonNull(value);
        PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO provider_account_openings
                    (observation_id,provider,observed_date,original_units,usd_value,month_start,
                     valuation_basis,source_note,recorded_at,payload) VALUES (?,?,?,?,?,?,?,?,?,?)
                    """)) {
                p.setObject(1, value.observationId()); p.setString(2, value.provider());
                p.setObject(3, value.date()); p.setBigDecimal(4, value.originalUnits());
                p.setBigDecimal(5, value.usdValue()); p.setBoolean(6, value.monthStart());
                p.setString(7, value.valuationBasis()); p.setString(8, value.sourceNote());
                p.setTimestamp(9, Timestamp.from(value.recordedAt()));
                p.setBytes(10, PostgresRepositorySupport.serialize(value)); p.executeUpdate();
            } catch (SQLException e) { duplicate(e, "Opening observation"); }
            return null;
        });
    }

    @Override public void saveAdjustment(AccountAdjustment value) {
        Objects.requireNonNull(value);
        PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO provider_account_adjustments
                    (event_id,provider,event_date,unit_change,usd_change,reason,recorded_by,recorded_at,payload)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """)) {
                p.setObject(1, value.eventId()); p.setString(2, value.provider());
                p.setObject(3, value.date()); p.setBigDecimal(4, value.unitChange());
                p.setBigDecimal(5, value.usdChange()); p.setString(6, value.reason());
                p.setString(7, value.recordedBy()); p.setTimestamp(8, Timestamp.from(value.recordedAt()));
                p.setBytes(9, PostgresRepositorySupport.serialize(value)); p.executeUpdate();
            } catch (SQLException e) { duplicate(e, "Adjustment"); }
            return null;
        });
    }

    @Override public void saveRedemption(ProviderRedemption value) {
        Objects.requireNonNull(value);
        PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement("""
                    INSERT INTO provider_redemptions_current
                    (redemption_id,provider,revision,status,payload,recorded_at) VALUES (?,?,?,?,?,?)
                    """)) {
                p.setObject(1, value.redemptionId()); p.setString(2, value.provider());
                p.setLong(3, 1); p.setString(4, value.status().name());
                p.setBytes(5, PostgresRepositorySupport.serialize(value));
                p.setTimestamp(6, Timestamp.from(Instant.now())); p.executeUpdate();
            } catch (SQLException e) { duplicate(e, "Redemption"); }
            insertRedemptionHistory(c, value, 1);
            return null;
        });
    }

    @Override public Optional<AccountOpening> findOpening(UUID id) {
        Objects.requireNonNull(id);
        return one("SELECT payload FROM provider_account_openings WHERE observation_id=?", id,
                AccountOpening.class);
    }
    @Override public Optional<AccountAdjustment> findAdjustment(UUID id) {
        Objects.requireNonNull(id);
        return one("SELECT payload FROM provider_account_adjustments WHERE event_id=?", id,
                AccountAdjustment.class);
    }
    @Override public Optional<ProviderRedemption> findRedemption(UUID id) {
        Objects.requireNonNull(id);
        return one("SELECT payload FROM provider_redemptions_current WHERE redemption_id=?", id,
                ProviderRedemption.class);
    }
    @Override public Optional<ProviderRedemption> findRedemptionRevision(UUID id, long revision) {
        Objects.requireNonNull(id);
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive.");
        return one("SELECT payload FROM provider_redemptions_history WHERE redemption_id=? AND revision=?",
                id, revision, ProviderRedemption.class);
    }

    @Override public List<AccountOpening> findOpenings(String provider) {
        return list("SELECT payload FROM provider_account_openings WHERE provider=? ORDER BY source_sequence NULLS LAST, recorded_at, observation_id",
                provider, AccountOpening.class);
    }
    @Override public List<AccountAdjustment> findAdjustments(String provider) {
        return list("SELECT payload FROM provider_account_adjustments WHERE provider=? ORDER BY source_sequence NULLS LAST, recorded_at, event_id",
                provider, AccountAdjustment.class);
    }
    @Override public List<ProviderRedemption> findRedemptionHistory(String provider) {
        return list("SELECT payload FROM provider_redemptions_history WHERE provider=? ORDER BY source_sequence NULLS LAST, recorded_at, redemption_id, revision",
                provider, ProviderRedemption.class);
    }
    @Override public AccountBalanceState balanceState(String provider) {
        Objects.requireNonNull(provider);
        if (provider.isBlank()) throw new IllegalArgumentException("Provider cannot be blank.");
        return new AccountBalanceState(findOpenings(provider), findAdjustments(provider),
                findRedemptionHistory(provider));
    }

    @Override public ProviderRedemption updateRedemption(UUID id, long expectedRevision,
            ProviderRedemption next) {
        Objects.requireNonNull(id); Objects.requireNonNull(next);
        if (!id.equals(next.redemptionId())) throw new IllegalArgumentException("Redemption ID cannot change.");
        if (expectedRevision < 1) throw new IllegalArgumentException("Revision must be positive.");
        return PostgresRepositorySupport.transaction(connections, c -> {
            long stored;
            String provider;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT revision,provider FROM provider_redemptions_current WHERE redemption_id=? FOR UPDATE")) {
                p.setObject(1, id);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Redemption does not exist.");
                    stored = r.getLong(1);
                    provider = r.getString(2);
                }
            }
            if (stored != expectedRevision)
                throw new ConcurrentModificationException("Redemption revision changed.");
            if (!provider.equals(next.provider()))
                throw new IllegalArgumentException("Redemption provider cannot change.");
            long revision = expectedRevision + 1;
            try (PreparedStatement p = c.prepareStatement("""
                    UPDATE provider_redemptions_current SET provider=?,revision=?,status=?,payload=?,recorded_at=?
                    WHERE redemption_id=?
                    """)) {
                p.setString(1, next.provider()); p.setLong(2, revision);
                p.setString(3, next.status().name());
                p.setBytes(4, PostgresRepositorySupport.serialize(next));
                p.setTimestamp(5, Timestamp.from(Instant.now())); p.setObject(6, id);
                p.executeUpdate();
            }
            insertRedemptionHistory(c, next, revision);
            return next;
        });
    }

    private static void insertRedemptionHistory(Connection c, ProviderRedemption value, long revision)
            throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                INSERT INTO provider_redemptions_history
                (redemption_id,revision,provider,status,payload,recorded_at) VALUES (?,?,?,?,?,?)
                """)) {
            p.setObject(1, value.redemptionId()); p.setLong(2, revision);
            p.setString(3, value.provider()); p.setString(4, value.status().name());
            p.setBytes(5, PostgresRepositorySupport.serialize(value));
            p.setTimestamp(6, Timestamp.from(Instant.now())); p.executeUpdate();
        }
    }

    private static void duplicate(SQLException e, String kind) {
        if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException(kind + " ID already exists.", e);
        throw new IllegalStateException(e.getMessage(), e);
    }

    private <T> Optional<T> one(String sql, UUID id, Class<T> type) {
        return PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setObject(1, id);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? Optional.of(PostgresRepositorySupport.deserialize(r.getBytes(1), type))
                            : Optional.empty();
                }
            }
        });
    }
    private Optional<ProviderRedemption> one(String sql, UUID id, long revision,
            Class<ProviderRedemption> type) {
        return PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setObject(1, id); p.setLong(2, revision);
                try (ResultSet r = p.executeQuery()) {
                    return r.next() ? Optional.of(PostgresRepositorySupport.deserialize(r.getBytes(1), type))
                            : Optional.empty();
                }
            }
        });
    }
    private <T> List<T> list(String sql, String provider, Class<T> type) {
        Objects.requireNonNull(provider);
        if (provider.isBlank()) throw new IllegalArgumentException("Provider cannot be blank.");
        return PostgresRepositorySupport.transaction(connections, c -> {
            List<T> out = new ArrayList<>();
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1, provider);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) out.add(PostgresRepositorySupport.deserialize(r.getBytes(1), type));
                }
            }
            return List.copyOf(out);
        });
    }
}
