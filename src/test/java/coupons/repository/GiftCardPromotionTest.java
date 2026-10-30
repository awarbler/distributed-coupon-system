package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.rewards.ProviderBalanceSummary;
import coupons.receipts.PurchaseItem;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterReward;

import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GiftCardPromotionTest {
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private final ReceiptChangeContext approval = new ReceiptChangeContext("AJ", "Correction", "AJ", Instant.now());
    private UUID save(InMemoryTransactionRepository repo, String price) {
        var id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, new BigDecimal(price)));
        tx.addPurchaseItem(new PurchaseItem("Eligible P&G", null, null, new BigDecimal(price), 1));
        tx.setObservations(new ReceiptObservations(LocalDate.of(2026, 10, 4), null, List.of(), List.of(), null, List.of(), Map.of()));
        repo.save(id, tx, new ReceiptMeta("Walgreens", id.toString(), null, null), List.of(), request(), new ReceiptCreateCtx("AJ"));
        return id;
    }
    private GiftCardPromotion offer(GiftCardPromotion.Basis basis) {
        return new GiftCardPromotion(UUID.randomUUID(), "P&G online", new BigDecimal("50"), new BigDecimal("10"),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), basis, "User-confirmed eligible products", GiftCardPromotion.Status.TRACKING, Map.of());
    }
    private GiftCardPromotion.Contribution link(InMemoryTransactionRepository repo, UUID id, BigDecimal amount) {
        var receipt = repo.findById(id).orElseThrow();
        return new GiftCardPromotion.Contribution(id, receipt.revision(), receipt.transaction().getPurchaseItems().stream()
                .map(PurchaseItem::getLineItemId).toList(), amount, "Offer terms checked");
    }
    private BigDecimal total(InMemoryTransactionRepository repo, GiftCardPromotion offer) {
        return offer.contributions().values().stream().map(link -> offer.eligibleAmount(link, repo.findById(link.receiptId()).orElseThrow()).orElseThrow())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    @Test void afterCouponReceiptLinksAreExactAndSubmissionDoesNotIssueFunds()throws Exception {
        var repo=new InMemoryTransactionRepository();var id=save(repo,"20.0000");var receipt=repo.findById(id).orElseThrow();
        var offer=offer(GiftCardPromotion.Basis.SALE_BEFORE_COUPONS);
        var lines=receipt.transaction().getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList();
        var link=new GiftCardPromotion.Contribution(id,1,lines,new BigDecimal("15.00125"),"After product coupons",true,true,LocalDate.of(2026,10,5),"claim-123");
        assertEquals(new BigDecimal("15.00125"),offer.eligibleAmount(link,receipt).orElseThrow());assertTrue(link.submitted());
        assertEquals(GiftCardPromotion.Status.TRACKING,offer.withContribution(link).status());
        var changed=new ReceiptRecord(id,receipt.status(),2,receipt.transaction(),receipt.metadata(),receipt.evidenceIds());assertTrue(offer.eligibleAmount(link,changed).isEmpty());
        var bytes=new java.io.ByteArrayOutputStream();try(var out=new java.io.ObjectOutputStream(bytes)){out.writeObject(offer.withContribution(link));}
        GiftCardPromotion loaded;try(var in=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))){loaded=(GiftCardPromotion)in.readObject();}
        assertEquals(link,loaded.contributions().get(id));assertEquals(GiftCardPromotion.contributionToken(link),GiftCardPromotion.contributionToken(loaded.contributions().get(id)));
        assertNotEquals(GiftCardPromotion.contributionToken(link),GiftCardPromotion.contributionToken(new GiftCardPromotion.Contribution(id,1,lines,new BigDecimal("15"),"Changed")));
        assertTrue(new ProviderBalanceSummary().movements(List.of(receipt)).isEmpty());assertEquals(1,receipt.revision());
    }
    @Test void multipleReceiptsRecomputeOnceAfterCorrectionAndRestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("receipts.bin");
        UUID first;
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            first = save(repo, "35.96"); var second = save(repo, "15.00");
            var offer = offer(GiftCardPromotion.Basis.SALE_BEFORE_COUPONS).withContribution(link(repo, first, null)).withContribution(link(repo, second, null));
            repo.saveGiftCardPromotion(offer);
            repo.saveGiftCardPromotion(offer.withContribution(link(repo, first, null)));
            assertEquals(2, repo.giftCardPromotions().get(0).contributions().size());
            assertEquals(new BigDecimal("50.96"), total(repo, offer));
            var before = repo.findById(first).orElseThrow();
            var snap = ReceiptSnapshot.from(before.transaction()); var line = snap.purchaseItems().get(0);
            var updated = new ReceiptSnapshot(List.of(new PurchaseLineSnap(line.lineItemId(), line.productName(), null, null, new BigDecimal("30.00"), 1)),
                    snap.coupons(), snap.appliedCoupons(), snap.promotions(), snap.payments(), snap.rewards(), snap.registerRewardRedemptions(), snap.receiptTotals(), snap.observations());
            var req = request();
            var result = repo.update(first, 1, updated.toTransaction(), before.metadata(), List.of(), req, approval);
            assertEquals(result, repo.update(first, 1, updated.toTransaction(), before.metadata(), List.of(), req, approval));
            assertEquals(new BigDecimal("45.00"), total(repo, repo.giftCardPromotions().get(0)));
            // Standalone instrument writes must not discard offer settings.
            repo.issuedRewards().save(new RegisterReward("Other", BigDecimal.ONE, null, null, null));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(new BigDecimal("45.00"), total(repo, repo.giftCardPromotions().get(0)));
            assertEquals(2, repo.history().findByReceiptId(first).size());
        }
    }

    @Test void reviewedAmountsRequireReviewAfterRevisionAndCannotBeSubmittedAgainstStaleRevision() {
        var repo = new InMemoryTransactionRepository(); var id = save(repo, "35.96");
        var offer = offer(GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT).withContribution(link(repo, id, new BigDecimal("24.96")));
        repo.saveGiftCardPromotion(offer);
        var receipt = repo.findById(id).orElseThrow();
        repo.update(id, 1, receipt.transaction(), receipt.metadata(), List.of(), request(), approval);
        assertTrue(offer.eligibleAmount(offer.contributions().get(id), repo.findById(id).orElseThrow()).isEmpty());
        var staleNewOffer = offer(GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT).withContribution(offer.contributions().get(id));
        assertThrows(IllegalArgumentException.class, () -> repo.saveGiftCardPromotion(staleNewOffer));
        repo.saveGiftCardPromotion(offer.withStatus(GiftCardPromotion.Status.SUBMITTED));
        var refreshed = offer.withContribution(link(repo, id, new BigDecimal("24.96")));
        repo.saveGiftCardPromotion(refreshed);
        assertEquals(new BigDecimal("24.96"), total(repo, refreshed));
    }

    @Test void failedOfferWriteLeavesPreviousStateAndCanRetry(@TempDir Path dir) throws Exception {
        var file = dir.resolve("receipts.bin"); var backup = dir.resolve("backup.bin");
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            save(repo, "35.96");
            var offer = offer(GiftCardPromotion.Basis.SALE_BEFORE_COUPONS);
            byte[] before = Files.readAllBytes(file);
            Files.move(file, backup); Files.createDirectory(file);
            assertThrows(IllegalStateException.class, () -> repo.saveGiftCardPromotion(offer));
            assertTrue(repo.giftCardPromotions().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(backup));
            Files.delete(file); Files.move(backup, file);
            repo.saveGiftCardPromotion(offer);
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) { assertEquals(1, repo.giftCardPromotions().size()); }
    }

    @Test void outOfPeriodOrUnknownDatesAreNotCounted() {
        var repo = new InMemoryTransactionRepository(); var id = save(repo, "35.96"); var link = link(repo, id, null);
        var offer = offer(GiftCardPromotion.Basis.SALE_BEFORE_COUPONS);
        var receipt = repo.findById(id).orElseThrow();
        receipt.transaction().setObservations(ReceiptObservations.empty());
        assertTrue(offer.eligibleAmount(link, receipt).isEmpty());
    }
    @Test void readsCheckpointBeforePromotionFieldExisted(@TempDir Path dir) throws Exception {
        // Generate the former seven-field State shape with its original binary name.
        String source = """
                package coupons.repository;
                import java.util.*;
                public class InMemoryStore {
                    record State(Map receipts, Map rewards, Map redemptions, Map evidence,
                                 Map history, Map requests, Map rewardVoids) implements java.io.Serializable {}
                    public static Object legacy(UUID id, Object receipt) {
                        return new State(Map.of(id, receipt), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
                    }
                }
                """;
        Path src = dir.resolve("InMemoryStore.java"); Files.writeString(src, source);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-d", dir.toString(), src.toString()));
        var id = UUID.randomUUID();
        var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ONE));
        var snap = new ReceiptRevSnap(id, 1, ReceiptStatus.ACTIVE, ReceiptSnapshot.from(tx), null, List.of());
        Path file = dir.resolve("legacy.bin");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{dir.toUri().toURL()}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("coupons.repository.InMemoryStore") || name.startsWith("coupons.repository.InMemoryStore$")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Object legacy = loader.loadClass("coupons.repository.InMemoryStore").getMethod("legacy", UUID.class, Object.class).invoke(null, id, snap);
            try (var output = new java.io.ObjectOutputStream(Files.newOutputStream(file))) {
                output.writeUTF("coupon-receipts-v1"); output.writeObject(legacy);
            }
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertEquals(ReceiptSnapshot.from(tx), ReceiptSnapshot.from(repo.findById(id).orElseThrow().transaction()));
            assertTrue(repo.giftCardPromotions().isEmpty());
            assertTrue(repo.accountBalances().openings().isEmpty());
            repo.saveGiftCardPromotion(offer(GiftCardPromotion.Basis.SALE_BEFORE_COUPONS));
        }
        try (var repo = InMemoryTransactionRepository.openLocal(file)) {
            assertTrue(repo.findById(id).isPresent());
            assertEquals(1, repo.giftCardPromotions().size());
        }
    }
}
