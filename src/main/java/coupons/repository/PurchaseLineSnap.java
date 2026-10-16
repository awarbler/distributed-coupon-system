package coupons.repository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import coupons.PurchaseItem;

/**
 * Preserves purchase-line values for a receipt revision.
 *
 * <p>All components are immutable. The line ID identifies the receipt
 * line rather than an individual product unit.
 *
 * @param lineItemId stable receipt-line identifier
 * @param productName recorded product name
 * @param productCode recorded product identifier
 * @param regularUnitPrice recorded regular price per unit
 * @param saleUnitPrice recorded sale price per unit
 * @param quantity number of units on this receipt line
 */
public record PurchaseLineSnap(
        UUID lineItemId,
        String productName,
        String productCode,
        BigDecimal regularUnitPrice,
        BigDecimal saleUnitPrice,
        int quantity
) {

    /**
     * Validates purchase-line snapshot values.
     *
     * <p>Text and monetary values are preserved without trimming,
     * rounding, or recalculation.
     *
     * @param lineItemId stable receipt-line identifier
     * @param productName recorded product name
     * @param productCode recorded product identifier
     * @param regularUnitPrice recorded regular unit price
     * @param saleUnitPrice recorded sale unit price
     * @param quantity number of units purchased
     * @throws NullPointerException if a required reference is null
     * @throws IllegalArgumentException if text is blank, a price is
     *         negative, or quantity is less than one
     */
    public PurchaseLineSnap {
        Objects.requireNonNull(lineItemId, "Line ID cannot be null.");
        Objects.requireNonNull(productName, "Product name cannot be null.");
        Objects.requireNonNull(productCode, "Product code cannot be null.");
        Objects.requireNonNull(
                regularUnitPrice,
                "Regular unit price cannot be null."
        );
        Objects.requireNonNull(
                saleUnitPrice,
                "Sale unit price cannot be null."
        );

        if (productName.isBlank() || productCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name and code cannot be blank."
            );
        }

        if (regularUnitPrice.signum() < 0
                || saleUnitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Unit prices cannot be negative."
            );
        }

        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be at least 1."
            );
        }
    }

    /**
     * Captures a purchase line's current values.
     *
     * @param item purchase line to capture
     * @return an immutable value snapshot
     * @throws NullPointerException if item is null
     */
    public static PurchaseLineSnap from(PurchaseItem item) {
        Objects.requireNonNull(item, "Purchase item cannot be null.");

        return new PurchaseLineSnap(
                item.getLineItemId(),
                item.getProductName(),
                item.getProductCode(),
                item.getRegularUnitPrice(),
                item.getSaleUnitPrice(),
                item.getQuantity()
        );
    }
}