package coupons.repository;

import coupons.CouponTransaction;
import coupons.ClippedCoupon;
import coupons.RegisterReward;
import coupons.RegisterRewardRedemption;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL adapter for the complete {@link TransactionRepository} contract.
 *
 * <p>The adapter is opt-in and never opens the local serialized receipt store.
 * Nested receipt values are stored as immutable Java snapshots in bytea columns
 * while identity, lifecycle, revision, evidence, reward, and audit relationships
 * remain relational and constrained by PostgreSQL.
 *
 * <p>Receipt-linked promotion progress and local RR reservations are persisted
 * and validated in the same transaction using the existing receipt rules.
 */
public final class PostgresTransactionRepository implements TransactionRepository, AutoCloseable {
    private final PostgresConnectionFactory connections;
    private final Clock clock;

    public PostgresTransactionRepository(PostgresConnectionFactory connections) {
        this(connections, Clock.systemUTC());
    }

    public PostgresTransactionRepository(PostgresConnectionFactory connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.clock = Objects.requireNonNull(clock);
    }

    public static PostgresTransactionRepository fromEnvironment() {
        return new PostgresTransactionRepository(PostgresConnectionFactory.fromEnvironment());
    }

    public void applySchema() throws SQLException, IOException {
        try (Connection connection = connections.open()) {
            PostgresSchema.apply(connection);
        }
    }

    @Override
    public ReceiptWriteResult save(UUID id, CouponTransaction transaction, ReceiptMeta metadata,
            List<ReceiptEvidence> newEvidence, ReceiptWriteRequest request, ReceiptCreateCtx context) {
        return saveWithCoupons(id,transaction,metadata,newEvidence,request,context,List.of());
    }

    /** Receipt and clipped-coupon redemptions commit or roll back together. */
    public ReceiptWriteResult saveWithCoupons(UUID id,CouponTransaction transaction,ReceiptMeta metadata,
            List<ReceiptEvidence> newEvidence,ReceiptWriteRequest request,ReceiptCreateCtx context,List<UUID> couponIds) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(newEvidence);
        Objects.requireNonNull(request);
        Objects.requireNonNull(context);
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(transaction);
        List<ReceiptEvidence> evidence = EvidenceBatchValidator.validateAndCopy(id, newEvidence);
        ReceiptRecord candidate = new ReceiptRecord(id, ReceiptStatus.ACTIVE, 1,
                snapshot.toTransaction(), metadata, evidence.stream().map(ReceiptEvidence::getEvidenceId).toList());
        ReceiptValidator.validate(candidate);
        CreatePayload payload = new CreatePayload(id, snapshot, metadata, evidence, request, context);
        return inTransaction(connection -> {
            ReceiptWriteResult retry = matchingRequest(connection, request.requestId(), payload);
            if (retry != null) return retry;
            if (exists(connection, "receipt_current", "transaction_id", id)) {
                throw new IllegalArgumentException("Receipt ID already exists.");
            }
            var state = PostgresReceiptState.read(connection);
            PostgresReceiptState.rules(state).validateWrite(state, ReceiptRevSnap.from(candidate), evidence, null);
            ensureEvidenceAvailable(connection, evidence);
            validateRedemptions(connection, snapshot.registerRewardRedemptions(), id, SetMode.NEW);
            Instant committedAt = clock.instant();
            ReceiptRevSnap after = ReceiptRevSnap.from(candidate);
            ReceiptWriteResult result = new ReceiptWriteResult(request.requestId(), id, 1,
                    ReceiptChangeAction.CREATED, ReceiptStatus.ACTIVE, committedAt);
            ReceiptHistoryEntry history = new ReceiptHistoryEntry(UUID.randomUUID(), request,
                    result, null, after, context, null);
            var promotions = PostgresReceiptState.rules(state).receiptPromotions(state, after, null);
            insertReceipt(connection, candidate, snapshot);
            PostgresReceiptState.promotions(connection, promotions);
            insertEvidence(connection, evidence);
            insertRedemptions(connection, snapshot.registerRewardRedemptions());
            for(var couponId:couponIds.stream().sorted().toList()) {
                ClippedCoupon coupon;
                try(var p=connection.prepareStatement("SELECT payload FROM clipped_coupon_current WHERE coupon_id=? FOR UPDATE")){p.setObject(1,couponId);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Selected coupon no longer exists.");coupon=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);}}
                if(snapshot.appliedCoupons().stream().noneMatch(x->x.getApplicationId().equals(couponId)))throw new IllegalArgumentException("Selected coupon must have one receipt discount.");
                if(metadata==null || snapshot.observations().purchaseDate()==null)throw new IllegalArgumentException("Purchase date required to use clipped coupons.");
                PostgresClippedCouponRepository.redeem(connection,couponId,id,snapshot.observations().purchaseDate(),coupon.revision(),context.requestedBy());
            }
            insertHistory(connection, history);
            insertRequest(connection, request.requestId(), id, serialize(payload), result);
            return result;
        });
    }

    @Override
    public Optional<ReceiptRecord> findById(UUID id) {
        return findByStatus(id, ReceiptStatus.ACTIVE);
    }

    @Override
    public ReceiptWriteResult update(UUID id, long expectedRevision, CouponTransaction transaction,
            ReceiptMeta metadata, List<ReceiptEvidence> newEvidence, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(newEvidence);
        Objects.requireNonNull(request);
        Objects.requireNonNull(context);
        if (expectedRevision < 1) throw new IllegalArgumentException("Revision must be positive.");
        ReceiptSnapshot snapshot = ReceiptSnapshot.from(transaction);
        List<ReceiptEvidence> evidence = EvidenceBatchValidator.validateAndCopy(id, newEvidence);
        UpdatePayload payload = new UpdatePayload(id, expectedRevision, snapshot, metadata, evidence, request, context);
        return inTransaction(connection -> {
            ReceiptWriteResult retry = matchingRequest(connection, request.requestId(), payload);
            if (retry != null) return retry;
            Row current = requiredRow(connection, id);
            if (current.status != ReceiptStatus.ACTIVE) throw new IllegalArgumentException("Receipt is deleted.");
            if (current.revision != expectedRevision) throw new ConcurrentModificationException("Receipt revision changed.");
            ReceiptRecord before = current.record();
            List<UUID> allEvidence = appendEvidence(before.evidenceIds(), evidence);
            ReceiptRecord afterRecord = new ReceiptRecord(id, ReceiptStatus.ACTIVE, expectedRevision + 1,
                    snapshot.toTransaction(), metadata, allEvidence);
            ReceiptValidator.validate(afterRecord);
            var state = PostgresReceiptState.read(connection);
            PostgresReceiptState.rules(state).validateWrite(state, ReceiptRevSnap.from(afterRecord), evidence, ReceiptRevSnap.from(before));
            ensureUnchangedRedemptions(before, afterRecord);
            ensureEvidenceAvailable(connection, evidence);
            validateRedemptions(connection, snapshot.registerRewardRedemptions(), id, SetMode.UPDATE);
            Instant committedAt = clock.instant();
            ReceiptRevSnap beforeSnap = ReceiptRevSnap.from(before);
            ReceiptRevSnap afterSnap = ReceiptRevSnap.from(afterRecord);
            ReceiptWriteResult result = new ReceiptWriteResult(request.requestId(), id, expectedRevision + 1,
                    ReceiptChangeAction.UPDATED, ReceiptStatus.ACTIVE, committedAt);
            ReceiptHistoryEntry history = new ReceiptHistoryEntry(UUID.randomUUID(), request, result,
                    beforeSnap, afterSnap, null, context);
            var promotions = PostgresReceiptState.rules(state).receiptPromotions(state, afterSnap, beforeSnap);
            updateReceipt(connection, afterRecord, snapshot);
            PostgresReceiptState.promotions(connection, promotions);
            insertEvidence(connection, evidence);
            insertNewRedemptions(connection, before.transaction().getRegisterRewardRedemptions(),
                    snapshot.registerRewardRedemptions());
            insertHistory(connection, history);
            insertRequest(connection, request.requestId(), id, serialize(payload), result);
            return result;
        });
    }

    @Override
    public ReceiptWriteResult softDelete(UUID id, long expectedRevision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        Objects.requireNonNull(id); Objects.requireNonNull(request); Objects.requireNonNull(context);
        if (expectedRevision < 1) throw new IllegalArgumentException("Revision must be positive.");
        DeletePayload payload = new DeletePayload(id, expectedRevision, request, context);
        return inTransaction(connection -> changeStatus(connection, id, expectedRevision, request, context,
                ReceiptStatus.ACTIVE, ReceiptStatus.DELETED, ReceiptChangeAction.DELETED, payload));
    }

    @Override
    public ReceiptWriteResult restore(UUID id, long expectedRevision, ReceiptWriteRequest request,
            ReceiptChangeContext context) {
        Objects.requireNonNull(id); Objects.requireNonNull(request); Objects.requireNonNull(context);
        if (expectedRevision < 1) throw new IllegalArgumentException("Revision must be positive.");
        RestorePayload payload = new RestorePayload(id, expectedRevision, request, context);
        return inTransaction(connection -> changeStatus(connection, id, expectedRevision, request, context,
                ReceiptStatus.DELETED, ReceiptStatus.ACTIVE, ReceiptChangeAction.RESTORED, payload));
    }

    @Override
    public Optional<ReceiptRecord> findDeletedById(UUID id) {
        return findByStatus(id, ReceiptStatus.DELETED);
    }

    @Override
    public List<ReceiptRecord> findDeleted() {
        return findAllByStatus(ReceiptStatus.DELETED);
    }

    @Override
    public List<ReceiptRecord> findPossibleDuplicates(ReceiptMeta metadata) {
        Objects.requireNonNull(metadata);
        if (metadata.receiptNumber() == null && metadata.purchasedAt() == null) {
            throw new IllegalArgumentException("Receipt number or purchase date/time is required.");
        }
        return inTransaction(connection -> {
            List<ReceiptRecord> matches = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT transaction_id,status,revision,contents,metadata,evidence_ids FROM receipt_current ORDER BY transaction_id")) {
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Row row = Row.read(rows);
                        if (matches(metadata, row.metadata)) matches.add(row.record());
                    }
                }
            }
            return List.copyOf(matches);
        });
    }

    public void save(RegisterReward reward) {
        Objects.requireNonNull(reward);
        inTransaction(connection -> {
            if (reward.getEarnedReceiptId() != null
                    && !exists(connection, "receipt_current", "transaction_id", reward.getEarnedReceiptId())) {
                throw new IllegalArgumentException("Earning receipt does not exist.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO register_rewards(reward_id,earned_receipt_id,payload) VALUES(?,?,?)")) {
                statement.setObject(1, reward.getRewardId());
                setUuid(statement, 2, reward.getEarnedReceiptId());
                statement.setBytes(3, serialize(reward));
                statement.executeUpdate();
            } catch (SQLException failure) {
                throw sqlFailure(failure);
            }
            return null;
        });
    }

    public Optional<RegisterReward> findRewardById(UUID id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM register_rewards WHERE reward_id=?")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(deserialize(rows.getBytes(1), RegisterReward.class)) : Optional.empty();
                }
            }
        });
    }

    public Optional<RegisterRewardRedemption> findRedemptionById(UUID id) {
        return redemption("redemption_id", id);
    }

    public Optional<RegisterRewardRedemption> findRedemptionByRewardId(UUID id) {
        return redemption("reward_id", id);
    }

    public List<RegisterRewardRedemption> findRedemptionsByReceiptId(UUID id) {
        return inTransaction(connection -> {
            List<RegisterRewardRedemption> values = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM register_reward_redemptions WHERE receipt_id=? ORDER BY redemption_id")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(deserialize(rows.getBytes(1), RegisterRewardRedemption.class));
                }
            }
            return List.copyOf(values);
        });
    }

    public Optional<ReceiptHistoryEntry> findHistoryById(UUID id) {
        return history("history_id", id);
    }

    public Optional<ReceiptHistoryEntry> findHistoryByRequestId(UUID id) {
        return history("request_id", id);
    }

    public Optional<ReceiptHistoryEntry> findHistoryByRevision(UUID id, long revision) {
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive.");
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM receipt_history WHERE transaction_id=? AND revision=?")) {
                statement.setObject(1, id); statement.setLong(2, revision);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(deserialize(rows.getBytes(1), ReceiptHistoryEntry.class)) : Optional.empty();
                }
            }
        });
    }

    public List<ReceiptHistoryEntry> findHistoryByReceiptId(UUID id) {
        return inTransaction(connection -> {
            List<ReceiptHistoryEntry> values = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM receipt_history WHERE transaction_id=? ORDER BY revision")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(deserialize(rows.getBytes(1), ReceiptHistoryEntry.class));
                }
            }
            return List.copyOf(values);
        });
    }

    public Optional<ReceiptEvidence> findEvidenceById(UUID id) {
        return evidence("evidence_id", id);
    }

    public List<ReceiptEvidence> findEvidenceByReceiptId(UUID id) {
        return inTransaction(connection -> {
            List<ReceiptEvidence> values = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT evidence_id,transaction_id,media_type,content FROM receipt_evidence WHERE transaction_id=? ORDER BY evidence_id")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(new ReceiptEvidence((UUID) rows.getObject(1),
                            (UUID) rows.getObject(2), rows.getString(3), rows.getBytes(4)));
                }
            }
            return List.copyOf(values);
        });
    }

    @Override
    public void close() {
    }

    private ReceiptWriteResult changeStatus(Connection connection, UUID id, long expectedRevision,
            ReceiptWriteRequest request, ReceiptChangeContext context, ReceiptStatus from, ReceiptStatus to,
            ReceiptChangeAction action, Object payload) throws SQLException {
        ReceiptWriteResult retry = matchingRequest(connection, request.requestId(), payload);
        if (retry != null) return retry;
        Row current = requiredRow(connection, id);
        if (current.status != from) throw new IllegalArgumentException("Receipt lifecycle state is invalid.");
        if (current.revision != expectedRevision) throw new ConcurrentModificationException("Receipt revision changed.");
        ReceiptRecord before = current.record();
        ReceiptRecord after = new ReceiptRecord(id, to, expectedRevision + 1, before.transaction(),
                before.metadata(), before.evidenceIds());
        var state = PostgresReceiptState.read(connection);
        var rules = PostgresReceiptState.rules(state);
        rules.validateStoredReferences(state, ReceiptRevSnap.from(after), List.of(), ReceiptRevSnap.from(before));
        if (to == ReceiptStatus.DELETED) rules.validateDeletionDependencies(state, ReceiptRevSnap.from(before));
        Instant committedAt = clock.instant();
        ReceiptWriteResult result = new ReceiptWriteResult(request.requestId(), id, expectedRevision + 1,
                action, to, committedAt);
        ReceiptHistoryEntry history = new ReceiptHistoryEntry(UUID.randomUUID(), request, result,
                ReceiptRevSnap.from(before), ReceiptRevSnap.from(after), null, context);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE receipt_current SET status=?,revision=? WHERE transaction_id=? AND revision=?")) {
            statement.setString(1, to.name()); statement.setLong(2, expectedRevision + 1);
            statement.setObject(3, id); statement.setLong(4, expectedRevision);
            if (statement.executeUpdate() != 1) throw new ConcurrentModificationException("Receipt revision changed.");
        }
        insertHistory(connection, history);
        insertRequest(connection, request.requestId(), id, serialize(payload), result);
        return result;
    }

    private Optional<ReceiptRecord> findByStatus(UUID id, ReceiptStatus status) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT transaction_id,status,revision,contents,metadata,evidence_ids FROM receipt_current WHERE transaction_id=? AND status=?")) {
                statement.setObject(1, id); statement.setString(2, status.name());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(Row.read(rows).record()) : Optional.empty();
                }
            }
        });
    }

    private List<ReceiptRecord> findAllByStatus(ReceiptStatus status) {
        return inTransaction(connection -> {
            List<ReceiptRecord> values = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT transaction_id,status,revision,contents,metadata,evidence_ids FROM receipt_current WHERE status=? ORDER BY transaction_id")) {
                statement.setString(1, status.name());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) values.add(Row.read(rows).record());
                }
            }
            return List.copyOf(values);
        });
    }

    private Row requiredRow(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT transaction_id,status,revision,contents,metadata,evidence_ids FROM receipt_current WHERE transaction_id=? FOR UPDATE")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("Receipt does not exist.");
                return Row.read(rows);
            }
        }
    }

    private void insertReceipt(Connection connection, ReceiptRecord record, ReceiptSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO receipt_current(transaction_id,status,revision,contents,metadata,evidence_ids) VALUES(?,?,?,?,?,?)")) {
            statement.setObject(1, record.transactionId()); statement.setString(2, record.status().name());
            statement.setLong(3, record.revision()); statement.setBytes(4, serialize(snapshot));
            setBytes(statement, 5, serializeNullable(record.metadata()));
            statement.setBytes(6, serialize(record.evidenceIds())); statement.executeUpdate();
        }
    }

    private void updateReceipt(Connection connection, ReceiptRecord record, ReceiptSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE receipt_current SET status=?,revision=?,contents=?,metadata=?,evidence_ids=? WHERE transaction_id=?")) {
            statement.setString(1, record.status().name()); statement.setLong(2, record.revision());
            statement.setBytes(3, serialize(snapshot)); setBytes(statement, 4, serializeNullable(record.metadata()));
            statement.setBytes(5, serialize(record.evidenceIds())); statement.setObject(6, record.transactionId());
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Receipt does not exist.");
        }
    }

    private void insertEvidence(Connection connection, List<ReceiptEvidence> evidence) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO receipt_evidence(evidence_id,transaction_id,media_type,content) VALUES(?,?,?,?)")) {
            for (ReceiptEvidence artifact : evidence) {
                statement.setObject(1, artifact.getEvidenceId()); statement.setObject(2, artifact.getTransactionId());
                statement.setString(3, artifact.getMediaType()); statement.setBytes(4, artifact.getContent());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertRedemptions(Connection connection,
            List<RegisterRewardRedemption> redemptions) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO register_reward_redemptions(redemption_id,reward_id,receipt_id,payload) VALUES(?,?,?,?)")) {
            for (RegisterRewardRedemption redemption : redemptions) {
                statement.setObject(1, redemption.getRedemptionId());
                statement.setObject(2, redemption.getRewardId());
                statement.setObject(3, redemption.getRedeemedReceiptId());
                statement.setBytes(4, serialize(redemption));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertNewRedemptions(Connection connection,
            List<RegisterRewardRedemption> existing,
            List<RegisterRewardRedemption> submitted) throws SQLException {
        HashSet<UUID> existingIds = existing.stream()
                .map(RegisterRewardRedemption::getRedemptionId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        insertRedemptions(connection, submitted.stream()
                .filter(value -> !existingIds.contains(value.getRedemptionId()))
                .toList());
    }

    private void insertHistory(Connection connection, ReceiptHistoryEntry history) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO receipt_history(history_id,transaction_id,revision,request_id,payload) VALUES(?,?,?,?,?)")) {
            statement.setObject(1, history.historyId()); statement.setObject(2, history.after().transactionId());
            statement.setLong(3, history.after().revision()); statement.setObject(4, history.request().requestId());
            statement.setBytes(5, serialize(history)); statement.executeUpdate();
        }
    }

    private void insertRequest(Connection connection, UUID requestId, UUID transactionId, byte[] payload,
            ReceiptWriteResult result) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO receipt_write_requests(request_id,transaction_id,payload,result) VALUES(?,?,?,?)")) {
            statement.setObject(1, requestId); statement.setObject(2, transactionId);
            statement.setBytes(3, payload); statement.setBytes(4, serialize(result)); statement.executeUpdate();
        }
    }

    private ReceiptWriteResult matchingRequest(Connection connection, UUID requestId, Object payload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload,result FROM receipt_write_requests WHERE request_id=? FOR UPDATE")) {
            statement.setObject(1, requestId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                if (!Objects.equals(deserialize(rows.getBytes(1), Object.class), payload)) {
                    throw new IllegalArgumentException("Request ID was used with different contents.");
                }
                return deserialize(rows.getBytes(2), ReceiptWriteResult.class);
            }
        }
    }

    private void ensureEvidenceAvailable(Connection connection, List<ReceiptEvidence> evidence) throws SQLException {
        for (ReceiptEvidence artifact : evidence) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT transaction_id,media_type,content FROM receipt_evidence WHERE evidence_id=?")) {
                statement.setObject(1, artifact.getEvidenceId());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        ReceiptEvidence existing = new ReceiptEvidence(artifact.getEvidenceId(),
                                (UUID) rows.getObject(1), rows.getString(2), rows.getBytes(3));
                        if (!existing.equals(artifact)) throw new IllegalArgumentException("Evidence ID already exists with different contents.");
                        throw new IllegalArgumentException("Evidence ID already exists.");
                    }
                }
            }
        }
    }

    private static boolean exists(Connection connection, String table, String column, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + "=?")) {
            statement.setObject(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void validateRedemptions(Connection connection, List<RegisterRewardRedemption> redemptions,
            UUID receiptId, SetMode mode) throws SQLException {
        for (RegisterRewardRedemption redemption : redemptions) {
            if (!receiptId.equals(redemption.getRedeemedReceiptId())) {
                throw new IllegalArgumentException("Redemption references a different receipt.");
            }
            if (mode == SetMode.UPDATE && redemptionExistsOnReceipt(connection, redemption)) continue;
            try (PreparedStatement reward = connection.prepareStatement(
                    "SELECT 1 FROM register_rewards WHERE reward_id=?")) {
                reward.setObject(1, redemption.getRewardId());
                try (ResultSet rows = reward.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Reward does not exist.");
                }
            }
            try (PreparedStatement used = connection.prepareStatement(
                    "SELECT 1 FROM register_reward_redemptions WHERE reward_id=?")) {
                used.setObject(1, redemption.getRewardId());
                try (ResultSet rows = used.executeQuery()) {
                    if (rows.next()) throw new IllegalArgumentException("Reward has already been redeemed.");
                }
            }
        }
    }

    private boolean redemptionExistsOnReceipt(Connection connection, RegisterRewardRedemption redemption) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload FROM register_reward_redemptions WHERE redemption_id=?")) {
            statement.setObject(1, redemption.getRedemptionId());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && deserialize(rows.getBytes(1), RegisterRewardRedemption.class).equals(redemption);
            }
        }
    }

    private void ensureUnchangedRedemptions(ReceiptRecord before, ReceiptRecord after) {
        List<RegisterRewardRedemption> oldValues = before.transaction().getRegisterRewardRedemptions();
        List<RegisterRewardRedemption> newValues = after.transaction().getRegisterRewardRedemptions();
        if (!new HashSet<>(newValues).containsAll(oldValues)) {
            throw new IllegalArgumentException("Existing redemptions cannot be removed or altered.");
        }
    }

    private Optional<RegisterRewardRedemption> redemption(String column, UUID id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM register_reward_redemptions WHERE " + column + "=?")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(deserialize(rows.getBytes(1), RegisterRewardRedemption.class)) : Optional.empty();
                }
            }
        });
    }

    private Optional<ReceiptHistoryEntry> history(String column, UUID id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT payload FROM receipt_history WHERE " + column + "=?")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(deserialize(rows.getBytes(1), ReceiptHistoryEntry.class)) : Optional.empty();
                }
            }
        });
    }

    private Optional<ReceiptEvidence> evidence(String column, UUID id) {
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT evidence_id,transaction_id,media_type,content FROM receipt_evidence WHERE " + column + "=?")) {
                statement.setObject(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(new ReceiptEvidence((UUID) rows.getObject(1),
                            (UUID) rows.getObject(2), rows.getString(3), rows.getBytes(4))) : Optional.empty();
                }
            }
        });
    }

    private static List<UUID> appendEvidence(List<UUID> existing, List<ReceiptEvidence> additions) {
        List<UUID> all = new ArrayList<>(existing);
        all.addAll(additions.stream().map(ReceiptEvidence::getEvidenceId).toList());
        return List.copyOf(all);
    }

    private static boolean matches(ReceiptMeta expected, ReceiptMeta candidate) {
        if (candidate == null || !expected.retailer().equals(candidate.retailer())) return false;
        boolean identity = expected.receiptNumber() != null && expected.receiptNumber().equals(candidate.receiptNumber());
        boolean time = expected.purchasedAt() != null && expected.purchasedAt().equals(candidate.purchasedAt());
        if (!identity && !time) return false;
        return expected.storeReference() == null || candidate.storeReference() == null
                || expected.storeReference().equals(candidate.storeReference());
    }

    /** Translate original retry inputs without issuing replacement write IDs or timestamps. */
    static Object postgresInput(InMemoryStore.WriteInput input) {
        return switch (input) {
            case InMemoryStore.CreateInput i -> new CreatePayload(i.transactionId(),i.contents(),i.metadata(),i.evidence(),i.request(),i.context());
            case InMemoryStore.UpdateInput i -> new UpdatePayload(i.transactionId(),i.expectedRevision(),i.contents(),i.metadata(),i.evidence(),i.request(),i.context());
            case InMemoryStore.DeleteInput i -> new DeletePayload(i.transactionId(),i.expectedRevision(),i.request(),i.context());
            case InMemoryStore.RestoreInput i -> new RestorePayload(i.transactionId(),i.expectedRevision(),i.request(),i.context());
        };
    }
    static InMemoryStore.WriteInput localInput(Object input) {
        if (input instanceof CreatePayload i) return new InMemoryStore.CreateInput(i.id(),i.snapshot(),i.metadata(),i.evidence(),i.request(),i.context());
        if (input instanceof UpdatePayload i) return new InMemoryStore.UpdateInput(i.id(),i.expectedRevision(),i.snapshot(),i.metadata(),i.evidence(),i.request(),i.context());
        if (input instanceof DeletePayload i) return new InMemoryStore.DeleteInput(i.id(),i.expectedRevision(),i.request(),i.context());
        if (input instanceof RestorePayload i) return new InMemoryStore.RestoreInput(i.id(),i.expectedRevision(),i.request(),i.context());
        throw new IllegalArgumentException("Unrecognized receipt retry payload; migration cannot continue.");
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, Types.OTHER); else statement.setObject(index, value);
    }

    private static void setBytes(PreparedStatement statement, int index, byte[] value) throws SQLException {
        if (value == null) statement.setNull(index, Types.BINARY); else statement.setBytes(index, value);
    }

    private static byte[] serialize(Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(value);
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not serialize PostgreSQL value.", failure);
        }
    }

    private static byte[] serializeNullable(Object value) {
        return value == null ? null : serialize(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes, Class<T> type) {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            return type.cast(value);
        } catch (IOException | ClassNotFoundException failure) {
            throw new IllegalStateException("Could not deserialize PostgreSQL value.", failure);
        }
    }

    private static RuntimeException sqlFailure(SQLException failure) {
        return new IllegalStateException("PostgreSQL repository operation failed.", failure);
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            boolean original = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                PostgresReceiptState.lock(connection);
                T result = work.run(connection);
                connection.commit();
                connection.setAutoCommit(original);
                return result;
            } catch (RuntimeException | SQLException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw sqlFailure((SQLException) failure);
            }
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private enum SetMode { NEW, UPDATE }

    private record CreatePayload(UUID id, ReceiptSnapshot snapshot, ReceiptMeta metadata,
            List<ReceiptEvidence> evidence, ReceiptWriteRequest request, ReceiptCreateCtx context)
            implements java.io.Serializable { }
    private record UpdatePayload(UUID id, long expectedRevision, ReceiptSnapshot snapshot,
            ReceiptMeta metadata, List<ReceiptEvidence> evidence, ReceiptWriteRequest request,
            ReceiptChangeContext context) implements java.io.Serializable { }
    private record DeletePayload(UUID id, long expectedRevision, ReceiptWriteRequest request,
            ReceiptChangeContext context) implements java.io.Serializable { }
    private record RestorePayload(UUID id, long expectedRevision, ReceiptWriteRequest request,
            ReceiptChangeContext context) implements java.io.Serializable { }

    private record Row(UUID id, ReceiptStatus status, long revision, ReceiptSnapshot snapshot,
            ReceiptMeta metadata, List<UUID> evidenceIds) {
        static Row read(ResultSet rows) throws SQLException {
            return new Row((UUID) rows.getObject(1), ReceiptStatus.valueOf(rows.getString(2)),
                    rows.getLong(3), deserialize(rows.getBytes(4), ReceiptSnapshot.class),
                    rows.getBytes(5) == null ? null : deserialize(rows.getBytes(5), ReceiptMeta.class),
                    deserialize(rows.getBytes(6), List.class));
        }

        ReceiptRecord record() {
            return new ReceiptRecord(id, status, revision, snapshot.toTransaction(), metadata, evidenceIds);
        }
    }
}
