package coupons.repository;
/**
 * Identifies an event in a receipt's change history.
 * Current receipt status is represented separately.
 */
public enum ReceiptChangeAction {


    /** The receipt was first saved. */
    CREATED,

    /** The existing receipt was corrected. */
    UPDATED,

    /** The receipt was marked deleted but remains recoverable. */
    DELETED,

    /** The deleted receipt was made active again. */
    RESTORED
}
    
