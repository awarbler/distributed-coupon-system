package coupons;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;


class WorkbookTransactionTest {
    @Test 
    void sheetOneTransactionPreserversRecordedItemsAndPayments(){
        CouponTransaction transaction = 
                new CouponTransaction(
                    new ReceiptTotals(BigDecimal.valueOf(1.04), BigDecimal.valueOf(3.60))
                );
        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Neutrogena",
                        "07050111090",
                        BigDecimal.valueOf(7.79)        ,
                        BigDecimal.valueOf(5.99),
                        2
                )
        );
        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Lysol travel wipes",
                        "01920099717",
                        BigDecimal.valueOf(3.29),
                        BigDecimal.valueOf(3.29),
                        2
                )
        );
        transaction.addPayment(
                new Payment(PaymentType.REGISTER_REWARD, BigDecimal.valueOf(2.00  ))
        );
        transaction.addPayment(
                new Payment(PaymentType.WALGREENS_CASH, BigDecimal.valueOf(10.00))
        );

        transaction.addPayment(
                new Payment(PaymentType.CASH, BigDecimal.valueOf(3.60))
        );

        PurchaseItem neutrogena =
                transaction.getPurchaseItems().get(0);

        PurchaseItem lysol =
                transaction.getPurchaseItems().get(1);

        assertEquals(
                "Neutrogena",
                neutrogena.getProductName()
        );

        assertEquals(
                "07050111090",
                neutrogena.getProductCode()
        );

        assertEquals(
                2,
                neutrogena.getQuantity()
        );

        assertEquals(
                BigDecimal.valueOf(15.58),
                neutrogena.getRegularUnitPrice()
                        .multiply(BigDecimal.valueOf(neutrogena.getQuantity()))
        );

        assertEquals(
                BigDecimal.valueOf(6.58),
                lysol.calculateLineTotal()
        );

        assertEquals(
                BigDecimal.valueOf(1.04),
                transaction.getReceiptTotals().getTaxAmount()
        );

        assertEquals(
                BigDecimal.valueOf(3.60),
                transaction.getReceiptTotals().getFinalTotal()
        );

        assertEquals(
                3,
                transaction.getPayments().size()
        );

        assertEquals(
                PaymentType.REGISTER_REWARD,
                transaction.getPayments().get(0).getPaymentType()
        );

        assertEquals(
                BigDecimal.valueOf(2.00),
                transaction.getPayments().get(0).getAmount()
        );

        assertEquals(
                PaymentType.WALGREENS_CASH,
                transaction.getPayments().get(1).getPaymentType()
        );

        assertEquals(
                BigDecimal.valueOf(10.00),
                transaction.getPayments().get(1).getAmount()
        );

        assertEquals(
                PaymentType.CASH,
                transaction.getPayments().get(2).getPaymentType()
        );

        assertEquals(
                BigDecimal.valueOf(3.60),
                transaction.getPayments().get(2).getAmount()
        );



    }
    
}
