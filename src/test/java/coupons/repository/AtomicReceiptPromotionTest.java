package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtomicReceiptPromotionTest {
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private GiftCardPromotion offer() {
        return new GiftCardPromotion(UUID.randomUUID(), "P&G online 75/25", new BigDecimal("75"), new BigDecimal("25"),
                LocalDate.of(2026,10,1), LocalDate.of(2026,10,31), GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,
                "Charmin only; reviewed online rules", GiftCardPromotion.Status.TRACKING, Map.of());
    }
    private CouponTransaction linked(GiftCardPromotion offer) {
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, new BigDecimal("35.96")));
        var charmin = new PurchaseItem("Charmin", null, null, new BigDecimal("5.49"), 4);
        tx.addPurchaseItem(charmin);
        tx.addPurchaseItem(new PurchaseItem("H&S", null, null, new BigDecimal("7"), 2));
        var link = new ReceiptPromotionLink(offer.id(), List.of(charmin.getLineItemId()), new BigDecimal("20.96"),
                "Charmin qualifies; H&S excluded", Instant.now());
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4), null, List.of(), List.of(), null, List.of(), Map.of(), null, List.of(link)));
        return tx;
    }

    @Test void atomicFailureRetryAndRestartRetainExactlyOneReceiptAndContribution(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("receipts.bin"), backup = dir.resolve("backup.bin");
        var offer = offer(); var tx = linked(offer); var id = UUID.randomUUID(); var req = request();
        var actor = new ReceiptCreateCtx("AJ"); ReceiptWriteResult result;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            repo.saveGiftCardPromotion(offer);
            byte[] before = Files.readAllBytes(file); Files.move(file, backup); Files.createDirectory(file);
            assertThrows(IllegalStateException.class, () -> repo.save(id, tx, null, List.of(), req, actor));
            assertTrue(repo.findById(id).isEmpty());
            assertTrue(repo.history().findByReceiptId(id).isEmpty());
            assertTrue(repo.giftCardPromotions().get(0).contributions().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(backup));
            Files.delete(file); Files.move(backup, file);
            result = repo.save(id, tx, null, List.of(), req, actor);
            assertEquals(result, repo.save(id, tx, null, List.of(), req, actor));
            assertEquals(1, repo.giftCardPromotions().get(0).contributions().size());
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(result, repo.save(id, tx, null, List.of(), req, actor));
            var saved = repo.findById(id).orElseThrow(); var offerNow = repo.giftCardPromotions().get(0);
            assertEquals(new BigDecimal("20.96"), offerNow.eligibleAmount(offerNow.contributions().get(id), saved).orElseThrow());
            assertEquals(1, saved.transaction().getObservations().promotionLinks().get(0).eligibleLineIds().size());
            assertTrue(saved.transaction().getObservations().earnings().isEmpty());
            assertEquals(1, repo.history().findByReceiptId(id).size());
        }
    }

    @Test void correctionDoesNotSilentlyReconfirmOldEligibilityAndExplicitReviewUpdatesAtomically() {
        var repo = new InMemoryTransactionRepository(); var offer = offer(); repo.saveGiftCardPromotion(offer);
        var tx = linked(offer); var id = UUID.randomUUID();
        repo.save(id, tx, null, List.of(), request(), new ReceiptCreateCtx("AJ"));
        var approval = new ReceiptChangeContext("AJ", "Receipt corrected", "AJ", Instant.now());
        repo.update(id, 1, tx, null, List.of(), request(), approval);
        var currentOffer = repo.giftCardPromotions().get(0);
        assertTrue(currentOffer.eligibleAmount(currentOffer.contributions().get(id), repo.findById(id).orElseThrow()).isEmpty());
        var obs = tx.getObservations(); var old = obs.promotionLinks().get(0);
        var revised = new ReceiptPromotionLink(old.promotionId(), old.eligibleLineIds(), new BigDecimal("19.96"), "Rechecked corrected receipt", Instant.now());
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(), null, obs.earnings(), obs.unidentifiedRR(), obs.walgreensCash(), obs.savings(), obs.discountKinds(), null, List.of(revised)));
        var req = request(); var result = repo.update(id, 2, tx, null, List.of(), req, approval);
        assertEquals(result, repo.update(id, 2, tx, null, List.of(), req, approval));
        var now = repo.giftCardPromotions().get(0);
        assertEquals(new BigDecimal("19.96"), now.eligibleAmount(now.contributions().get(id), repo.findById(id).orElseThrow()).orElseThrow());
        assertEquals(3, repo.history().findByReceiptId(id).size());
        assertEquals(new BigDecimal("20.96"), repo.history().findByRevision(id,1).orElseThrow().after().contents().observations().promotionLinks().get(0).reviewedEligibleAmount());
    }

    @Test void deletingReceiptEligibilityRemovesProgressOnceAndPreservesHistory() {
        var repo=new InMemoryTransactionRepository();var offer=offer();repo.saveGiftCardPromotion(offer);
        var tx=linked(offer);var id=UUID.randomUUID();repo.save(id,tx,null,List.of(),request(),new ReceiptCreateCtx("Owner"));
        var o=tx.getObservations();
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),o.earnings(),o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds(),null,List.of()));
        var req=request();var context=new ReceiptChangeContext("Owner","Remove eligibility","Owner",Instant.now());
        var result=repo.update(id,1,tx,null,List.of(),req,context);
        assertEquals(result,repo.update(id,1,tx,null,List.of(),req,context));
        assertTrue(repo.giftCardPromotions().get(0).contributions().isEmpty());
        assertEquals(1,repo.history().findByRevision(id,1).orElseThrow().after().contents().observations().promotionLinks().size());
        assertEquals(2,repo.history().findByReceiptId(id).size());
    }

    @Test void missingOfferRejectsEntireReceiptAndRetryPayloadIncludesEligibility() {
        var repo = new InMemoryTransactionRepository(); var offer = offer(); var tx = linked(offer); var id = UUID.randomUUID(); var req = request();
        var actor = new ReceiptCreateCtx("AJ");
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, tx, null, List.of(), req, actor));
        assertTrue(repo.findById(id).isEmpty());
        repo.saveGiftCardPromotion(offer); repo.save(id, tx, null, List.of(), req, actor);
        tx.setObservations(ReceiptObservations.empty());
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, tx, null, List.of(), req, actor));
        assertEquals(1, repo.history().findByReceiptId(id).size());
    }
    @Test void historicalObservationsWithoutPromotionLinksDeserialize(@TempDir Path dir) throws Exception {
        String source = """
            package coupons;
            import java.time.*; import java.util.*; import java.math.*;
            public record ReceiptObservations(LocalDate purchaseDate, String printedTime,
                    List earnings, List unidentifiedRR, WalgreensCashAccount walgreensCash,
                    List savings, Map discountKinds, BigDecimal receiptSavingValuePercent) implements java.io.Serializable {
                public record WalgreensCashAccount(BigDecimal startingBalance, BigDecimal used,
                        BigDecimal earned, BigDecimal endingBalance) implements java.io.Serializable {}
                public static Object legacy() {
                    return new ReceiptObservations(LocalDate.of(2026,10,4), "953am", List.of(), List.of(),
                        new WalgreensCashAccount(new BigDecimal("247.75"), BigDecimal.ZERO, BigDecimal.ONE,
                                new BigDecimal("248.75")), List.of(), Map.of(), null);
                }
            }
            """;
        Path src=dir.resolve("ReceiptObservations.java"); Files.writeString(src,source);
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);
        assertEquals(0,compiler.run(null,null,null,"-d",dir.toString(),src.toString()));
        byte[] bytes;
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.equals("coupons.ReceiptObservations") || name.startsWith("coupons.ReceiptObservations$")) {
                    Class<?> result=findLoadedClass(name);if(result==null) result=findClass(name);
                    if(resolve) resolveClass(result);return result;
                }
                return super.loadClass(name,resolve);
            }
        }) {
            var legacy=loader.loadClass("coupons.ReceiptObservations").getMethod("legacy").invoke(null);
            var buffer=new java.io.ByteArrayOutputStream();
            try(var output=new java.io.ObjectOutputStream(buffer)) { output.writeObject(legacy); }
            bytes=buffer.toByteArray();
        }
        try(var input=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var restored=(ReceiptObservations)input.readObject();
            assertTrue(restored.promotionLinks().isEmpty());
            assertEquals(new BigDecimal("247.75"),restored.walgreensCash().startingBalance());
            assertEquals("953am",restored.printedTime());
        }
    }
}
