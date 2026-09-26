package edu.utexas.ece382n.coupons; // Declare the package that contains the coupon-system classes.

/**
 * Provides the entry point for the distributed coupon tracking application.
 *
 * <p>
 * The application currently creates a local coupon transaction, associates
 * rewards with the transaction, and displays the resulting cost and reward
 * calculations.</p>
 *
 * <p>
 * Distributed-system behavior will be added as ECE 382N course concepts are
 * introduced.</p>
 */
public class CouponTrackerApp { // Provide the entry point for the coupon tracking application.

    /**
     * Prevents creation of utility instances of this application class.
     */
    private CouponTrackerApp() { // Prevent callers from constructing CouponTrackerApp objects.
    } // End the private constructor.

    /**
     * Starts the coupon tracking application.
     *
     * @param args command-line arguments; currently unused
     */
    public static void main(String[] args) { // Start the coupon tracking application.
        System.out.println("Distributed Coupon System"); // Display the application title.
        DistributedProcess couponTrackerProcess = new DistributedProcess(1, "Coupon Tracker"); // Create P1 as the coupon-tracking process.
        DistributedProcess walgreensProcess = new DistributedProcess(2, "Walgreens"); // Create P2 as the Walgreens process.
        DistributedProcess ibottaProcess = new DistributedProcess(3, "Ibotta"); // Create P3 as the Ibotta process.

        DistributedEvent purchaseRecorded = new DistributedEvent("a", 1, "Purchase recorded"); // Create event a at P1.
        DistributedEvent rebateRequestSent = new DistributedEvent("b", 1, "Ibotta rebate request sent"); // Create event b at P1.

        DistributedEvent purchaseProcessed = new DistributedEvent("c", 2, "Purchase processed"); // Create event c at P2.
        DistributedEvent purchaseConfirmed = new DistributedEvent("d", 2, "Purchase confirmed"); // Create event d at P2.

        DistributedEvent rebateRequestReceived = new DistributedEvent("e", 3, "Ibotta rebate request received"); // Create event e at P3.
        DistributedEvent rebateApproved = new DistributedEvent("f", 3, "Ibotta rebate approved"); // Create event f at P3.

        // attach events to p1
        couponTrackerProcess.addEvent(purchaseRecorded);// add event a to the local event sequence for P1
        couponTrackerProcess.addEvent(rebateRequestSent); // add event b to the local event sequence for p1 

        // attach events to P2 
        walgreensProcess.addEvent(purchaseProcessed); // add event c to the local event sequence for P2
        walgreensProcess.addEvent(purchaseConfirmed); // add event d to the local event sequence for P2

        // attach events to P3
        ibottaProcess.addEvent(rebateRequestReceived); // add event e to the local event sequence for P3
        ibottaProcess.addEvent(rebateApproved); // add event f to the local event sequence for P3

        // Message
        Message rebateRequestMessage = new Message( // Create the rebate-request message sent from P1 to P3.
                couponTrackerProcess.getProcessId(), // Set P1 as the source process Pi.
                ibottaProcess.getProcessId(), // Set P3 as the destination process Pj.
                "REBATE_REQUEST", // Identify the type of distributed message.
                "Submit Ibotta rebate", // Store the coupon application's message content.
                rebateRequestSent, // Connect event b to the sending of this message.
                rebateRequestReceived // Connect event e to the receipt of this message.
        ); // Finish constructing the rebate-request message.
        CouponTransaction transaction = new CouponTransaction(20.00, 15.00, 5.00); // Create a coupon transaction.

        Reward ibotta = new Reward("Ibotta", 3.00, true); // Create a cash-equivalent Ibotta rebate.
        Reward walgreensCash = new Reward("Walgreens Cash", 2.00, false); // Create a non-cash Walgreens Cash reward.

        transaction.addReward(ibotta); // Associate the Ibotta rebate with the transaction.
        transaction.addReward(walgreensCash); // Associate the Walgreens Cash reward with the transaction.

        System.out.println();
        System.out.println("Distributed Message");
        System.out.println("Source: P" + rebateRequestMessage.getSourceProcessId()); // Display the sending process Pi.
        System.out.println("Destination: P" + rebateRequestMessage.getDestinationProcessId()); // display the receiving process pj
        System.out.println("Type: " + rebateRequestMessage.getMessageType()); // Display the message type.
        System.out.println("Content: " + rebateRequestMessage.getContent()); // Display the application-level message content.

        System.out.println("Send event: " + rebateRequestMessage.getSendEvent().getEventId()); // Display event b as the send event.
        System.out.println("Receive event: " + rebateRequestMessage.getReceiveEvent().getEventId()); // Display event e as the receive event.

        System.out.println();
        System.out.println("Original price: $20.00"); // Display the transaction's original retail price.
        System.out.println("Out of pocket: $" + transaction.calculateOutOfPocket()); // Display the amount paid at checkout.
        System.out.println("Checkout savings: $" + transaction.calculateCheckoutSavings()); // Display savings realized at checkout.
        System.out.println("Cash rebates: $" + transaction.calculateCashRebates()); // Display the total cash-equivalent rebate value.
        System.out.println("Net cash cost: $" + transaction.calculateNetCashCost()); // Display the cost after cash-equivalent rebates.
        System.out.println("Non-cash rewards: $" + transaction.calculateNonCashRewards()); // Display the total non-cash reward value.
        System.out.println("Effective net cost: $" + transaction.calculateEffectiveNetCost()); // Display the cost after all reward value.
        System.out.println("Number of rewards: " + transaction.getRewards().size()); // Display the number of rewards associated with the transaction.

    }

}
