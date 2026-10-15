package coupons.repository;

public enum ReceiptStatus {
    ACTIVE, // The receipt is active and can be updated or deleted.
    DELETED // The receipt has been soft-deleted and can be restored.
    
}
