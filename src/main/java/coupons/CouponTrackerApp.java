package coupons;

/**
 * Provides the entry point for the distributed coupon tracking application.
 */
public final class CouponTrackerApp {

    /**
     * Prevents creation of application entry-point objects.
     */
    private CouponTrackerApp() {
    }

    /**
     * Starts the coupon tracking application.
     *
     * @param args command-line arguments; currently unused
     */
    public static void main(String[] args) {
        CouponSystem application = new CouponSystem();
        application.run();
    }
}