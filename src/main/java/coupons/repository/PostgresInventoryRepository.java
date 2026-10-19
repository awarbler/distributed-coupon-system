package coupons.repository;

import coupons.InventoryBalance;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL implementation with append-only adjustment history. */
public final class PostgresInventoryRepository implements InventoryRepository {
    private final PostgresConnectionFactory connections;
    private final java.time.Clock clock;
    public PostgresInventoryRepository(PostgresConnectionFactory connections) {
        this(connections, java.time.Clock.systemUTC());
    }
    public PostgresInventoryRepository(PostgresConnectionFactory connections, java.time.Clock clock) {
        this.connections = Objects.requireNonNull(connections); this.clock = Objects.requireNonNull(clock);
    }
    @Override public void save(InventoryBalance b, String actor) {
        Objects.requireNonNull(b); PostgresRepositorySupport.requireActor(actor, "Recorded by");
        PostgresRepositorySupport.transaction(connections, c -> {
            if (b.revision() != 1) throw new IllegalArgumentException("New inventory revision must be one.");
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO inventory_current VALUES (?,?,?,?)")) {
                p.setObject(1,b.inventoryId()); p.setObject(2,b.productId()); p.setInt(3,b.quantityOnHand()); p.setLong(4,1); p.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("Inventory ID or product already exists.", e);
                throw e;
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO inventory_history VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                UUID hid = UUID.randomUUID(); Instant now = clock.instant();
                p.setObject(1,hid); p.setObject(2,b.inventoryId()); p.setInt(3,b.quantityOnHand());
                p.setInt(4,b.quantityOnHand()); p.setLong(5,1); p.setLong(6,1);
                p.setString(7,actor); p.setString(8,"initial balance"); p.setString(9,actor);
                p.setTimestamp(10,Timestamp.from(now)); p.setTimestamp(11,Timestamp.from(now)); p.executeUpdate();
            }
            return null;
        });
    }
    @Override public Optional<InventoryBalance> findById(UUID id) { Objects.requireNonNull(id); return find("inventory_id",id); }
    @Override public Optional<InventoryBalance> findByProductId(UUID id) { Objects.requireNonNull(id); return find("product_id",id); }
    private Optional<InventoryBalance> find(String column, UUID id) {
        return PostgresRepositorySupport.transaction(connections,c -> {
            try (PreparedStatement p=c.prepareStatement("SELECT inventory_id,product_id,quantity_on_hand,revision FROM inventory_current WHERE "+column+"=?")) {
                p.setObject(1,id); try(ResultSet r=p.executeQuery()) {
                    return r.next()?Optional.of(new InventoryBalance((UUID)r.getObject(1),(UUID)r.getObject(2),r.getInt(3),r.getLong(4))):Optional.empty();
                }
            }
        });
    }
    @Override public InventoryBalance updateQuantity(UUID id,long expected,int quantity,InventoryAdjustCtx ctx) {
        Objects.requireNonNull(id); Objects.requireNonNull(ctx);
        if (expected < 1 || quantity < 0) throw new IllegalArgumentException("Invalid inventory adjustment.");
        return PostgresRepositorySupport.transaction(connections,c -> {
            InventoryBalance old;
            try (PreparedStatement p=c.prepareStatement("SELECT inventory_id,product_id,quantity_on_hand,revision FROM inventory_current WHERE inventory_id=? FOR UPDATE")) {
                p.setObject(1,id); try(ResultSet r=p.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("Inventory balance does not exist.");
                    old=new InventoryBalance((UUID)r.getObject(1),(UUID)r.getObject(2),r.getInt(3),r.getLong(4));
                }
            }
            if(old.revision()!=expected) throw new ConcurrentModificationException("Inventory revision changed.");
            if(old.revision()==Long.MAX_VALUE) throw new IllegalArgumentException("Inventory revision cannot be incremented.");
            long next=expected+1; try(PreparedStatement p=c.prepareStatement("UPDATE inventory_current SET quantity_on_hand=?,revision=? WHERE inventory_id=?")) {
                p.setInt(1,quantity);p.setLong(2,next);p.setObject(3,id);p.executeUpdate();
            }
            Instant now=clock.instant(); try(PreparedStatement p=c.prepareStatement("INSERT INTO inventory_history VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                p.setObject(1,UUID.randomUUID());p.setObject(2,id);p.setInt(3,old.quantityOnHand());p.setInt(4,quantity);
                p.setLong(5,expected);p.setLong(6,next);p.setString(7,ctx.requestedBy());p.setString(8,ctx.reason());
                p.setString(9,ctx.approvedBy());p.setTimestamp(10,Timestamp.from(ctx.approvedAt()));p.setTimestamp(11,Timestamp.from(now));p.executeUpdate();
            }
            return new InventoryBalance(id,old.productId(),quantity,next);
        });
    }
}
