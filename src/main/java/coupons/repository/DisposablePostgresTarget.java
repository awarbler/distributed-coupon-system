package coupons.repository;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Explicit capability for experimental DDL; never read from production configuration. */
public record DisposablePostgresTarget(String host, int port, String marker, String role, String schema) {
    public DisposablePostgresTarget {
        if (!"127.0.0.1".equals(host) || port < 1024 || port > 65535
                || marker == null || !marker.matches("phase4-[a-f0-9]{32}")
                || role == null || !role.matches("[a-z][a-z0-9_]*")
                || schema == null || !schema.matches("(?:phase4|api|migration)_test_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Explicit disposable loopback target and private schema required.");
        }
    }

    void verifyUrl(String url, String user) {
        URI uri;
        try { uri = URI.create(url.substring(5)); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid disposable JDBC URL."); }
        if (!url.startsWith("jdbc:postgresql://") || !"postgresql".equals(uri.getScheme())
                || !host.equals(uri.getHost()) || port != uri.getPort()
                || !"/distributed_coupons_test".equals(uri.getPath()) || uri.getUserInfo() != null
                || uri.getFragment() != null || !role.equals(user)) {
            throw new IllegalArgumentException("Connection does not match the authorized disposable target.");
        }
        String query = uri.getQuery();
        if (query != null && !query.equals("currentSchema=" + schema)) {
            throw new IllegalArgumentException("Only the authorized private-schema URL option is permitted.");
        }
    }

    public Connection open(PostgresConnectionFactory factory) throws SQLException {
        return factory.openDisposable(this);
    }

    public void verifyServer(Connection connection) throws SQLException {
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT current_database(), current_user, session_user, host(inet_server_addr()),
                       inet_server_port(), current_setting('coupon.phase4_marker', true),
                       r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls,
                       EXISTS(SELECT 1 FROM pg_database d WHERE d.datallowconn
                         AND d.datname <> 'distributed_coupons_test'
                         AND has_database_privilege(session_user, d.oid, 'CONNECT')),
                       EXISTS(SELECT 1 FROM pg_roles other WHERE other.rolname <> current_user AND other.rolname <> 'pg_database_owner'
                         AND pg_has_role(session_user,other.oid,'MEMBER'))
                  FROM pg_roles r WHERE r.rolname = current_user
                """)) {
            if (!rows.next() || !"distributed_coupons_test".equals(rows.getString(1))
                    || !role.equals(rows.getString(2)) || !role.equals(rows.getString(3))
                    || !host.equals(rows.getString(4)) || port != rows.getInt(5)
                    || !Objects.equals(marker, rows.getString(6)) || rows.getBoolean(7)
                    || rows.getBoolean(8) || rows.getBoolean(9)) {
                throw new IllegalStateException("Disposable database, marker, endpoint or restricted-role verification failed.");
            }
        }
        verifyProtectedMarker(connection);
    }

    private void verifyProtectedMarker(Connection connection) throws SQLException {
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT marker, has_table_privilege(current_user,'phase4_safety.cluster_identity','INSERT,UPDATE,DELETE,TRUNCATE'),
                  pg_get_userbyid(n.nspowner), pg_my_temp_schema()
                FROM phase4_safety.cluster_identity, pg_namespace n WHERE n.nspname='phase4_safety'
                """)) {
            if (!rows.next() || !marker.equals(rows.getString(1)) || rows.getBoolean(2)
                    || role.equals(rows.getString(3)) || rows.getLong(4)!=0 || rows.next()) {
                throw new IllegalStateException("Read-only administrator-owned cluster marker required.");
            }
        }
    }

    public void verifySchema(Connection connection) throws SQLException {
        verifyServer(connection);
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT current_schema(), current_schemas(false)::text,
                  (SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname=current_schema())
                """)) {
            if (!rows.next() || !schema.equals(rows.getString(1))
                    || !("{" + schema + "}").equals(rows.getString(2)) || !role.equals(rows.getString(3))) {
                throw new IllegalStateException("Experimental operations require the owned private schema only.");
            }
        }
    }
}
