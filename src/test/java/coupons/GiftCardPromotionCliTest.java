package coupons;

import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiftCardPromotionCliTest {
    @Test void createsLinksViewsAndNotesPromotionWithoutCreditingGiftCardBalance() {
        var repo = new InMemoryTransactionRepository();
        var id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, new BigDecimal("35.96")));
        tx.addPurchaseItem(new PurchaseItem("Charmin", null, null, new BigDecimal("5.49"), 4));
        tx.addPurchaseItem(new PurchaseItem("H&S shampoo", null, null, new BigDecimal("7.00"), 1));
        tx.addPurchaseItem(new PurchaseItem("H&S conditioner", null, null, new BigDecimal("7.00"), 1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026, 10, 4), null, List.of(), List.of(), null, List.of(), Map.of()));
        repo.save(id, tx, new ReceiptMeta("Walgreens", "Oct4", null, null), List.of(),
                new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx("AJ"));
        // Illustrative offer terms entered by the user, not a built-in P&G offer.
        String script = String.join("\n", "PROMOTIONS", "NEW", "Online gift-card offer", "50", "10", "10/1/2026", "10/31/2026", "1",
                "Selected P&G products; sale prices before coupons", "y", "LINK", "1", "Oct4", "A", "Eligible UPCs checked", "y",
                "LINK", "1", "Oct4", "A", "Same receipt reviewed again", "y", "VIEW", "1", "BACK", "9") + "\n";
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner(script), new PrintStream(bytes), repo).run();
        String output = bytes.toString();
        assertFalse(output.contains("Operation not saved:"), output);
        assertTrue(output.contains("Qualifying spend: $35.96 / $50.00"), output);
        assertTrue(output.contains("| H&S shampoo") && output.contains("| H&S conditioner"), output);
        var offer = repo.giftCardPromotions().get(0);
        assertEquals(1, offer.contributions().size());
        assertEquals("Same receipt reviewed again", offer.contributions().get(id).note());
        assertEquals(3, offer.contributions().get(id).lineIds().size());
        assertTrue(repo.findById(id).orElseThrow().transaction().getObservations().earnings().isEmpty());
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }
}
