package coupons.repository;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposablePostgresTargetTest {
    private final String schema = "phase4_test_" + UUID.randomUUID().toString().replace("-", "");
    private final DisposablePostgresTarget target = new DisposablePostgresTarget("127.0.0.1", 55441,
            "phase4-" + "a".repeat(32), "coupon_numeric_test", schema);

    @Test void rejectsWrongEndpointDatabaseRoleAndOptionsBeforeConnecting() {
        for (String url : new String[]{"jdbc:postgresql://127.0.0.1:55441/distributed_coupons",
                "jdbc:postgresql://localhost:55441/distributed_coupons_test",
                "jdbc:postgresql://127.0.0.1:5432/distributed_coupons_test",
                "jdbc:postgresql://127.0.0.1:55441/distributed_coupons_test?currentSchema=public",
                "jdbc:postgresql://127.0.0.1:55441/distributed_coupons_test?options=-crole=postgres"}) {
            assertThrows(IllegalArgumentException.class, () -> target.open(new PostgresConnectionFactory(url, "coupon_numeric_test", "unused")));
        }
        assertThrows(IllegalArgumentException.class, () -> target.verifyUrl("jdbc:postgresql://127.0.0.1:55441/distributed_coupons_test", "postgres"));
    }

    @Test void rejectsPublicAndUnmarkedTargets() {
        assertThrows(IllegalArgumentException.class, () -> new DisposablePostgresTarget("127.0.0.1", 55441, "unmarked", "test", schema));
        assertThrows(IllegalArgumentException.class, () -> new DisposablePostgresTarget("127.0.0.1", 55441, "phase4-"+"a".repeat(32), "test", "public"));
    }

    @Test void liveVerificationRejectsWrongMarkerAndWrongSchema() throws Exception {
        try (var database = new PostgresTestDatabase()) {
            var guard = database.target();
            try (var connection = guard.open(database.connections)) {
                guard.verifySchema(connection);
                assertThrows(java.sql.SQLException.class, () -> connection.createStatement().executeUpdate("UPDATE phase4_safety.cluster_identity SET marker='spoofed'"));
                var wrongSchema = new DisposablePostgresTarget(guard.host(),guard.port(),guard.marker(),guard.role(),"phase4_test_"+"f".repeat(32));
                assertThrows(IllegalStateException.class, () -> wrongSchema.verifySchema(connection));
                var wrong = new DisposablePostgresTarget(guard.host(), guard.port(), "phase4-"+"0".repeat(32), guard.role(), guard.schema());
                assertThrows(IllegalStateException.class, () -> wrong.verifyServer(connection));
                connection.createStatement().execute("SET search_path TO public");
                assertThrows(IllegalStateException.class, () -> guard.verifySchema(connection));
            }
        }
    }
}
