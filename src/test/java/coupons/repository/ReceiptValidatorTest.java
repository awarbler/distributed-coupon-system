package coupons.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import coupons.CouponTransaction;
import coupons.ReceiptTotals;
import coupons.RegisterRewardRedemption;

/**
 * Tests receipt-ID consistency for Register Reward redemptions.
 */
class ReceiptValidatorTest {

    /**
     * Creates a receipt with empty transaction contents.
     *
     * @param receiptId identifier of the receipt
     * @return an active receipt at revision one
     */
    private ReceiptRecord newReceipt(UUID receiptId) {
        return new ReceiptRecord(
                receiptId,
                ReceiptStatus.ACTIVE,
                1,
                new CouponTransaction(
                        new ReceiptTotals(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO
                        )
                )
        );
    }

    /**
     * Creates a redemption referencing the supplied receipt.
     *
     * @param receiptId receipt referenced by the redemption
     * @return a redemption with a distinct reward identifier
     */
    private RegisterRewardRedemption newRedemption(UUID receiptId) {
        return new RegisterRewardRedemption(
                UUID.randomUUID(),
                receiptId,
                new BigDecimal("5.00"),
                null
        );
    }

    /**
     * Verifies that receipts without redemptions are valid.
     */
    @Test
    void acceptsReceiptWithoutRedemptions() {
        ReceiptRecord receipt = newReceipt(UUID.randomUUID());

        assertDoesNotThrow(() -> ReceiptValidator.validate(receipt));
    }

    /**
     * Verifies that multiple redemptions may reference one receipt.
     */
    @Test
    void acceptsMatchingReceiptIds() {
        UUID receiptId = UUID.randomUUID();
        ReceiptRecord receipt = newReceipt(receiptId);

        receipt.transaction().addRegisterRewardRedemption(
                newRedemption(receiptId)
        );
        receipt.transaction().addRegisterRewardRedemption(
                newRedemption(receiptId)
        );

        assertDoesNotThrow(() -> ReceiptValidator.validate(receipt));
        assertEquals(
                2,
                receipt.transaction().getRegisterRewardRedemptions().size()
        );
    }

    /**
     * Verifies rejection without modifying transaction contents.
     */
    @Test
    void rejectsMismatchWithoutChangingReceipt() {
        UUID receiptId = UUID.randomUUID();
        ReceiptRecord receipt = newReceipt(receiptId);

        RegisterRewardRedemption matching = newRedemption(receiptId);
        RegisterRewardRedemption mismatched =
                newRedemption(UUID.randomUUID());

        receipt.transaction().addRegisterRewardRedemption(matching);
        receipt.transaction().addRegisterRewardRedemption(mismatched);

        List<RegisterRewardRedemption> before =
                receipt.transaction().getRegisterRewardRedemptions();

        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptValidator.validate(receipt)
        );

        assertEquals(
                before,
                receipt.transaction().getRegisterRewardRedemptions()
        );
        assertEquals(receiptId, receipt.transactionId());
        assertEquals(ReceiptStatus.ACTIVE, receipt.status());
        assertEquals(1L, receipt.revision());
    }

    /**
     * Verifies that validation must be repeated after content changes.
     */
    @Test
    void detectsMismatchAddedAfterSuccessfulValidation() {
        UUID receiptId = UUID.randomUUID();
        ReceiptRecord receipt = newReceipt(receiptId);

        receipt.transaction().addRegisterRewardRedemption(
                newRedemption(receiptId)
        );

        assertDoesNotThrow(() -> ReceiptValidator.validate(receipt));

        receipt.transaction().addRegisterRewardRedemption(
                newRedemption(UUID.randomUUID())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptValidator.validate(receipt)
        );
    }

    /**
     * Verifies that null receipts are rejected.
     */
    @Test
    void rejectsNullReceipt() {
        assertThrows(
                NullPointerException.class,
                () -> ReceiptValidator.validate(null)
        );
    }
}