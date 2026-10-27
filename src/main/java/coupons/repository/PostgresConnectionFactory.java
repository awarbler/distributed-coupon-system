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

    public PostgresConnectionFactory(String url, String user, String password) {
        this.url = requireText(url, "PostgreSQL URL");
        this.user = requireText(user, "PostgreSQL user");
        this.password = Objects.requireNonNull(password, "PostgreSQL password cannot be null.");
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
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
