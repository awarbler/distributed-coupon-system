package coupons;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the recorded whole-package balance for a catalog product.
 *
 * <p>Product associations are supplied by the catalog workflow.
 * Matching names or identifiers does not establish an association.
 * Package measurements and partial consumption are outside this model.
 *
 * @param inventoryId stable balance identifier
 * @param productId internal catalog product identifier
 * @param quantityOnHand number of whole retail packages owned
 * @param revision balance revision, beginning at one
 */
public record InventoryBalance(
        UUID inventoryId,
        UUID productId,
        int quantityOnHand,
        long revision
) {
    /**
     * Validates balance values without changing them.
     *
     * @param inventoryId stable balance identifier
     * @param productId internal catalog product identifier
     * @param quantityOnHand number of whole packages owned
     * @param revision balance revision
     * @throws NullPointerException if either identifier is null
     * @throws IllegalArgumentException if quantity is negative or revision is less than one
     */
    public InventoryBalance {
        Objects.requireNonNull(inventoryId, "Inventory ID cannot be null.");
        Objects.requireNonNull(productId, "Product ID cannot be null.");
        if (quantityOnHand < 0) {
            throw new IllegalArgumentException("Quantity on hand cannot be negative.");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("Inventory revision must be at least 1.");
        }
    }
}
