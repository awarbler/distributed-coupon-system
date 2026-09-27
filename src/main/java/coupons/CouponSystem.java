package coupons; // Place the CouponSystem class in the coupons package.

/**
 * Coordinates the coupon application's transaction and distributed-system
 * workflows.
 */
public class CouponSystem { // Define the main application service for the coupon tracker.

    private final TransactionCalculator calculator; // Store the calculator used to calculate transaction totals.

    /**
     * Creates the coupon application and its required services.
     */
    public CouponSystem() { // Construct the coupon-system application.
        this.calculator = new TransactionCalculator(); // Create the transaction calculator used by this application.
    } // End the CouponSystem constructor.

    /**
     * Runs the current coupon-system demonstration.
     */
    public void run() { // Start the current application workflow.
        CouponTransaction transaction = createWalgreensTransaction(); // Create the Walgreens transaction used by the current demonstration.
        displayTransaction(transaction); // Display the transaction calculations.
        runDistributedExample(); // Run the current distributed-system demonstration.
    } // End the run method.

    /**
     * Creates the Walgreens transaction currently used by the application.
     *
     * @return the completed Walgreens transaction used by the demonstration
     */
    private CouponTransaction createWalgreensTransaction() { // Build the Walgreens transaction represented by the current receipt.
        ReceiptTotals receiptTotals = new ReceiptTotals(1.31, 7.28); // Record the tax and final total printed on the receipt.
        CouponTransaction transaction = new CouponTransaction(receiptTotals); // Create the transaction using the retailer-reported receipt totals.

        addPurchaseItems(transaction); // Add the products purchased during the transaction.
        addCoupons(transaction); // Add the coupons applied during the transaction.
        addPayments(transaction); // Add the payments and redeemed rewards used during checkout.

        return transaction; // Return the completed transaction to the caller.
    } // End the createWalgreensTransaction method.

    /**
     * Adds the purchased products from the current Walgreens receipt.
     *
     * @param transaction transaction receiving the purchased products
     */
    private void addPurchaseItems(CouponTransaction transaction) { // Add each purchased product to the transaction.
        transaction.addPurchaseItem( // Add the Gain product line.
                new PurchaseItem( // Create the Gain purchase record.
                        "Gain Laundry Detergent", // Store the product's display name.
                        "GAIN", // Store the product identifier.
                        11.99, // Record the regular unit price.
                        9.99, // Record the sale unit price.
                        1 // Record that one unit was purchased.
                ) // Finish creating the Gain purchase record.
        ); // Finish adding the Gain purchase record.

        transaction.addPurchaseItem( // Add the Tide product line.
                new PurchaseItem( // Create the Tide purchase record.
                        "Tide Pods", // Store the product's display name.
                        "TIDE", // Store the product identifier.
                        17.99, // Record the regular unit price.
                        15.99, // Record the sale unit price.
                        1 // Record that one unit was purchased.
                ) // Finish creating the Tide purchase record.
        ); // Finish adding the Tide purchase record.

        transaction.addPurchaseItem( // Add the Dawn Platinum product line.
                new PurchaseItem( // Create the Dawn Platinum purchase record.
                        "Dawn Platinum", // Store the product's display name.
                        "DAWN-PLATINUM", // Store the product identifier.
                        7.49, // Record the regular unit price.
                        4.99, // Record the sale unit price.
                        1 // Record that one unit was purchased.
                ) // Finish creating the Dawn Platinum purchase record.
        ); // Finish adding the Dawn Platinum purchase record.

        transaction.addPurchaseItem( // Add the Dawn Powerwash product line.
                new PurchaseItem( // Create the Dawn Powerwash purchase record.
                        "Dawn Powerwash", // Store the product's display name.
                        "DAWN-POWERWASH", // Store the product identifier.
                        6.99, // Record the regular unit price.
                        5.99, // Record the sale unit price.
                        1 // Record that one unit was purchased.
                ) // Finish creating the Dawn Powerwash purchase record.
        ); // Finish adding the Dawn Powerwash purchase record.
    } // End the addPurchaseItems method.

    /**
     * Adds the coupons applied to the current Walgreens transaction.
     *
     * <p>
     * The expiration dates are currently unknown, so null records that the
     * expiration date was not available when this transaction was entered.
     * </p>
     *
     * @param transaction transaction receiving the coupons
     */
    private void addCoupons(CouponTransaction transaction) { // Add each coupon recorded for this transaction.
        transaction.addCoupon( // Add the Tide coupon.
                new Coupon( // Create the Tide coupon record.
                        "$3 off Tide", // Store the coupon description.
                        3.00, // Record the coupon discount.
                        1, // Require one qualifying Tide product.
                        CouponType.MANUFACTURER, // Identify the coupon as a manufacturer coupon.
                        null // Record that the coupon's expiration date is currently unknown.
                ) // Finish creating the Tide coupon.
        ); // Finish adding the Tide coupon.

        transaction.addCoupon( // Add the Gain coupon.
                new Coupon( // Create the Gain coupon record.
                        "$2 off Gain", // Store the coupon description.
                        2.00, // Record the coupon discount.
                        1, // Require one qualifying Gain product.
                        CouponType.MANUFACTURER, // Identify the coupon as a manufacturer coupon.
                        null // Record that the coupon's expiration date is currently unknown.
                ) // Finish creating the Gain coupon.
        ); // Finish adding the Gain coupon.

        transaction.addCoupon( // Add the Dawn coupon.
                new Coupon( // Create the Dawn coupon record.
                        "$4 off 2 Dawn", // Store the coupon description.
                        4.00, // Record the coupon discount.
                        2, // Require two qualifying Dawn products.
                        CouponType.MANUFACTURER, // Identify the coupon as a manufacturer coupon.
                        null // Record that the coupon's expiration date is currently unknown.
                ) // Finish creating the Dawn coupon.
        ); // Finish adding the Dawn coupon.
    } // End the addCoupons method.

    /**
     * Adds the reward value redeemed on the current Walgreens transaction.
     *
     * @param transaction transaction receiving the payment records
     */
    private void addPayments(CouponTransaction transaction) { // Add each payment or redeemed reward used during checkout.
        transaction.addPayment( // Add the first Register Reward redemption.
                new Payment( // Create the Register Reward payment record.
                        PaymentType.REGISTER_REWARD, // Identify this payment as a Register Reward.
                        7.00 // Record seven dollars of redeemed Register Reward value.
                ) // Finish creating the Register Reward payment.
        ); // Finish adding the first Register Reward payment.

        transaction.addPayment( // Add the second Register Reward redemption.
                new Payment( // Create the second Register Reward payment record.
                        PaymentType.REGISTER_REWARD, // Identify this payment as a Register Reward.
                        5.00 // Record five dollars of redeemed Register Reward value.
                ) // Finish creating the Register Reward payment.
        ); // Finish adding the second Register Reward payment.

        transaction.addPayment( // Add the Walgreens Cash redemption.
                new Payment( // Create the Walgreens Cash payment record.
                        PaymentType.WALGREENS_CASH, // Identify this payment as Walgreens Cash.
                        10.00 // Record ten dollars of redeemed Walgreens Cash.
                ) // Finish creating the Walgreens Cash payment.
        ); // Finish adding the Walgreens Cash payment.
    } // End the addPayments method.

    /**
     * Displays the calculated and retailer-reported transaction totals.
     *
     * @param transaction transaction whose totals will be displayed
     */
    private void displayTransaction(CouponTransaction transaction) { // Display the transaction calculations.
        System.out.println(); // Print a blank line before the transaction report.
        System.out.println("Distributed Coupon System"); // Print the transaction report heading.

        System.out.println( // Print the number of purchase lines.
                "Purchase lines: " // Begin the purchase-line output.
                        + transaction.getPurchaseItems().size() // Append the number of purchase lines.
        ); // Finish printing the purchase-line count.

        System.out.printf( // Print the regular-price merchandise total.
                "Regular merchandise total: $%.2f%n", // Format the amount as currency with two decimal places.
                calculator.calculateRegularMerchandiseTotal(transaction) // Calculate the regular-price merchandise total.
        ); // Finish printing the regular-price merchandise total.

        System.out.printf( // Print the sale-price merchandise total.
                "Sale merchandise total: $%.2f%n", // Format the amount as currency with two decimal places.
                calculator.calculateSaleMerchandiseTotal(transaction) // Calculate the sale-price merchandise total.
        ); // Finish printing the sale-price merchandise total.

        System.out.printf( // Print the total value of the coupons.
                "Coupon total: $%.2f%n", // Format the coupon amount as currency.
                calculator.calculateCouponTotal(transaction) // Calculate the combined coupon value.
        ); // Finish printing the coupon total.

        System.out.printf( // Print the merchandise total remaining after coupons.
                "After coupons: $%.2f%n", // Format the remaining merchandise amount as currency.
                calculator.calculateAfterCouponTotal(transaction) // Calculate the amount remaining after coupons.
        ); // Finish printing the after-coupon total.

        System.out.printf( // Print the Walgreens Cash redeemed.
                "Walgreens Cash redeemed: $%.2f%n", // Format the Walgreens Cash amount as currency.
                calculator.calculateWalgreensCashRedeemed(transaction) // Calculate the Walgreens Cash used during checkout.
        ); // Finish printing the Walgreens Cash redemption.

        System.out.printf( // Print the Register Rewards redeemed.
                "Register Rewards redeemed: $%.2f%n", // Format the Register Reward amount as currency.
                calculator.calculateRegisterRewardsRedeemed(transaction) // Calculate the Register Rewards used during checkout.
        ); // Finish printing the Register Reward redemption.

        System.out.printf( // Print the merchandise amount remaining after reward redemptions.
                "Remaining after reward redemptions: $%.2f%n", // Format the remaining amount as currency.
                calculator.calculateAfterRewardRedemptions(transaction) // Calculate the amount remaining after redeemed rewards.
        ); // Finish printing the remaining amount.

        System.out.printf( // Print the tax reported on the receipt.
                "Tax: $%.2f%n", // Format the tax as currency.
                transaction.getReceiptTotals().getTaxAmount() // Read the retailer-reported tax.
        ); // Finish printing the tax.

        System.out.printf( // Print the checkout amount predicted by the model.
                "Calculated checkout amount: $%.2f%n", // Format the calculated checkout amount as currency.
                calculator.calculateExpectedCheckoutAmount(transaction) // Calculate the expected checkout amount.
        ); // Finish printing the calculated checkout amount.

        System.out.printf( // Print the actual final total from the receipt.
                "Actual receipt total: $%.2f%n", // Format the retailer-reported amount as currency.
                transaction.getReceiptTotals().getFinalTotal() // Read the final total printed on the receipt.
        ); // Finish printing the actual receipt total.

        System.out.printf( // Print the difference between the calculated and actual totals.
                "Receipt difference: $%.2f%n", // Format the difference as currency.
                calculator.calculateReceiptDifference(transaction) // Calculate the receipt discrepancy.
        ); // Finish printing the receipt difference.
    } // End the displayTransaction method.

    /**
     * Runs the current distributed-process example.
     *
     * <p>
     * This example is temporary. The distributed-process model will be revised
     * after the transaction, coupon, receipt, and rebate domain models are
     * completed.
     * </p>
     */
    private void runDistributedExample() { // Run the existing distributed-system example temporarily.
        DistributedProcess couponTrackerProcess = new DistributedProcess(1, "Coupon Tracker"); // Create the current P1 coupon-tracker process.
        DistributedProcess walgreensProcess = new DistributedProcess(2, "Walgreens"); // Create the current P2 Walgreens process.
        DistributedProcess ibottaProcess = new DistributedProcess(3, "Ibotta"); // Create the current P3 Ibotta process.

        DistributedEvent purchaseRecorded = new DistributedEvent( // Create event a at the coupon-tracker process.
                "a", // Assign event label a.
                1, // Record that event a currently belongs to P1.
                "Purchase recorded" // Describe the application-level action.
        ); // Finish creating event a.

        DistributedEvent rebateRequestSent = new DistributedEvent( // Create event b at the coupon-tracker process.
                "b", // Assign event label b.
                1, // Record that event b currently belongs to P1.
                "Ibotta rebate request sent" // Describe the application-level action.
        ); // Finish creating event b.

        DistributedEvent purchaseProcessed = new DistributedEvent( // Create event c at the Walgreens process.
                "c", // Assign event label c.
                2, // Record that event c currently belongs to P2.
                "Purchase processed" // Describe the application-level action.
        ); // Finish creating event c.

        DistributedEvent purchaseConfirmed = new DistributedEvent( // Create event d at the Walgreens process.
                "d", // Assign event label d.
                2, // Record that event d currently belongs to P2.
                "Purchase confirmed" // Describe the application-level action.
        ); // Finish creating event d.

        DistributedEvent rebateRequestReceived = new DistributedEvent( // Create event e at the Ibotta process.
                "e", // Assign event label e.
                3, // Record that event e currently belongs to P3.
                "Ibotta rebate request received" // Describe the application-level action.
        ); // Finish creating event e.

        DistributedEvent rebateApproved = new DistributedEvent( // Create event f at the Ibotta process.
                "f", // Assign event label f.
                3, // Record that event f currently belongs to P3.
                "Ibotta rebate approved" // Describe the application-level action.
        ); // Finish creating event f.

        couponTrackerProcess.addEvent(purchaseRecorded); // Add event a to the current P1 event sequence.
        couponTrackerProcess.addEvent(rebateRequestSent); // Add event b to the current P1 event sequence.

        walgreensProcess.addEvent(purchaseProcessed); // Add event c to the current P2 event sequence.
        walgreensProcess.addEvent(purchaseConfirmed); // Add event d to the current P2 event sequence.

        ibottaProcess.addEvent(rebateRequestReceived); // Add event e to the current P3 event sequence.
        ibottaProcess.addEvent(rebateApproved); // Add event f to the current P3 event sequence.

        Message rebateRequestMessage = new Message( // Create the current message representing the Ibotta rebate request.
                couponTrackerProcess.getProcessId(), // Use the current coupon-tracker process as the message source.
                ibottaProcess.getProcessId(), // Use the Ibotta process as the message destination.
                "REBATE_REQUEST", // Identify the message as a rebate request.
                "Submit Ibotta rebate", // Store the application-level content of the request.
                rebateRequestSent, // Associate event b with sending the request.
                rebateRequestReceived // Associate event e with receiving the request.
        ); // Finish creating the rebate-request message.

        displayDistributedMessage(rebateRequestMessage); // Display the current distributed message.
    } // End the runDistributedExample method.

    /**
     * Displays the current distributed message example.
     *
     * @param message distributed message to display
     */
    private void displayDistributedMessage(Message message) { // Display information about one distributed message.
        System.out.println(); // Print a blank line before the distributed-system output.
        System.out.println("Distributed Message"); // Print the distributed-message heading.

        System.out.println( // Print the source process.
                "Source: P" + message.getSourceProcessId() // Append the source process identifier.
        ); // Finish printing the source process.

        System.out.println( // Print the destination process.
                "Destination: P" + message.getDestinationProcessId() // Append the destination process identifier.
        ); // Finish printing the destination process.

        System.out.println( // Print the message type.
                "Type: " + message.getMessageType() // Append the message type.
        ); // Finish printing the message type.

        System.out.println( // Print the application-level message content.
                "Content: " + message.getContent() // Append the message content.
        ); // Finish printing the message content.

        System.out.println( // Print the message's send event.
                "Send event: " + message.getSendEvent().getEventId() // Append the send-event identifier.
        ); // Finish printing the send event.

        System.out.println( // Print the message's receive event.
                "Receive event: " + message.getReceiveEvent().getEventId() // Append the receive-event identifier.
        ); // Finish printing the receive event.
    } // End the displayDistributedMessage method.

} // End the CouponSystem class.