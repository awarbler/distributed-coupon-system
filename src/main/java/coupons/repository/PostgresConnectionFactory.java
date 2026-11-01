package coupons.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Creates PostgreSQL connections from explicit configuration. */
public final class PostgresConnectionFactory {
    private final String url;
    private final String user;
    private final String password;
    private final String readSnapshot;

    public PostgresConnectionFactory(String url, String user, String password) {
        this(url, user, password, null);
    }

    private PostgresConnectionFactory(String url, String user, String password, String readSnapshot) {
        this.url = requireText(url, "PostgreSQL URL");
        this.user = requireText(user, "PostgreSQL user");
        this.password = Objects.requireNonNull(password, "PostgreSQL password cannot be null.");
        this.readSnapshot = readSnapshot;
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        if (readSnapshot == null) return connection;
        try {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION SNAPSHOT '" + readSnapshot.replace("'", "''") + "'");
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try { connection.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    /** Nested read repositories share the caller's still-open repeatable-read snapshot. */
    PostgresConnectionFactory snapshotReads(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT pg_export_snapshot()")) {
            rows.next();
            return new PostgresConnectionFactory(url, user, password, rows.getString(1));
        }
    }

    /** Validates the endpoint before connecting, then verifies server identity and privileges. */
    Connection openDisposable(DisposablePostgresTarget target) throws SQLException {
        target.verifyUrl(url, user);
        Connection connection = open();
        try {
            target.verifyServer(connection);
            return connection;
        } catch (SQLException | RuntimeException failure) {
            try { connection.close(); } catch (SQLException close) { failure.addSuppressed(close); }
            throw failure;
        }
    }

    public static PostgresConnectionFactory fromEnvironment() {
        return new PostgresConnectionFactory(
                environment("COUPON_POSTGRES_URL"),
                environment("COUPON_POSTGRES_USER"),
                System.getenv("COUPON_POSTGRES_PASSWORD"));
    }

    private static String environment(String key) {
        String value = System.getenv(key);
        return requireText(value, key + " environment variable");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured.");
        }
        return value;
    }
}
