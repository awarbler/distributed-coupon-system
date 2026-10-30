package coupons.repository;

import coupons.shopping.PlannedPurchase;
import java.util.*;

public interface PlannedPurchaseRepository {
    void save(PlannedPurchase purchase, String recordedBy);
    Optional<PlannedPurchase> findById(UUID purchaseId);
    List<PlannedPurchase> findByPlanId(UUID planId);
    PlannedPurchase update(PlannedPurchase purchase, long expectedRevision, String changedBy, String reason);
}
