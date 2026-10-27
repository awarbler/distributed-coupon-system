package coupons.repository;

import coupons.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Child JVM probe: no source files or personal database settings are read. */
public final class ReceiptFinancialRestartProbe {
    public static void main(String[] args) throws Exception {
        var target = PostgresTestDatabase.targetForSchema(args[0]);
        var factory = new PostgresConnectionFactory(System.getenv("COUPON_POSTGRES_TEST_URL"),
                System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        var repository = PostgresTransactionRepository.withFinancialProjections(factory,target);
        UUID id=UUID.fromString(args[1]);
        if (args[2].equals("write")) {
            var tx=new CouponTransaction(new ReceiptTotals(new BigDecimal("1.31"),new BigDecimal("7.28")));
            tx.addPurchaseItem(new PurchaseItem("Synthetic restart item",null,new BigDecimal("10.0000"),new BigDecimal("5.96"),1));
            tx.addPayment(new Payment(PaymentType.CASH,new BigDecimal("7.28")));
            repository.save(id,tx,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("process-test"));
        } else {
            var tx=repository.findById(id).orElseThrow().transaction();
            var calculator=new TransactionCalculator();
            if (calculator.calculateExpectedCheckoutAmount(tx).compareTo(new BigDecimal("7.27"))!=0
                    || tx.getReceiptTotals().getFinalTotal().compareTo(new BigDecimal("7.28"))!=0
                    || calculator.calculateReceiptDifference(tx).compareTo(new BigDecimal("0.01"))!=0) {
                throw new AssertionError("Restart changed the receipt's exact baseline.");
            }
            if (!ReceiptFinancialProjection.backfill(factory,target,UUID.randomUUID(),10).complete()) throw new AssertionError("Restart backfill incomplete.");
        }
    }
}
