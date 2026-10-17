package coupons;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents one product line in a purchase transaction.
 */
public class PurchaseItem {
    
    private final String productName; // Store the human-readable product name.
    private final String productCode; // Store the identifier used for the product.
    private final BigDecimal regularUnitPrice; // Store the regular price for one unit.
    private final BigDecimal saleUnitPrice; // Store the actual sale price for one unit.
    private final int quantity; // Store the number of units purchased.
    private final UUID lineItemId;   // Store a unique identifier for this product line.
    
    /**
     * Creates a new receipt line with a generated identifier.
     */
    public PurchaseItem(
            String productName,
            String productCode,
            BigDecimal regularUnitPrice,
            BigDecimal saleUnitPrice,
            int quantity) {
        this (
            UUID.randomUUID(),
            productName,
            productCode,
            regularUnitPrice,
            saleUnitPrice,
            quantity
        );
    }

    /**
     * Creates a receipt line using an existing identifier.
     * Use when loading or correcting an existing line.
     *
     * @param lineItemId existing receipt-line identifier
     * @param productName recorded name
     * @param productCode recorded identifier, or null when unknown
     * @param regularUnitPrice regular price, or null when unknown
     * @param saleUnitPrice recorded actual unit price
     * @param quantity number of units on the line
     * @throws NullPointerException if ID, name, or sale price is null
     * @throws IllegalArgumentException if a string is blank, a price
     *         is negative, or quantity is less than one
     */
    public PurchaseItem(
            UUID lineItemId,
            String productName,
            String productCode,
            BigDecimal regularUnitPrice,
            BigDecimal saleUnitPrice,
            int quantity) {

        this.lineItemId = Objects.requireNonNull(
                lineItemId,
                "Receipt line ID cannot be null."
        );

        this.productName = requireText(
                productName,
                "Product name"
        );

        if (productCode != null && productCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Product code must be null or nonblank."
            );
        }

        this.productCode = productCode;

        Objects.requireNonNull(
                saleUnitPrice,
                "Sale unit price cannot be null."
        );

        if (regularUnitPrice != null && regularUnitPrice.signum() < 0) {
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

    public UUID getLineItemId() {
        return lineItemId;
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
