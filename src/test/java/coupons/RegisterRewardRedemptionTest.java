package coupons;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RegisterRewardRedemptionTest {

    @Test
    void recordsSpecificRewardReceiptAndAcceptedAmount() {
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 15);

        RegisterRewardRedemption redemption =
                new RegisterRewardRedemption(
                        rewardId,
                        receiptId,
                        new BigDecimal("5.00"),
                        date
                );

        assertNotNull(redemption.getRedemptionId());
        assertEquals(rewardId, redemption.getRewardId());
        assertEquals(receiptId, redemption.getRedeemedReceiptId());
        assertEquals(
                new BigDecimal("5.00"),
                redemption.getRedeemedAmount()
        );
        assertEquals(date, redemption.getRedeemedDate());
    }

    @Test
    void reconstructsExistingRedemptionId() {
        UUID redemptionId = UUID.randomUUID();

        RegisterRewardRedemption redemption =
                new RegisterRewardRedemption(
                        redemptionId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("5.00"),
                        null
                );

        assertEquals(redemptionId, redemption.getRedemptionId());
        assertNull(redemption.getRedeemedDate());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        for (String amount : List.of("0.00", "-5.00")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RegisterRewardRedemption(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            new BigDecimal(amount),
                            null
                    )
            );
        }
    }

    @Test
    void rejectsNullRequiredFields() {
        UUID redemptionId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5.00");

        assertThrows(
                NullPointerException.class,
                () -> new RegisterRewardRedemption(
                        null, rewardId, receiptId, amount, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new RegisterRewardRedemption(
                        redemptionId, null, receiptId, amount, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new RegisterRewardRedemption(
                        redemptionId, rewardId, null, amount, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new RegisterRewardRedemption(
                        redemptionId, rewardId, receiptId, null, null
                )
        );
    }

    @Test
    void correctionPreservesIdentityAndOriginalAmount() {
        RegisterRewardRedemption original =
                new RegisterRewardRedemption(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("5.00"),
                        null
                );

        RegisterRewardRedemption corrected =
                new RegisterRewardRedemption(
                        original.getRedemptionId(),
                        original.getRewardId(),
                        original.getRedeemedReceiptId(),
                        new BigDecimal("4.50"),
                        original.getRedeemedDate()
                );

        assertNotSame(original, corrected);
        assertEquals(
                original.getRedemptionId(),
                corrected.getRedemptionId()
        );
        assertEquals(original.getRewardId(), corrected.getRewardId());
        assertEquals(
                original.getRedeemedReceiptId(),
                corrected.getRedeemedReceiptId()
        );
        assertEquals(
                new BigDecimal("5.00"),
                original.getRedeemedAmount()
        );
        assertEquals(
                new BigDecimal("4.50"),
                corrected.getRedeemedAmount()
        );
    }
}