package coupons.repository;

import coupons.receipts.CouponTransaction;
import coupons.receipts.ReceiptTotals;
import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleReferenceValidationTest {
    private ReceiptWriteRequest request() { return new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()); }
    private ReceiptChangeContext context() { return new ReceiptChangeContext("Owner", "Reference verification", "Owner", Instant.now()); }

    @Test void missingAndWrongOwnerEvidenceAndMissingExistingRewardRejectEveryReceiptWrite() {
        for (String operation : List.of("UPDATE", "DELETE", "RESTORE")) {
            for (String damage : List.of("MISSING_EVIDENCE", "WRONG_OWNER", "MISSING_REWARD", "MISSING_REDEMPTION")) {
                var store = new InMemoryStore(); var repo = new InMemoryTransactionRepository(store, Clock.systemUTC());
                UUID id = UUID.randomUUID(); var reward = new RegisterReward("Existing RR", BigDecimal.ONE, null, null, null); repo.issuedRewards().save(reward);
                var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
                var redemption = new RegisterRewardRedemption(reward.getRewardId(), id, BigDecimal.ONE, null); tx.addRegisterRewardRedemption(redemption);
                var artifact = new ReceiptEvidence(UUID.randomUUID(), id, "text/plain", new byte[]{1});
                repo.save(id, tx, null, List.of(artifact), request(), new ReceiptCreateCtx("Owner"));
                if (operation.equals("RESTORE")) repo.softDelete(id, 1, request(), context());
                var c = store.state; var evidence = new HashMap<>(c.evidence()); var rewards = new HashMap<>(c.rewards()); var redemptions = new HashMap<>(c.redemptions());
                switch (damage) {
                    case "MISSING_EVIDENCE" -> evidence.clear();
                    case "WRONG_OWNER" -> evidence.put(artifact.getEvidenceId(), new ReceiptEvidence(artifact.getEvidenceId(), UUID.randomUUID(), "text/plain", new byte[]{1}));
                    case "MISSING_REWARD" -> rewards.clear();
                    case "MISSING_REDEMPTION" -> redemptions.clear();
                }
                store.state = new InMemoryStore.State(c.receipts(), rewards, redemptions, evidence, c.history(), c.requests(), c.rewardVoids(), c.giftCardPromotions(), c.balances(), c.rrUsageLinks());
                var before = store.state; var req = request();
                assertThrows(IllegalArgumentException.class, () -> {
                    switch (operation) {
                        case "UPDATE" -> repo.update(id, 1, tx, null, List.of(), req, context());
                        case "DELETE" -> repo.softDelete(id, 1, req, context());
                        default -> repo.restore(id, 2, req, context());
                    }
                }, operation + " / " + damage);
                assertSame(before, store.state); assertTrue(repo.history().findByRequestId(req.requestId()).isEmpty());
            }
        }
    }

    @Test void newRewardAndEvidenceReferencesAreValidatedBeforeCreation() {
        var store = new InMemoryStore(); var repo = new InMemoryTransactionRepository(store, Clock.systemUTC()); var before = store.state;
        UUID id = UUID.randomUUID(); var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        tx.addRegisterRewardRedemption(new RegisterRewardRedemption(UUID.randomUUID(), id, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, tx, null, List.of(), request(), new ReceiptCreateCtx("Owner")));
        assertSame(before, store.state);
        var valid = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        var foreign = new ReceiptEvidence(UUID.randomUUID(), UUID.randomUUID(), "text/plain", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> repo.save(id, valid, null, List.of(foreign), request(), new ReceiptCreateCtx("Owner")));
        assertSame(before, store.state);
    }
}
