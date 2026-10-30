package coupons.repository;

import coupons.inventory.InventoryBalance;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines database-independent storage for whole-package inventory.
 *
 * <p>Each repository instance operates within one owner's inventory scope.
 * It permits one aggregate balance per product within that scope.
 *
 * <p>Callers must supply a productId established by the catalog workflow.
 * Repository implementations must enforce product-reference validity
 * when catalog integration is available. The repository does not perform
 * product matching.
 *
 * <p>Product associations must not be inferred solely from matching
 * names or identifiers. Confirmation policy belongs to product-catalog work.
 *
 * <p>Inventory writes do not modify historical receipts, automatically
 * import purchases, reverse quantities when receipts are deleted, or
 * trigger shopping recommendations.
 *
 * <p>Creation and adjustments must preserve history. Each successful
 * write and its history entry must commit atomically.
 */
public interface InventoryRepository {
    /**
     * Creates a previously unrecorded balance at revision one.
     *
     * <p>Rejects duplicate inventory IDs and additional balances for an
     * already represented product. Records the actor and initial quantity.
     * The application service supplies the authenticated actor.
     *
     * @param balance initial whole-package balance
     * @param recordedBy user recording the initial balance
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if recordedBy is blank, revision
     *         is not one, an identity is duplicated, or the product reference
     *         is invalid when catalog validation is available
     */
    void save(InventoryBalance balance, String recordedBy);

    /**
     * Finds a balance by its stable identifier.
     *
     * @param inventoryId balance identifier
     * @return the balance, or Optional.empty() if absent
     * @throws NullPointerException if inventoryId is null
     */
    Optional<InventoryBalance> findById(UUID inventoryId);

    /**
     * Finds the balance for a catalog product.
     *
     * <p>An absent balance is different from a recorded zero balance.
     *
     * @param productId internal catalog product identifier
     * @return the balance, or Optional.empty() if absent
     * @throws NullPointerException if productId is null
     */
    Optional<InventoryBalance> findByProductId(UUID productId);

    /**
     * Records an approved replacement whole-package quantity.
     *
     * <p>Preserves inventory and product identity. The expected revision
     * check, quantity replacement, revision increment, and adjustment
     * history must succeed or fail together.
     *
     * <p>History must preserve the previous and resulting quantities,
     * previous and resulting revisions, adjustment context, and commit time.
     *
     * <p>For example, replacing five packages at revision three with
     * four packages produces a balance of four at revision four.
     * A concurrent change from revision three must cause rejection.
     *
     * <p>The supplied quantity is absolute, not an amount to add or subtract.
     * Even an approved adjustment to the same quantity records a new revision.
     *
     * <p>After an uncertain outcome, callers must retrieve and reconcile
     * the balance before submitting another adjustment. They must not
     * silently retry using a newer revision.
     *
     * @param inventoryId balance being adjusted
     * @param expectedRevision revision reviewed for the adjustment
     * @param quantityOnHand approved number of whole packages
     * @param context requester, reason, and approval information
     * @return the resulting balance at the next revision
     * @throws NullPointerException if inventoryId or context is null
     * @throws IllegalArgumentException if the balance is missing,
     *         quantity is negative, expectedRevision is less than one,
     *         or the stored revision cannot be incremented
     * @throws java.util.ConcurrentModificationException if the expected
     *         revision does not match the stored revision
     */
    InventoryBalance updateQuantity(
            UUID inventoryId,
            long expectedRevision,
            int quantityOnHand,
            InventoryAdjustCtx context
    );
}
