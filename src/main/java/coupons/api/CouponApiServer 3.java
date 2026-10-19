package coupons.api;

import com.sun.net.httpserver.HttpServer;
import coupons.repository.*;

/** Optional API process; the existing CLI remains unchanged. */
public final class CouponApiServer {
    private CouponApiServer() {}
    public static void main(String[] args) throws Exception {
        PostgresConnectionFactory database = PostgresConnectionFactory.fromEnvironment();
        try (var connection = database.open()) { PostgresSchema.apply(connection); }
        CouponApiService service = CouponApiService.fromEnvironment(
                new PostgresTransactionRepository(database),
                new PostgresShoppingPlanRepository(database),
                new PostgresClippedCouponRepository(database));
        int port = Integer.parseInt(System.getenv().getOrDefault("COUPON_API_PORT", "8080"));
        HttpServer server = service.start(port);
        System.out.println("Coupon API listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }
}
