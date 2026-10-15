package coupons; // Place the Coupon class in the coupons package.

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

class CouponSystemTest {

    @Test
    void baselineRunPreservesCurrentReceiptResults() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;

        try {
            System.setOut(new PrintStream(output));
            new CouponSystem().run();
        } finally {
            System.setOut(originalOutput);
        }

        String applicationOutput = output.toString();

        assertTrue(
                applicationOutput.contains(
                        "Calculated checkout amount: $7.27"
                )
        );

        assertTrue(
                applicationOutput.contains(
                        "Actual receipt total: $7.28"
                )
        );

        assertTrue(
                applicationOutput.contains(
                        "Receipt difference: $0.01"
                )
        );

        assertTrue(
                applicationOutput.contains(
                        "Type: REBATE_REQUEST"
                )
        );
    }
}