package coupons;

/**
 * Coordinates the coupon application's transaction and distributed-system
 * workflows.
 */
public class CouponSystem {

    private final TransactionCalculator calculator;

    /**
     * Creates the coupon application and its required services.
     */
    public CouponSystem() {
        this.calculator = new TransactionCalculator();
    }

    /**
     * Runs the current coupon-system demonstration.
     */
    public void run() {
        CouponTransaction transaction = createWalgreensTransaction();

        displayTransaction(transaction);
        runDistributedExample();
    }

    /**
     * Creates the Walgreens transaction currently used by the application.
     */
    private CouponTransaction createWalgreensTransaction() {
        ReceiptTotals receiptTotals = new ReceiptTotals(1.31, 7.28);
        CouponTransaction transaction = new CouponTransaction(receiptTotals);

        addPurchaseItems(transaction);
        addCoupons(transaction);
        addPayments(transaction);

        return transaction;
    }

    /**
     * Adds the purchased products from the current Walgreens receipt.
     */
    private void addPurchaseItems(CouponTransaction transaction) {
        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Gain Laundry Detergent",
                        "GAIN",
                        11.99,
                        9.99,
                        1
                )
        );

        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Tide Pods",
                        "TIDE",
                        17.99,
                        15.99,
                        1
                )
        );

        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Dawn Platinum",
                        "DAWN-PLATINUM",
                        7.49,
                        4.99,
                        1
                )
        );

        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Dawn Powerwash",
                        "DAWN-POWERWASH",
                        6.99,
                        5.99,
                        1
                )
        );
    }

    /**
     * Adds the coupons applied to the current Walgreens transaction.
     */
    private void addCoupons(CouponTransaction transaction) {
        transaction.addCoupon(
                new Coupon(
                        "$3 off Tide",
                        3.00,
                        1,
                        CouponType.MANUFACTURER
                )
        );

        transaction.addCoupon(
                new Coupon(
                        "$2 off Gain",
                        2.00,
                        1,
                        CouponType.MANUFACTURER
                )
        );

        transaction.addCoupon(
                new Coupon(
                        "$4 off 2 Dawn",
                        4.00,
                        2,
                        CouponType.MANUFACTURER
                )
        );
    }

    /**
     * Adds the reward value redeemed on the current Walgreens transaction.
     */
    private void addPayments(CouponTransaction transaction) {
        transaction.addPayment(
                new Payment(
                        PaymentType.REGISTER_REWARD,
                        7.00
                )
        );

        transaction.addPayment(
                new Payment(
                        PaymentType.REGISTER_REWARD,
                        5.00
                )
        );

        transaction.addPayment(
                new Payment(
                        PaymentType.WALGREENS_CASH,
                        10.00
                )
        );
    }

    /**
     * Displays the calculated and retailer-reported transaction totals.
     */
    private void displayTransaction(CouponTransaction transaction) {
        System.out.println();
        System.out.println("Distributed Coupon System");

        System.out.println(
                "Purchase lines: "
                        + transaction.getPurchaseItems().size()
        );

        System.out.printf(
                "Regular merchandise total: $%.2f%n",
                calculator.calculateRegularMerchandiseTotal(transaction)
        );

        System.out.printf(
                "Sale merchandise total: $%.2f%n",
                calculator.calculateSaleMerchandiseTotal(transaction)
        );

        System.out.printf(
                "Coupon total: $%.2f%n",
                calculator.calculateCouponTotal(transaction)
        );

        System.out.printf(
                "After coupons: $%.2f%n",
                calculator.calculateAfterCouponTotal(transaction)
        );

        System.out.printf(
                "Walgreens Cash redeemed: $%.2f%n",
                calculator.calculateWalgreensCashRedeemed(transaction)
        );

        System.out.printf(
                "Register Rewards redeemed: $%.2f%n",
                calculator.calculateRegisterRewardsRedeemed(transaction)
        );

        System.out.printf(
                "Remaining after reward redemptions: $%.2f%n",
                calculator.calculateAfterRewardRedemptions(transaction)
        );

        System.out.printf(
                "Tax: $%.2f%n",
                transaction.getReceiptTotals().getTaxAmount()
        );

        System.out.printf(
                "Calculated checkout amount: $%.2f%n",
                calculator.calculateExpectedCheckoutAmount(transaction)
        );

        System.out.printf(
                "Actual receipt total: $%.2f%n",
                transaction.getReceiptTotals().getFinalTotal()
        );

        System.out.printf(
                "Receipt difference: $%.2f%n",
                calculator.calculateReceiptDifference(transaction)
        );
    }

    /**
     * Runs the current distributed-process example.
     */
    private void runDistributedExample() {
        DistributedProcess couponTrackerProcess
                = new DistributedProcess(1, "Coupon Tracker");

        DistributedProcess walgreensProcess
                = new DistributedProcess(2, "Walgreens");

        DistributedProcess ibottaProcess
                = new DistributedProcess(3, "Ibotta");

        DistributedEvent purchaseRecorded
                = new DistributedEvent(
                        "a",
                        1,
                        "Purchase recorded"
                );

        DistributedEvent rebateRequestSent
                = new DistributedEvent(
                        "b",
                        1,
                        "Ibotta rebate request sent"
                );

        DistributedEvent purchaseProcessed
                = new DistributedEvent(
                        "c",
                        2,
                        "Purchase processed"
                );

        DistributedEvent purchaseConfirmed
                = new DistributedEvent(
                        "d",
                        2,
                        "Purchase confirmed"
                );

        DistributedEvent rebateRequestReceived
                = new DistributedEvent(
                        "e",
                        3,
                        "Ibotta rebate request received"
                );

        DistributedEvent rebateApproved
                = new DistributedEvent(
                        "f",
                        3,
                        "Ibotta rebate approved"
                );

        couponTrackerProcess.addEvent(purchaseRecorded);
        couponTrackerProcess.addEvent(rebateRequestSent);

        walgreensProcess.addEvent(purchaseProcessed);
        walgreensProcess.addEvent(purchaseConfirmed);

        ibottaProcess.addEvent(rebateRequestReceived);
        ibottaProcess.addEvent(rebateApproved);

        Message rebateRequestMessage = new Message(
                couponTrackerProcess.getProcessId(),
                ibottaProcess.getProcessId(),
                "REBATE_REQUEST",
                "Submit Ibotta rebate",
                rebateRequestSent,
                rebateRequestReceived
        );

        displayDistributedMessage(rebateRequestMessage);
    }

    /**
     * Displays the current distributed message example.
     */
    private void displayDistributedMessage(Message message) {
        System.out.println();
        System.out.println("Distributed Message");

        System.out.println(
                "Source: P" + message.getSourceProcessId()
        );

        System.out.println(
                "Destination: P" + message.getDestinationProcessId()
        );

        System.out.println(
                "Type: " + message.getMessageType()
        );

        System.out.println(
                "Content: " + message.getContent()
        );

        System.out.println(
                "Send event: " + message.getSendEvent().getEventId()
        );

        System.out.println(
                "Receive event: " + message.getReceiveEvent().getEventId()
        );
    }
}