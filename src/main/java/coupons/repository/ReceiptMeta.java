package coupons.repository;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Preserves recorded receipt-identification information.
 *
 * <p>Text retains leading zeros. Complete Walgreens RFNs use five groups of four digits.
 * Unknown optional values are null rather than invented placeholders.
 * The timestamp is printed local time, without an assumed time zone.
 * These fields support possible matches, not proof of duplication.
 *
 * @param retailer recorded retailer name
 * @param receiptNumber printed receipt reference, or null if unknown
 * @param purchasedAt printed local purchase date/time, or null if unknown
 * @param storeReference printed store header/address/phone, or null if unknown
 * @param registerNumber printed register identifier, retaining leading zeros, or null if unknown
 * @param transactionNumber printed transaction number, separate from the RFN, or null if unknown
 */
public record ReceiptMeta(
        String retailer,
        String receiptNumber,
        LocalDateTime purchasedAt,
        String storeReference,
        PurchaseChannel purchaseChannel,
        PrintedTotalBasis printedTotalBasis,
        PaymentEntryStatus paymentEntryStatus, ReceiptKind receiptKind, String transactionNumber, String registerNumber
) implements java.io.Serializable {
    public ReceiptMeta(String retailer,String number,LocalDateTime at,String store,PurchaseChannel channel,PrintedTotalBasis basis,PaymentEntryStatus payments,ReceiptKind kind,String transaction){this(retailer,number,at,store,channel,basis,payments,kind,transaction,null);}
    public ReceiptMeta(String retailer,String number,LocalDateTime at,String store,PurchaseChannel channel,PrintedTotalBasis basis,PaymentEntryStatus payments,ReceiptKind kind){this(retailer,number,at,store,channel,basis,payments,kind,null);}
    public static String formatReceiptNumber(String retailer,String number) {
        if(number==null||retailer==null||!retailer.trim().matches("(?i)^walgreens(?:\\s|#|$).*"))return number;
        String digits=number.replaceAll("\\D","");
        return digits.length()==20?String.join("-",digits.split("(?<=\\G.{4})")):number;
    }
    public enum ReceiptKind { PURCHASE, REWARD_ADJUSTMENT }
    public ReceiptMeta(String retailer,String number,LocalDateTime at,String store,PurchaseChannel channel,PrintedTotalBasis basis,PaymentEntryStatus payments){this(retailer,number,at,store,channel,basis,payments,ReceiptKind.PURCHASE);}
    public enum PurchaseChannel { IN_STORE, ONLINE, UNKNOWN }
    public enum PrintedTotalBasis { BEFORE_PAYMENT_FUNDING, AFTER_REWARD_REDEMPTIONS, UNKNOWN }
    public enum PaymentEntryStatus { COMPLETE, PARTIAL, UNKNOWN }
    public ReceiptMeta(String retailer, String receiptNumber, LocalDateTime purchasedAt,
            String storeReference, PurchaseChannel purchaseChannel) {
        this(retailer, receiptNumber, purchasedAt, storeReference, purchaseChannel,
                PrintedTotalBasis.UNKNOWN, PaymentEntryStatus.UNKNOWN);
    }
    public ReceiptMeta(String retailer,String receiptNumber,LocalDateTime purchasedAt,String storeReference) {
        this(retailer,receiptNumber,purchasedAt,storeReference,PurchaseChannel.UNKNOWN);
    }
    /**
     * Validates metadata and formats complete Walgreens RFNs.
     *
     * <p>If only a date is known, do not invent a midnight time.
     * Original source evidence must preserve unsupported information.
     *
     * @param retailer recorded retailer name
     * @param receiptNumber printed reference, or null if unknown
     * @param purchasedAt printed local date/time, or null if unknown
     * @param storeReference printed store identifier, or null if unknown
     * @throws NullPointerException if retailer is null
     * @throws IllegalArgumentException if retailer is blank or a supplied
     *         optional text field is blank
     */
    public ReceiptMeta {
        if(registerNumber!=null&&registerNumber.isBlank())throw new IllegalArgumentException("Register number must be null or nonblank.");
        receiptNumber=formatReceiptNumber(retailer,receiptNumber);
        if(transactionNumber!=null&&transactionNumber.isBlank())throw new IllegalArgumentException("Transaction number must be null or nonblank.");
        receiptKind=receiptKind==null?ReceiptKind.PURCHASE:receiptKind;
        printedTotalBasis = printedTotalBasis == null ? PrintedTotalBasis.UNKNOWN : printedTotalBasis;
        paymentEntryStatus = paymentEntryStatus == null ? PaymentEntryStatus.UNKNOWN : paymentEntryStatus;
        purchaseChannel=purchaseChannel==null?PurchaseChannel.UNKNOWN:purchaseChannel;
        Objects.requireNonNull(retailer, "Retailer cannot be null.");
        if (retailer.isBlank()) {
            throw new IllegalArgumentException("Retailer cannot be blank.");
        }
        if (receiptNumber != null && receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number must be null or nonblank.");
        }
        if (storeReference != null && storeReference.isBlank()) {
            throw new IllegalArgumentException("Store reference must be null or nonblank.");
        }
    }
}
