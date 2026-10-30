package coupons.cli;

import coupons.receipts.CouponTransaction;
import coupons.receipts.ReceiptObservations;
import coupons.receipts.ReceiptTotals;

import coupons.promotions.BenefitKind;
import coupons.promotions.RebateBenefit;
import coupons.promotions.RebateStatus;

import coupons.rewards.ProviderBalanceSummary;

import coupons.repository.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactAccountDisplayTest {
    @Test void hidesZeroRowsAndTargetCombinedOpeningWithoutChangingStoredHistory() {
        var repo = new InMemoryTransactionRepository();
        var balances = new ProviderBalanceSummary(repo);
        var day = LocalDate.of(2026,10,4);
        balances.recordOpening(day,"Target",new BigDecimal("35"),true);
        balances.recordOpening(day,"Target Gift Card",new BigDecimal("15"),true);
        balances.recordOpening(day,"Target Circle",new BigDecimal("20"),true);
        balances.recordOpening(day,"Ibotta",BigDecimal.ZERO,true);
        var bytes = new ByteArrayOutputStream();
        balances.print(day,List.of(),new PrintStream(bytes));
        var output = bytes.toString();
        assertFalse(output.contains("Ibotta"),output);
        assertFalse(output.matches("(?s).*\\nTarget +\\|.*"),output);
        assertFalse(output.contains("Opening source"),output);
        assertTrue(output.matches("(?s).*USD value total +\\| +\\$35.00 +\\| +\\$35.00 +\\| +\\$35.00.*"),output);
        assertEquals(4,repo.accountBalances().openings().size());
    }

    @Test void activeCouponsUsePurchaseTimeRatherThanReceiptCreationOrderAndEmptySectionsStayHidden() {
        var repo = new InMemoryTransactionRepository();
        for (var time : List.of("12:15PM","9:05AM","10:10AM")) {
            var tx = new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));
            var earning = new ReceiptObservations.Earning(UUID.randomUUID(),ReceiptObservations.EarningKind.REGISTER_REWARD,
                    "Walgreens",null,ReceiptObservations.Scope.RECEIPT,List.of(),null,
                    new RebateBenefit(BenefitKind.STORE_COUPON,BigDecimal.ONE,Currency.getInstance("USD"),"Walgreens"),
                    RebateStatus.PAID,null,null,null,List.of());
            tx.setObservations(new ReceiptObservations(LocalDate.of(2026,10,4),time,List.of(earning),List.of(),null,List.of(),Map.of()));
            repo.save(UUID.randomUUID(),tx,new ReceiptMeta("Walgreens",time,null,null),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("AJ"));
        }
        var bytes = new ByteArrayOutputStream();
        new ReceiptEntryApp(new Scanner("5\n1\n5\n9\n"),new PrintStream(bytes),repo).run();
        var output = bytes.toString();
        var active = output.substring(output.indexOf("ACTIVE PRINTED RR"));
        assertTrue(active.indexOf("9:05AM") < active.indexOf("10:10AM"),active);
        assertTrue(active.indexOf("10:10AM") < active.indexOf("12:15PM"),active);
        assertFalse(output.contains("EXPECTED / OTHER RR OBSERVATIONS"),output);
        assertFalse(output.contains("SEPARATELY RECORDED PHYSICAL COUPONS"),output);
        assertFalse(output.contains("Recorded account balance;"),output);
    }
}
