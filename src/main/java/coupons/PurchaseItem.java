package coupons;

import java.util.Objects;

/**
 * Represents one product line in a purchase transaction.
 */
public class PurchaseItem {

    private final String productName; // Store the human-readable product name.
    private final String productCode; // Store the identifier used for the product.
    private final double regularUnitPrice; // Store the regular price for one unit.
    private final double saleUnitPrice; // Store the actual sale price for one unit.
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
     *         negative, or quantity is less than one
     */
    public PurchaseItem(
            String productName,
            String productCode,
            double regularUnitPrice,
            double saleUnitPrice,
            int quantity) {

        this.productName = requireText(
                productName,
                "Product name"
        );

        this.productCode = requireText(
                productCode,
                "Product code"
        );

        if (!Double.isFinite(regularUnitPrice) || regularUnitPrice < 0.0) { // Reject non-finite or negative regular prices.
            throw new IllegalArgumentException(
                    "Regular unit price must be a finite non-negative number."
            );
        }

        if (!Double.isFinite(saleUnitPrice) || saleUnitPrice < 0.0) { // Reject non-finite or negative sale prices.
            throw new IllegalArgumentException(
                    "Sale unit price must be a finite non-negative number."
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

    public double getRegularUnitPrice() {
        return regularUnitPrice;
    }

    public double getSaleUnitPrice() {
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
    public double calculateLineTotal() {
        return saleUnitPrice * quantity;
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