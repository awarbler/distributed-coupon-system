package coupons.cli;

import coupons.rewards.RegisterReward;

import coupons.repository.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptMenuCleanupTest {
    @Test void balanceBackReturnsAndExitClosesWithoutBeingParsedAsDates() {
        for (String command : new String[]{"BACK", "EXIT"}) {
            var bytes = new ByteArrayOutputStream();
            var input = "BALANCE SETTINGS\n" + command + "\n" + (command.equals("BACK") ? "11\n" : "");
            new ReceiptEntryApp(new Scanner(input), new PrintStream(bytes)).run();
            String output = bytes.toString();
            assertFalse(output.contains("Enter a date such as"), output);
            assertFalse(output.contains("Input ended"), output);
            assertTrue(output.contains(command.equals("BACK") ? "Returned To Main Menu." : "CLI closed."));
            assertTrue(output.contains("\n\nMAIN MENU\n\n1. Enter"));
        }
    }

    @Test void canCancelThenRemoveMistakenSeparateCouponWithoutDeletingItsAuditRecord() {
        var repo = new InMemoryTransactionRepository();
        var reward = new RegisterReward("neutrogena", new BigDecimal("7.00"), null, null, null);
        repo.issuedRewards().save(reward);
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("5\n3\n1\nn\n3\n1\ny\nEntered by mistake\naj\n1\n5\n9\n"), new PrintStream(bytes), repo).run();
        assertTrue(repo.recordedRewards().isEmpty(), bytes.toString());
        assertEquals(1, repo.rewardRemovalHistory().size());
        assertEquals("aj", repo.rewardRemovalHistory().get(0).recordedBy());
        assertTrue(repo.issuedRewards().findById(reward.getRewardId()).isPresent());
        assertTrue(bytes.toString().contains("RR Removed From Active List"));
    }
}
