package coupons;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents one product line in a purchase transaction.
 */
public class PurchaseItem {

    private final String productName; // Store the human-readable product name.
    private final String productCode; // Store the identifier used for the product.
    private final BigDecimal regularUnitPrice; // Store the regular price for one unit.
    private final BigDecimal saleUnitPrice; // Store the actual sale price for one unit.
    private final int quantity; // Store the number of units purchased.

    /**
     * Creates a purchased product line.
     *
     * @param productName product's display name
     * @param productCode product's identifier
     * @param regularUnitPrice regular price for one unit
     * @param saleUnitPrice sale price for one unit
     * @param quantity number of units purchased
     * @throws NullPointerException if a required string is null
     * @throws IllegalArgumentException if a string is blank, a price is
     * negative, or quantity is less than one
     */
    public PurchaseItem(
            String productName,
            String productCode,
            BigDecimal regularUnitPrice,
            BigDecimal saleUnitPrice,
            int quantity) {

        this.productName = requireText(
                productName,
                "Product name"
        );

        this.productCode = requireText(
                productCode,
                "Product code"
        );

        Objects.requireNonNull(
                regularUnitPrice,
                "Regular unit price cannot be null."
        );

        Objects.requireNonNull(
                saleUnitPrice,
                "Sale unit price cannot be null."
        );

        if (regularUnitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Regular unit price cannot be negative."
            );
        }

        if (saleUnitPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Sale unit price cannot be negative."
            );
        }
        if (quantity < 1) {
            throw new IllegalArgumentException(
                    "Quantity must be at least 1."
            );
        }

        this.regularUnitPrice = regularUnitPrice;
        this.saleUnitPrice = saleUnitPrice;
        this.quantity = quantity;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getRegularUnitPrice() {
        return regularUnitPrice;
    }

    public BigDecimal getSaleUnitPrice() {
        return saleUnitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * Calculates the sale-price total for this product line.
     *
     * @return sale unit price multiplied by quantity
     */
    public BigDecimal calculateLineTotal() {
        return saleUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Validates required text fields before storing them.
     */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(
                value,
                fieldName + " cannot be null."
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank."
            );
        }

        return value.trim(); // Remove accidental leading and trailing whitespace from validated text.
    }
}
