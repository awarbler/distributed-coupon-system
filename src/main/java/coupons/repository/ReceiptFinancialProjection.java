package coupons.repository;

import coupons.promotions.BenefitKind;
import coupons.receipts.CouponTransaction;
import coupons.promotions.PromotionType;
import coupons.receipts.TransactionCalculator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.*;

/** Exact receipt revision projections, integrated with normal schema and receipt writes. */
public final class ReceiptFinancialProjection {
    private static final int VERSION = 1;
    private static final String FORMULA = "existing-transaction-calculator-v1";
    private ReceiptFinancialProjection() {}

    /** Explicit operator entry point. All settings are test-specific; no production fallback. */
    public static void main(String[] args) throws Exception {
        if (!(args.length == 2 && args[0].equals("install"))
                && !(args.length == 4 && args[0].equals("backfill"))) {
            throw new IllegalArgumentException("Use: install <private-schema> OR backfill <private-schema> <run-uuid> <max-new-revisions>");
        }
        var target = new DisposablePostgresTarget(System.getenv("COUPON_POSTGRES_TEST_HOST"),
                Integer.parseInt(System.getenv("COUPON_POSTGRES_TEST_PORT")),
                System.getenv("COUPON_POSTGRES_TEST_MARKER"), System.getenv("COUPON_POSTGRES_TEST_USER"), args[1]);
        var factory = new PostgresConnectionFactory(System.getenv("COUPON_POSTGRES_TEST_URL"),
                System.getenv("COUPON_POSTGRES_TEST_USER"), System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        // Force the exact schema in the URL rather than accepting arbitrary search_path options.
        // The caller supplies the same schema using currentSchema in the test URL.
        if (args[0].equals("install")) install(factory, target);
        else System.out.println(backfill(factory, target, UUID.fromString(args[2]), Integer.parseInt(args[3])));
    }

    /** Additional target verification for disposable operator commands and tests. */
    public static void install(PostgresConnectionFactory factory, DisposablePostgresTarget target) throws Exception {
        try (var connection = target.open(factory)) {
            target.verifySchema(connection);
            connection.setAutoCommit(false);
            try { install(connection); connection.commit(); }
            catch (Exception failure) { connection.rollback(); throw failure; }
        }
    }

    /** Runs within the normal migration transaction; never opens another connection. */
    static void install(Connection connection) throws SQLException, IOException {
        PostgresReceiptState.lock(connection);
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT to_regclass('receipt_financial_projection_config')")) {
            rows.next();
            if (rows.getString(1) != null) { verifyInstalled(connection); return; }
        }
        try (var resource = ReceiptFinancialProjection.class.getResourceAsStream("/db/migration/V12__experimental_receipt_financial_projections.sql")) {
            if (resource == null) throw new IOException("Missing receipt financial migration.");
            try (var statement = connection.createStatement()) { statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8)); }
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO receipt_financial_projection_config VALUES(true,'APPLICATION',1)");
            statement.executeUpdate("INSERT INTO schema_version(version,description) VALUES(12,'receipt financial projections')");
        }
    }

    static void verifyInstalled(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT projection_version FROM receipt_financial_projection_config WHERE singleton=true")) {
            if (!rows.next() || VERSION != rows.getInt(1)) throw new IllegalStateException("Receipt projection schema is missing or incompatible.");
        }
    }

    static void verifyInstalled(Connection connection, DisposablePostgresTarget target) throws SQLException {
        target.verifySchema(connection);
        verifyInstalled(connection);
    }

    static void append(Connection connection, UUID id, long revision) throws SQLException {
        append(connection, null, id, revision);
    }

    /** Called inside the same transaction that inserted the authoritative audit row. */
    static void append(Connection connection, DisposablePostgresTarget target, UUID id, long revision) throws SQLException {
        if (target == null) verifyInstalled(connection); else verifyInstalled(connection, target);
        byte[] bytes;
        try (var query = connection.prepareStatement("SELECT payload FROM receipt_history WHERE transaction_id=? AND revision=?")) {
            query.setObject(1, id); query.setLong(2, revision);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Projection requires its authoritative audit revision.");
                bytes = rows.getBytes(1);
            }
        }
        var history = deserialize(bytes, ReceiptHistoryEntry.class);
        if (!id.equals(history.after().transactionId()) || revision != history.after().revision()) {
            throw new IllegalStateException("Audit identity does not match its payload.");
        }
        var records = records(history.after(), hash(bytes));
        boolean exists;
        try (var query = connection.prepareStatement("SELECT source_hash,projection_version FROM receipt_financial_revisions WHERE transaction_id=? AND revision=?")) {
            query.setObject(1, id); query.setLong(2, revision);
            try (var rows = query.executeQuery()) {
                exists = rows.next();
                if (exists && (!hash(bytes).equals(rows.getString(1)) || rows.getInt(2) != VERSION)) {
                    throw new IllegalStateException("Financial projection source/version conflict: " + id + " revision " + revision);
                }
            }
        }
        for (var row : records) {
            if (exists) compare(connection, row);
            else insert(connection, row);
        }
        // Detect extra rows as well as missing/changed records on an idempotent retry.
        for (String table : List.of("receipt_financial_revisions","receipt_purchase_lines",
                "receipt_coupon_applications","receipt_coupon_lines","receipt_coupon_offers","receipt_payments",
                "receipt_reward_redemptions","receipt_promotion_terms","receipt_earning_benefits","receipt_financial_amounts")) {
            long expected = records.stream().filter(r -> r.table().equals(table)).count();
            try (var query = connection.prepareStatement("SELECT count(*) FROM " + table + " WHERE transaction_id=? AND revision=?")) {
                query.setObject(1, id); query.setLong(2, revision);
                try (var rows = query.executeQuery()) { rows.next(); if (rows.getLong(1) != expected) throw new IllegalStateException("Projection row-count mismatch: " + table); }
            }
        }
    }

    public record BackfillResult(UUID runId, int totalRevisions, int projectedRevisions, boolean complete) {}
    private record Source(UUID id, long revision, String hash) {}

    /** Explicit, restartable historical work; each revision is committed atomically. */
    public static BackfillResult backfill(PostgresConnectionFactory factory, DisposablePostgresTarget target,
            UUID runId, int maximumRevisions) throws Exception {
        Objects.requireNonNull(runId);
        if (maximumRevisions < 0) throw new IllegalArgumentException("Revision limit cannot be negative.");
        List<Source> sources;
        String manifest;
        try (var connection = target.open(factory)) {
            verifyInstalled(connection, target);
            connection.setAutoCommit(false);
            PostgresReceiptState.lock(connection);
            sources = sources(connection); manifest = manifest(sources);
            try (var query = connection.prepareStatement("SELECT manifest_hash,total_revisions FROM receipt_financial_backfill_runs WHERE run_id=? FOR UPDATE")) {
                query.setObject(1, runId);
                try (var rows = query.executeQuery()) {
                    if (rows.next() && (!manifest.equals(rows.getString(1)) || sources.size() != rows.getLong(2))) {
                        throw new IllegalStateException("Backfill source manifest changed; refusing to overwrite its history.");
                    }
                }
            }
            try (var statement = connection.prepareStatement("INSERT INTO receipt_financial_backfill_runs VALUES(?,?,?,0,false) ON CONFLICT DO NOTHING")) {
                statement.setObject(1, runId); statement.setString(2, manifest); statement.setInt(3, sources.size()); statement.executeUpdate();
            }
            connection.commit();
        }
        int processed = 0;
        // Reverify all rows, even on a completed retry; max limits work, not source identity checks.
        for (var source : sources) {
            try (var connection = target.open(factory)) {
                connection.setAutoCommit(false);
                try {
                    PostgresReceiptState.lock(connection);
                    verifyManifest(connection, manifest);
                    boolean existing;
                    try (var query = connection.prepareStatement("SELECT 1 FROM receipt_financial_revisions WHERE transaction_id=? AND revision=?")) {
                        query.setObject(1, source.id()); query.setLong(2, source.revision());
                        try (var rows = query.executeQuery()) { existing = rows.next(); }
                    }
                    if (existing || processed < maximumRevisions) {
                        append(connection, target, source.id(), source.revision());
                        if (!existing) processed++;
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
        }
        try (var connection = target.open(factory)) {
            connection.setAutoCommit(false);
            try {
                verifyInstalled(connection, target); PostgresReceiptState.lock(connection); verifyManifest(connection, manifest);
                int projected = 0;
                for (var source : sources) {
                    try (var query = connection.prepareStatement("SELECT source_hash FROM receipt_financial_revisions WHERE transaction_id=? AND revision=?")) {
                        query.setObject(1, source.id()); query.setLong(2, source.revision());
                        try (var rows = query.executeQuery()) {
                            if (rows.next()) {
                                if (!source.hash().equals(rows.getString(1))) throw new IllegalStateException("Backfill projection hash mismatch.");
                                // Validate complete contents before marking the run complete.
                                append(connection, target, source.id(), source.revision()); projected++;
                            }
                        }
                    }
                }
                boolean complete = projected == sources.size();
                try (var statement = connection.prepareStatement("UPDATE receipt_financial_backfill_runs SET projected_revisions=?,complete=? WHERE run_id=?")) {
                    statement.setInt(1, projected); statement.setBoolean(2, complete); statement.setObject(3, runId); statement.executeUpdate();
                }
                connection.commit(); return new BackfillResult(runId, sources.size(), projected, complete);
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
    }

    static void appendAll(Connection connection) throws SQLException { appendAll(connection, null); }

    static void appendAll(Connection connection, DisposablePostgresTarget target) throws SQLException {
        if (target == null) verifyInstalled(connection); else verifyInstalled(connection, target);
        for (var source : sources(connection)) append(connection, target, source.id(), source.revision());
    }

    private static List<Source> sources(Connection connection) throws SQLException {
        var sources = new ArrayList<Source>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT transaction_id,revision,payload FROM receipt_history ORDER BY transaction_id,revision")) {
            while (rows.next()) sources.add(new Source((UUID) rows.getObject(1), rows.getLong(2), hash(rows.getBytes(3))));
        }
        return sources;
    }
    private static String manifest(List<Source> sources) { return hash(sources.toString().getBytes(StandardCharsets.UTF_8)); }
    private static void verifyManifest(Connection connection, String expected) throws SQLException {
        if (!expected.equals(manifest(sources(connection)))) throw new IllegalStateException("Backfill source manifest changed.");
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Row(String table, LinkedHashMap<String,Object> values) {}
    private static Row row(String table, ReceiptRevSnap revision, Object... fields) {
        var values = new LinkedHashMap<String,Object>();
        values.put("transaction_id", revision.transactionId()); values.put("revision", revision.revision());
        for (int i = 0; i < fields.length; i += 2) values.put((String) fields[i], fields[i+1]);
        return new Row(table, values);
    }
    private static void decimal(Row row, String column, BigDecimal value) {
        if (value != null && (value.scale() > 16383 || (long)value.precision() - value.scale() > 131072)) {
            throw new IllegalArgumentException("Decimal exceeds exact PostgreSQL NUMERIC limits: " + column);
        }
        row.values().put(column, value); row.values().put(column+"_scale", value == null ? null : value.scale());
    }
    private static BigDecimal legacy(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite legacy amount cannot be projected.");
        return BigDecimal.valueOf(value);
    }
    private static void insert(Connection connection, Row row) throws SQLException {
        String names = String.join(",", row.values().keySet());
        try (var statement = connection.prepareStatement("INSERT INTO " + row.table() + "(" + names + ") VALUES(" + String.join(",", Collections.nCopies(row.values().size(), "?")) + ")")) {
            int i = 1;
            for (Object value : row.values().values()) {
                if (value instanceof BigDecimal amount) statement.setBigDecimal(i++, amount);
                else statement.setObject(i++, value);
            }
            statement.executeUpdate();
        }
    }
    private static void compare(Connection connection, Row expected) throws SQLException {
        var keys = new LinkedHashMap<String,Object>();
        for (var entry : expected.values().entrySet()) {
            if (Set.of("transaction_id","revision","line_item_id","application_id","payment_id","redemption_id","source_position","earning_id","benefit_role","source_path","field_name").contains(entry.getKey())) keys.put(entry.getKey(), entry.getValue());
        }
        String predicate = String.join(" AND ", keys.keySet().stream().map(k -> k+"=?").toList());
        try (var query = connection.prepareStatement("SELECT * FROM " + expected.table() + " WHERE " + predicate)) {
            int index = 1; for (Object value : keys.values()) query.setObject(index++, value);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Missing financial projection row: " + expected.table());
                for (var entry : expected.values().entrySet()) {
                    Object actual = rows.getObject(entry.getKey()), value = entry.getValue();
                    boolean same;
                    if (value instanceof byte[] bytes) same = Objects.equals(deserialize(bytes, Object.class), deserialize((byte[]) actual, Object.class));
                    else if (value instanceof BigDecimal amount) same = actual instanceof BigDecimal number && amount.compareTo(number)==0;
                    else same = Objects.equals(value, actual);
                    if (!same) throw new IllegalStateException("Financial projection mismatch: " + expected.table()+"."+entry.getKey());
                }
                if (rows.next()) throw new IllegalStateException("Duplicate financial projection row.");
            }
        }
    }

    private static List<Row> records(ReceiptRevSnap rev, String sourceHash) {
        var snapshot = rev.contents();
        var rows = new ArrayList<Row>();
        var root = row("receipt_financial_revisions", rev, "status",rev.status().name(), "projection_version",VERSION,
                "source_hash",sourceHash, "formula_version",FORMULA, "source_context",serialize(rev), "calculation_note",null);
        decimal(root,"tax_amount",snapshot.receiptTotals().taxAmount());
        decimal(root,"retailer_reported_total",snapshot.receiptTotals().finalTotal());
        CouponTransaction transaction = null;
        try { transaction = snapshot.toTransaction(); }
        catch (IllegalArgumentException | NullPointerException unavailable) { root.values().put("calculation_note", "Stored source cannot be reconstructed by the current domain model."); }
        var calculator = new TransactionCalculator();
        BigDecimal expected = transaction == null ? null : calculator.calculateExpectedCheckoutAmount(transaction);
        decimal(root,"expected_total",expected);
        decimal(root,"difference",expected == null ? null : snapshot.receiptTotals().finalTotal().subtract(expected));
        root.values().put("calculation_status",expected == null ? "UNAVAILABLE" : "CALCULATED"); rows.add(root);
        int position = 0;
        for (var item : snapshot.purchaseItems()) {
            var line = row("receipt_purchase_lines",rev,"line_item_id",item.lineItemId(),"source_position",position++,"product_name",item.productName(),"product_code",item.productCode(),"quantity",item.quantity(),"source_context",serialize(item));
            decimal(line,"regular_unit_price",item.regularUnitPrice()); decimal(line,"purchase_unit_price",item.saleUnitPrice()); rows.add(line);
        }
        position = 0;
        for (var coupon : snapshot.appliedCoupons()) {
            var line = row("receipt_coupon_applications",rev,"application_id",coupon.getApplicationId(),"source_position",position++,"printed_label",coupon.getPrintedLabel(),"required_quantity",coupon.getRequiredQuantity(),"whole_receipt",coupon.isWholeReceipt(),"source_context",serialize(coupon));
            decimal(line,"applied_amount",coupon.getAppliedAmount()); rows.add(line);
            int n = 0;
            for (UUID id : coupon.getLineItemIds()) rows.add(row("receipt_coupon_lines",rev,"application_id",coupon.getApplicationId(),"line_item_id",id,"source_position",n++,"qualifying_quantity",coupon.getQualifyingQuantities().get(id)));
        }
        position = 0;
        for (var coupon : snapshot.coupons()) {
            var line = row("receipt_coupon_offers",rev,"source_position",position++,"description",coupon.description(),"coupon_type",coupon.couponType().name(),"required_quantity",coupon.requiredQuantity(),"expiration_date",coupon.expirationDate()==null?null:java.sql.Date.valueOf(coupon.expirationDate()),"source_context",serialize(coupon));
            decimal(line,"discount_amount",coupon.discountAmount()); rows.add(line);
        }
        position = 0;
        for (var payment : snapshot.payments()) {
            var line = row("receipt_payments",rev,"payment_id",payment.paymentId(),"source_position",position++,"payment_type",payment.paymentType().name(),"account_name",payment.accountName(),"notes",payment.notes(),"source_context",serialize(payment));
            decimal(line,"amount",payment.amount()); rows.add(line);
        }
        position = 0;
        for (var reward : snapshot.registerRewardRedemptions()) {
            var line = row("receipt_reward_redemptions",rev,"redemption_id",reward.getRedemptionId(),"reward_id",reward.getRewardId(),"source_position",position++,"source_context",serialize(reward));
            decimal(line,"amount",reward.getRedeemedAmount()); rows.add(line);
        }
        position = 0;
        for (var promotion : snapshot.promotions()) {
            var line = row("receipt_promotion_terms",rev,"source_position",position++,"description",promotion.description(),"promotion_type",promotion.promotionType().name(),"threshold_unit",promotion.promotionType()==PromotionType.SPEND?"MONEY":"QUANTITY","reward_type",promotion.rewardType().name(),"source_representation","LEGACY_DOUBLE","source_context",serialize(promotion));
            decimal(line,"qualification_threshold",legacy(promotion.qualificationThreshold())); decimal(line,"reward_amount",legacy(promotion.rewardAmount())); rows.add(line);
        }
        position = 0;
        for (var reward : snapshot.rewards()) amount(rows,rev,"rewards/"+position++,"legacy_reward_amount",legacy(reward.amount()),"UNKNOWN_UNIT",null,null,"LEGACY_DOUBLE",reward);
        observations(rows,rev);
        if (transaction != null) {
            var subtotals = new LinkedHashMap<String,BigDecimal>();
            if (snapshot.purchaseItems().stream().allMatch(item -> item.regularUnitPrice() != null)) {
                subtotals.put("regular_merchandise_total",calculator.calculateRegularMerchandiseTotal(transaction));
            }
            subtotals.put("sale_merchandise_total",calculator.calculateSaleMerchandiseTotal(transaction));
            subtotals.put("applied_coupon_total",calculator.calculateCouponTotal(transaction));
            subtotals.put("after_coupon_total",calculator.calculateAfterCouponTotal(transaction));
            subtotals.put("register_rewards_redeemed",calculator.calculateRegisterRewardsRedeemed(transaction));
            subtotals.put("walgreens_cash_redeemed",calculator.calculateWalgreensCashRedeemed(transaction));
            subtotals.put("reward_redemption_total",calculator.calculateRewardRedemptionTotal(transaction));
            subtotals.put("after_redemptions_total",calculator.calculateAfterRewardRedemptions(transaction));
            subtotals.forEach((field,value)->amount(rows,rev,"calculated",field,value,"MONEY",null,null,"DERIVED",FORMULA));
        }
        return rows;
    }

    private static void amount(List<Row> rows, ReceiptRevSnap rev, String path, String field, BigDecimal value,
            String unit, String currency, String program, String representation, Object context) {
        if (value == null) return;
        var line = row("receipt_financial_amounts",rev,"source_path",path,"field_name",field,"unit",unit,"currency",currency,"program",program,"source_representation",representation,"source_text",value.toString(),"source_context",serialize(context));
        decimal(line,"amount",value); rows.add(line);
    }
    private static void observed(List<Row> rows, ReceiptRevSnap rev, String path, String field, BigDecimal value, String unit, String currency, Object context) {
        amount(rows,rev,path,field,value,unit,currency,null,"BIG_DECIMAL",context);
    }
    private static void observations(List<Row> rows, ReceiptRevSnap rev) {
        var observed = rev.contents().observations();
        int position = 0;
        for (var earning : observed.earnings()) {
            String path = "earnings/"+earning.earningId();
            for (String role : List.of("EXPECTED","CONFIRMED")) {
                var benefit = role.equals("EXPECTED")?earning.expected():earning.confirmed();
                if (benefit == null) continue;
                var line = row("receipt_earning_benefits",rev,"earning_id",earning.earningId(),"source_position",position,"benefit_role",role,"earning_status",earning.status().name(),"benefit_kind",benefit.kind().name(),"unit",benefit.kind()==BenefitKind.POINTS?"POINTS":"MONEY","currency",benefit.currency()==null?null:benefit.currency().getCurrencyCode(),"program",benefit.program(),"source_context",serialize(earning));
                decimal(line,"amount",benefit.amount()); rows.add(line);
            }
            if (earning.valuation()!=null) observed(rows,rev,path,"valuation_amount",earning.valuation().amount(),"MONEY",earning.valuation().currency().getCurrencyCode(),earning.valuation());
            if (earning.requirement()!=null) observed(rows,rev,path,"requirement_amount",earning.requirement().amount(),earning.requirement().type()==PromotionType.SPEND?"MONEY":"QUANTITY",null,earning.requirement());
            position++;
        }
        for (var rr : observed.unidentifiedRR()) observed(rows,rev,"unidentifiedRR/"+rr.eventId(),"unidentified_rr_amount",rr.amount(),"MONEY",null,rr);
        var wc = observed.walgreensCash();
        if (wc!=null) {
            observed(rows,rev,"walgreensCash","wc_starting_balance",wc.startingBalance(),"MONEY",null,wc);
            observed(rows,rev,"walgreensCash","wc_used",wc.used(),"MONEY",null,wc);
            observed(rows,rev,"walgreensCash","wc_earned",wc.earned(),"MONEY",null,wc);
            observed(rows,rev,"walgreensCash","wc_ending_balance",wc.endingBalance(),"MONEY",null,wc);
            observed(rows,rev,"walgreensCash","wc_available_balance",wc.availableBalance(),"MONEY",null,wc);
        }
        for (var saving : observed.savings()) {
            String path="savings/"+saving.categoryId();
            observed(rows,rev,path,"savings_reported_amount",saving.reportedAmount(),"MONEY",saving.currency().getCurrencyCode(),saving);
            for (var inclusion : saving.inclusions()) observed(rows,rev,path+"/"+inclusion.event().kind()+"/"+inclusion.event().eventId(),"savings_included_amount",inclusion.includedAmount(),"MONEY",saving.currency().getCurrencyCode(),inclusion);
        }
        for (var link : observed.promotionLinks()) observed(rows,rev,"promotions/"+link.promotionId(),"reviewed_eligible_amount",link.reviewedEligibleAmount(),"MONEY",null,link);
        for (var entry : observed.worksheet().uses().entrySet()) observed(rows,rev,"uses/"+entry.getKey(),"original_units",entry.getValue().originalUnits(),"UNKNOWN_UNIT",null,entry.getValue());
        observed(rows,rev,"receipt","receipt_saving_value_percent",observed.receiptSavingValuePercent(),"RATE",null,observed.receiptSavingValuePercent());
    }
}
