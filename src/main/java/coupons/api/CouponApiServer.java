package coupons.api;

import coupons.repository.*;

/** Optional API process; CLI entry points remain unchanged. */
public final class CouponApiServer {
    private CouponApiServer() {}
    public static void main(String[] args) throws Exception {
        PostgresConnectionFactory db = PostgresConnectionFactory.fromEnvironment();
        CouponApiService service = new CouponApiService(new PostgresTransactionRepository(db),
                new PostgresShoppingPlanRepository(db), new PostgresClippedCouponRepository(db),
                System.getenv("COUPON_API_TOKEN"), System.getenv("COUPON_API_ADMIN_TOKEN"),
                new PostgresApiRequestStore(db), new PostgresPlatformRepository(db));
        try (var connection = db.open()) { PostgresSchema.apply(connection); }
        var server = service.start(Integer.parseInt(System.getenv().getOrDefault("COUPON_API_PORT", "8080")));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        System.out.println("Grocery Intelligence Platform API listening on 127.0.0.1:" + server.getAddress().getPort());
    }
}
