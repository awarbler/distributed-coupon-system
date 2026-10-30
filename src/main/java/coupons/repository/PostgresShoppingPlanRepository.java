package coupons.repository;

import coupons.ShoppingPlan;
import java.sql.*;
import java.util.*;

/** PostgreSQL plan store with append-only revisions and optimistic locking. */
public final class PostgresShoppingPlanRepository implements ShoppingPlanRepository {
    private final PostgresConnectionFactory connections;
    public PostgresShoppingPlanRepository(PostgresConnectionFactory connections) { this.connections = Objects.requireNonNull(connections); }
    @Override public void save(ShoppingPlan plan, String actor) {
        Objects.requireNonNull(plan); PostgresRepositorySupport.requireActor(actor, "Recorded by");
        if (plan.revision() != 1) throw new IllegalArgumentException("New plan revision must be one.");
        PostgresRepositorySupport.transaction(connections, c -> {
            try (PreparedStatement p=c.prepareStatement("INSERT INTO shopping_plan_current(plan_id,name,retailer,shopping_date,revision,payload) VALUES (?,?,?,?,?,?)")) {
                bind(p, plan); p.setBytes(6, PostgresRepositorySupport.serialize(plan)); p.executeUpdate();
            } catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("Plan already exists.", e); throw e; }
            history(c, plan, actor, "initial plan"); return null;
        });
    }
    @Override public Optional<ShoppingPlan> findById(UUID id) { return find(Objects.requireNonNull(id), false, 0); }
    @Override public Optional<ShoppingPlan> findByRevision(UUID id,long revision) {
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive.");
        return find(Objects.requireNonNull(id), true, revision);
    }
    private Optional<ShoppingPlan> find(UUID id, boolean history, long revision) {
        return PostgresRepositorySupport.transaction(connections,c -> {
            String table=history?"shopping_plan_history":"shopping_plan_current";
            String sql="SELECT payload FROM "+table+" WHERE plan_id=?"+(history?" AND revision=?":"");
            try (PreparedStatement p=c.prepareStatement(sql)) { p.setObject(1,id); if(history)p.setLong(2,revision);
                try(ResultSet r=p.executeQuery()) { return r.next()?Optional.of(PostgresRepositorySupport.deserialize(r.getBytes(1),ShoppingPlan.class)):Optional.empty(); }
            }
        });
    }
    @Override public ShoppingPlan update(ShoppingPlan plan,long expected,String actor,String reason) {
        Objects.requireNonNull(plan); PostgresRepositorySupport.requireActor(actor,"Changed by"); PostgresRepositorySupport.requireActor(reason,"Reason");
        if(expected<1 || plan.revision()!=expected+1) throw new IllegalArgumentException("Invalid plan revision.");
        return PostgresRepositorySupport.transaction(connections,c -> {
            long stored; try(PreparedStatement p=c.prepareStatement("SELECT revision FROM shopping_plan_current WHERE plan_id=? FOR UPDATE")) {
                p.setObject(1,plan.planId()); try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Plan does not exist.");stored=r.getLong(1);}
            }
            if(stored!=expected) throw new ConcurrentModificationException("Plan revision changed.");
            try(PreparedStatement p=c.prepareStatement("UPDATE shopping_plan_current SET name=?,retailer=?,shopping_date=?,revision=?,payload=? WHERE plan_id=?")) {
                p.setString(1,plan.name());p.setString(2,plan.retailer());p.setObject(3,plan.shoppingDate());p.setLong(4,plan.revision());p.setBytes(5,PostgresRepositorySupport.serialize(plan));p.setObject(6,plan.planId());p.executeUpdate();
            }
            history(c,plan,actor,reason); return plan;
        });
    }
    static void markPurchased(Connection c,UUID id,long expected,UUID receiptId,String actor)throws SQLException {
        ShoppingPlan old;
        try(var p=c.prepareStatement("SELECT payload FROM shopping_plan_current WHERE plan_id=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Shopping plan no longer exists.");old=PostgresRepositorySupport.deserialize(r.getBytes(1),ShoppingPlan.class);}}
        if(old.purchasedReceiptId()!=null)throw new ConcurrentModificationException("This plan is already purchased. Open its linked receipt.");
        if(old.revision()!=expected)throw new ConcurrentModificationException("Plan changed since this draft was opened. Review the current plan before saving.");
        var next=new ShoppingPlan(old.planId(),old.name(),old.retailer(),old.shoppingDate(),old.plannedPurchaseIds(),Math.addExact(expected,1),old.scenario(),receiptId,old.organization());
        try(var p=c.prepareStatement("UPDATE shopping_plan_current SET revision=?,payload=? WHERE plan_id=?")){p.setLong(1,next.revision());p.setBytes(2,PostgresRepositorySupport.serialize(next));p.setObject(3,id);p.executeUpdate();}
        history(c,next,actor,"Purchased on receipt "+receiptId);
    }
    private static void bind(PreparedStatement p,ShoppingPlan x)throws SQLException{p.setObject(1,x.planId());p.setString(2,x.name());p.setString(3,x.retailer());p.setObject(4,x.shoppingDate());p.setLong(5,x.revision());}
    private static void history(Connection c,ShoppingPlan x,String actor,String reason)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO shopping_plan_history(plan_id,revision,name,changed_by,reason,payload) VALUES (?,?,?,?,?,?)")){p.setObject(1,x.planId());p.setLong(2,x.revision());p.setString(3,x.name());p.setString(4,actor);p.setString(5,reason);p.setBytes(6,PostgresRepositorySupport.serialize(x));p.executeUpdate();}
    }
}
