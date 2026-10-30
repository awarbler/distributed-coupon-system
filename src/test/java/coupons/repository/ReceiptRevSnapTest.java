package coupons.repository;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import coupons.receipts.CouponTransaction;
import coupons.receipts.Payment;
import coupons.receipts.PaymentType;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterRewardRedemption;

/**
 * Tests immutable capture of receipt identity and lifecycle state.
 */
class ReceiptRevSnapTest {

    /**
     * Creates empty transaction contents.
     *
     * @return a transaction with zero recorded totals
     */
    private CouponTransaction newTransaction() {
        return new CouponTransaction(
                new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    /**
     * Verifies preservation of identity, revision, and status.
     */
    @Test
    void capturesReceiptMetadata() {
        UUID id = UUID.randomUUID();
        CouponTransaction transaction = newTransaction();

        ReceiptRevSnap snapshot = ReceiptRevSnap.from(
                new ReceiptRecord(id, ReceiptStatus.ACTIVE, 3, transaction)
        );

        assertEquals(id, snapshot.transactionId());
        assertEquals(3L, snapshot.revision());
        assertEquals(ReceiptStatus.ACTIVE, snapshot.status());
        assertEquals(
                ReceiptSnapshot.from(transaction),
                snapshot.contents()
        );
    }

    /**
     * Verifies that deletion preserves separate before and after states.
     */
    @Test
    void distinguishesDeletionFromActiveState() {
        UUID id = UUID.randomUUID();
        CouponTransaction transaction = newTransaction();

        ReceiptRevSnap before = ReceiptRevSnap.from(
                new ReceiptRecord(id, ReceiptStatus.ACTIVE, 1, transaction)
        );

        ReceiptRevSnap after = ReceiptRevSnap.from(
                new ReceiptRecord(id, ReceiptStatus.DELETED, 2, transaction)
        );

        assertEquals(before.transactionId(), after.transactionId());
        assertEquals(before.contents(), after.contents());
        assertEquals(ReceiptStatus.ACTIVE, before.status());
        assertEquals(ReceiptStatus.DELETED, after.status());
        assertNotEquals(before, after);
    }

    /**
     * Verifies isolation from subsequent transaction additions.
     */
    @Test
    void laterChangesDoNotAlterCapturedRevision() {
        CouponTransaction transaction = newTransaction();

        ReceiptRevSnap snapshot = ReceiptRevSnap.from(
                new ReceiptRecord(
                        UUID.randomUUID(),
                        ReceiptStatus.ACTIVE,
                        1,
                        transaction
                )
        );

        transaction.addPayment(new Payment(
                PaymentType.CASH,
                new BigDecimal("1.00")
        ));

        assertTrue(snapshot.contents().payments().isEmpty());
        assertEquals(1, transaction.getPayments().size());
    }

    /**
     * Verifies rejection of inconsistent redemption receipt references.
     */
    @Test
    void rejectsMismatchedRedemption() {
        CouponTransaction transaction = newTransaction();

        transaction.addRegisterRewardRedemption(
                new RegisterRewardRedemption(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("5.00"),
                        null
                )
        );

        ReceiptRecord receipt = new ReceiptRecord(
                UUID.randomUUID(),
                ReceiptStatus.ACTIVE,
                1,
                transaction
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptRevSnap.from(receipt)
        );
    }

    /**
     * Verifies rejection of missing fields and invalid revisions.
     */
    @Test
    void rejectsInvalidInputs() {
        UUID id = UUID.randomUUID();
        ReceiptSnapshot contents = ReceiptSnapshot.from(newTransaction());

        assertThrows(
                NullPointerException.class,
                () -> ReceiptRevSnap.from(null)
        );

        assertThrows(
                NullPointerException.class,
                () -> new ReceiptRevSnap(
                        null, 1, ReceiptStatus.ACTIVE, contents
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ReceiptRevSnap(id, 1, null, contents)
        );

        assertThrows(
                NullPointerException.class,
                () -> new ReceiptRevSnap(
                        id, 1, ReceiptStatus.ACTIVE, null
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceiptRevSnap(
                        id, 0, ReceiptStatus.ACTIVE, contents
                )
        );
    }
}