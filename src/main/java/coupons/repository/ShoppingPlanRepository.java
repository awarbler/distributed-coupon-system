package coupons.repository;

import coupons.shopping.ShoppingPlan;
import java.util.*;

public interface ShoppingPlanRepository {
    void save(ShoppingPlan plan, String recordedBy);
    Optional<ShoppingPlan> findById(UUID planId);
    Optional<ShoppingPlan> findByRevision(UUID planId, long revision);
    ShoppingPlan update(ShoppingPlan plan, long expectedRevision, String changedBy, String reason);
}
