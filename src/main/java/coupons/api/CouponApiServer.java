package coupons.api;

import coupons.repository.*;

/** Optional API process; CLI entry points remain unchanged. */
public final class CouponApiServer {
    private CouponApiServer() {}
    public static void main(String[] args) throws Exception {
        PostgresConnectionFactory db = PostgresConnectionFactory.fromEnvironment();
        try (var connection = db.open()) { PostgresSchema.apply(connection); }
        CouponApiService service = new CouponApiService(new PostgresTransactionRepository(db),
                new PostgresShoppingPlanRepository(db), new PostgresClippedCouponRepository(db),
                System.getenv("COUPON_API_TOKEN"), System.getenv("COUPON_API_ADMIN_TOKEN"));
        service.start(Integer.parseInt(System.getenv().getOrDefault("COUPON_API_PORT", "8080")));
    }
}
