package coupons;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;



class WorkbookTransactionTest {
    @Test 
    void sheetOneTransactionPreserversRecordedItemsAndPayments(){
        CouponTransaction transaction = 
                new CouponTransaction(
                    new ReceiptTotals(1.04, 3.60)
                );
        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Neutrogena",
                        "07050111090",
                        7.79,
                        5.99,
                        2
                )
        );
        transaction.addPurchaseItem(
                new PurchaseItem(
                        "Lysol travel wipes",
                        "01920099717",
                        3.29,
                        3.29,
                        2
                )
        );
        transaction.addPayment(
                new Payment(PaymentType.REGISTER_REWARD, 2.00  )
        );
        transaction.addPayment(
                new Payment(PaymentType.WALGREENS_CASH, 10.00)
        );

        transaction.addPayment(
                new Payment(PaymentType.CASH, 3.60)
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
                15.58,
                neutrogena.getRegularUnitPrice()
                        * neutrogena.getQuantity(),
                0.001
        );

        assertEquals(
                6.58,
                lysol.calculateLineTotal(),
                0.001
        );

        assertEquals(
                1.04,
                transaction.getReceiptTotals().getTaxAmount(),
                0.001
        );

        assertEquals(
                3.60,
                transaction.getReceiptTotals().getFinalTotal(),
                0.001
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
                2.00,
                transaction.getPayments().get(0).getAmount(),
                0.001
        );

        assertEquals(
                PaymentType.WALGREENS_CASH,
                transaction.getPayments().get(1).getPaymentType()
        );

        assertEquals(
                10.00,
                transaction.getPayments().get(1).getAmount(),
                0.001
        );

        assertEquals(
                PaymentType.CASH,
                transaction.getPayments().get(2).getPaymentType()
        );

        assertEquals(
                3.60,
                transaction.getPayments().get(2).getAmount(),
                0.001
        );



    }
    
}
