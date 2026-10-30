package coupons.rewards;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRewardTest {

    @Test
    void identicalRewardsReceiveDistinctIds() {
        LocalDate earned = LocalDate.of(2026, 10, 1);
        LocalDate expires = LocalDate.of(2026, 10, 15);
        UUID receiptId = UUID.randomUUID();

        RegisterReward first = new RegisterReward(
                "Example promotion",
                new BigDecimal("5.00"),
                earned,
                expires,
                receiptId
        );

        RegisterReward second = new RegisterReward(
                "Example promotion",
                new BigDecimal("5.00"),
                earned,
                expires,
                receiptId
        );

        assertNotNull(first.getRewardId());
        assertNotNull(second.getRewardId());
        assertNotEquals(first.getRewardId(), second.getRewardId());
    }

    @Test
    void reconstructsAllStoredValues() {
        UUID rewardId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        LocalDate earned = LocalDate.of(2026, 10, 1);
        LocalDate expires = LocalDate.of(2026, 10, 15);

        RegisterReward reward = new RegisterReward(
                rewardId,
                "  Example promotion  ",
                new BigDecimal("5.00"),
                earned,
                expires,
                receiptId
        );

        assertEquals(rewardId, reward.getRewardId());
        assertEquals("  Example promotion  ", reward.getSourceDescription());
        assertEquals(new BigDecimal("5.00"), reward.getFaceValue());
        assertEquals(earned, reward.getEarnedDate());
        assertEquals(expires, reward.getExpirationDate());
        assertEquals(receiptId, reward.getEarnedReceiptId());
    }

    @Test
    void allowsUnknownDatesAndEarningReceipt() {
        RegisterReward reward = new RegisterReward(
                "Unknown source",
                new BigDecimal("7.00"),
                null,
                null,
                null
        );

        assertNull(reward.getEarnedDate());
        assertNull(reward.getExpirationDate());
        assertNull(reward.getEarnedReceiptId());
    }

    @Test
    void allowsEitherDateToBeUnknown() {
        LocalDate date = LocalDate.of(2026, 10, 15);

        RegisterReward unknownEarnedDate = new RegisterReward(
                "Unknown source", new BigDecimal("5.00"),
                null, date, null
        );

        RegisterReward unknownExpiration = new RegisterReward(
                "Unknown source", new BigDecimal("5.00"),
                date, null, null
        );

        assertEquals(date, unknownEarnedDate.getExpirationDate());
        assertEquals(date, unknownExpiration.getEarnedDate());
    }

    @Test
    void rejectsZeroAndNegativeFaceValues() {
        for (String amount : List.of("0.00", "-5.00")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RegisterReward(
                            "Example promotion",
                            new BigDecimal(amount),
                            null, null, null
                    )
            );
        }
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(
                NullPointerException.class,
                () -> new RegisterReward(
                        null, "Example promotion",
                        new BigDecimal("5.00"),
                        null, null, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new RegisterReward(
                        (String) null, new BigDecimal("5.00"),
                        null, null, null
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new RegisterReward(
                        "Example promotion", null,
                        null, null, null
                )
        );
    }

    @Test
    void rejectsBlankSourceDescriptions() {
        for (String source : List.of("", "   ")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RegisterReward(
                            source, new BigDecimal("5.00"),
                            null, null, null
                    )
            );
        }
    }

    @Test
    void rejectsExpirationBeforeEarning() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RegisterReward(
                        "Example promotion",
                        new BigDecimal("5.00"),
                        LocalDate.of(2026, 10, 15),
                        LocalDate.of(2026, 10, 1),
                        null
                )
        );
    }

    @Test
    void allowsEarningAndExpirationOnSameDate() {
        LocalDate date = LocalDate.of(2026, 10, 15);

        RegisterReward reward = new RegisterReward(
                "Example promotion", new BigDecimal("5.00"),
                date, date, null
        );

        assertEquals(date, reward.getEarnedDate());
        assertEquals(date, reward.getExpirationDate());
    }

    @Test
    void correctionPreservesIdentityWithoutChangingOriginal() {
        RegisterReward original = new RegisterReward(
                "Unknown source", new BigDecimal("5.00"),
                null, null, null
        );

        RegisterReward corrected = new RegisterReward(
                original.getRewardId(),
                "Confirmed source",
                original.getFaceValue(),
                original.getEarnedDate(),
                original.getExpirationDate(),
                original.getEarnedReceiptId()
        );

        assertNotSame(original, corrected);
        assertEquals(original.getRewardId(), corrected.getRewardId());
        assertEquals("Unknown source", original.getSourceDescription());
        assertEquals("Confirmed source", corrected.getSourceDescription());
    }
}