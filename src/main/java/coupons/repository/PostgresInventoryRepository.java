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
    /** Explicit owner import; enabling it also synchronizes later receipt revisions. */
    public Map<String,Object> importReceipts(String actor) {
        PostgresRepositorySupport.requireActor(actor,"Imported by");
        return PostgresRepositorySupport.transaction(connections,c->{
            try(var p=c.prepareStatement("UPDATE inventory_receipt_settings SET enabled=true WHERE singleton=true")){p.executeUpdate();}
            syncReceipts(c,actor);
            return Map.of("products",count(c,"SELECT count(*) FROM inventory_receipt_products"),"receiptLines",count(c,"SELECT count(*) FROM inventory_receipt_lines WHERE active"),"purchasedPackages",count(c,"SELECT COALESCE(sum(quantity),0) FROM inventory_receipt_lines WHERE active"));
        });
    }
    private static long count(Connection c,String sql)throws SQLException {try(var p=c.prepareStatement(sql);var r=p.executeQuery()){r.next();return r.getLong(1);}}
    /** Called on the receipt write connection, so stock and evidence cannot diverge. */
    static void syncReceipts(Connection c,String actor)throws SQLException {
        try(var p=c.prepareStatement("SELECT enabled FROM inventory_receipt_settings WHERE singleton=true FOR UPDATE");var r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))return;}
        var before=purchased(c);
        try(var p=c.prepareStatement("UPDATE inventory_receipt_lines SET active=false WHERE active")){p.executeUpdate();}
        var records=PostgresReceiptState.read(c).receipts().values();
        for(var receipt:records) {
            if(receipt.status()!=ReceiptStatus.ACTIVE)continue;
            for(var line:receipt.contents().purchaseItems()) {
                String sku=line.productCode()==null||line.productCode().isBlank()?null:line.productCode().trim();
                String key=sku==null?"LINE:"+receipt.transactionId()+":"+line.lineItemId():"SKU:"+sku;
                UUID productId=UUID.nameUUIDFromBytes(("receipt-inventory:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                UUID inventoryId=UUID.nameUUIDFromBytes(("receipt-stock:"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                boolean fresh;
                try(var p=c.prepareStatement("INSERT INTO inventory_current(inventory_id,product_id,quantity_on_hand,revision) VALUES(?,?,0,1) ON CONFLICT(product_id) DO NOTHING")){p.setObject(1,inventoryId);p.setObject(2,productId);fresh=p.executeUpdate()>0;}
                if(fresh)writeHistory(c,inventoryId,0,0,1,1,actor,"Receipt import; on-hand count not yet confirmed");
                try(var p=c.prepareStatement("INSERT INTO inventory_receipt_products(product_id,product_key,product_name,sku) VALUES(?,?,?,?) ON CONFLICT(product_id) DO NOTHING")){p.setObject(1,productId);p.setString(2,key);p.setString(3,line.productName());p.setString(4,sku);p.executeUpdate();}
                try(var p=c.prepareStatement("INSERT INTO inventory_receipt_lines(transaction_id,line_item_id,product_id,receipt_revision,quantity,active,product_name,retailer,receipt_number,purchase_date) VALUES(?,?,?,?,?,true,?,?,?,?) ON CONFLICT(transaction_id,line_item_id) DO UPDATE SET product_id=EXCLUDED.product_id,receipt_revision=EXCLUDED.receipt_revision,quantity=EXCLUDED.quantity,active=true,product_name=EXCLUDED.product_name,retailer=EXCLUDED.retailer,receipt_number=EXCLUDED.receipt_number,purchase_date=EXCLUDED.purchase_date")) {
                    p.setObject(1,receipt.transactionId());p.setObject(2,line.lineItemId());p.setObject(3,productId);p.setLong(4,receipt.revision());p.setInt(5,line.quantity());p.setString(6,line.productName());p.setString(7,receipt.metadata()==null?null:receipt.metadata().retailer());p.setString(8,receipt.metadata()==null?null:receipt.metadata().receiptNumber());p.setObject(9,receipt.contents().observations().purchaseDate());p.executeUpdate();
                }
            }
        }
        var after=purchased(c);var products=new HashSet<UUID>(before.keySet());products.addAll(after.keySet());
        for(var id:products) {
            long delta=after.getOrDefault(id,0L)-before.getOrDefault(id,0L);if(delta==0)continue;
            try(var p=c.prepareStatement("SELECT i.inventory_id,i.quantity_on_hand,i.revision,m.quantity_confirmed,m.units_per_package,m.unit_name,m.count_unit FROM inventory_current i JOIN inventory_receipt_products m USING(product_id) WHERE product_id=? FOR UPDATE OF i,m")){
                p.setObject(1,id);try(var r=p.executeQuery()){if(r.next()){
                    UUID stock=r.getObject(1,UUID.class);int old=r.getInt(2);long revision=r.getLong(3);boolean confirmed=r.getBoolean(4);
                    long multiplier="ITEM".equals(r.getString(7))?r.getInt(5):1;
                    long target=confirmed?old+Math.multiplyExact(delta,multiplier):Math.multiplyExact(after.getOrDefault(id,0L),multiplier);boolean nextConfirmed=confirmed&&target>=0;
                    if(target>Integer.MAX_VALUE)throw new IllegalArgumentException("Inventory quantity is too large.");
                    int quantity=(int)Math.max(0,target);
                    try(var u=c.prepareStatement("UPDATE inventory_current SET quantity_on_hand=?,revision=revision+1 WHERE inventory_id=?")){u.setInt(1,quantity);u.setObject(2,stock);u.executeUpdate();}
                    try(var u=c.prepareStatement("UPDATE inventory_receipt_products SET quantity_confirmed=? WHERE product_id=?")){u.setBoolean(1,nextConfirmed);u.setObject(2,id);u.executeUpdate();}
                    writeHistory(c,stock,old,quantity,revision,revision+1,actor,"Receipt purchase quantity changed by "+delta+(nextConfirmed?"":"; confirm physical stock"));
                }}
            }
        }
    }
    private static Map<UUID,Long> purchased(Connection c)throws SQLException {
        var out=new HashMap<UUID,Long>();try(var p=c.prepareStatement("SELECT product_id,sum(quantity) FROM inventory_receipt_lines WHERE active GROUP BY product_id");var r=p.executeQuery()){while(r.next())out.put(r.getObject(1,UUID.class),r.getLong(2));}return out;
    }
    private static void writeHistory(Connection c,UUID stock,int old,int quantity,long previous,long revision,String actor,String reason)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO inventory_history VALUES(?,?,?,?,?,?,?,?,?,?,?)")){var now=Instant.now();p.setObject(1,UUID.randomUUID());p.setObject(2,stock);p.setInt(3,old);p.setInt(4,quantity);p.setLong(5,previous);p.setLong(6,revision);p.setString(7,actor);p.setString(8,reason);p.setString(9,actor);p.setTimestamp(10,Timestamp.from(now));p.setTimestamp(11,Timestamp.from(now));p.executeUpdate();}
    }
    public void adjust(UUID id,long expected,String action,int amount,String location,String note,String actor) {
        adjust(id,expected,action,amount,location,note,actor,null);
    }
    public void adjust(UUID id,long expected,String action,int amount,String location,String note,String actor,Integer lowStockLevel) {
        adjust(id,expected,action,amount,location,note,actor,lowStockLevel,null,null,null);
    }
    public void adjust(UUID id,long expected,String action,int amount,String location,String note,String actor,Integer lowStockLevel,Integer unitsPerPackage,String unitName,String countUnit) {
        if(unitsPerPackage!=null&&unitsPerPackage<1)throw new IllegalArgumentException("Items per package must be positive.");
        if(unitName!=null&&unitName.length()>80)throw new IllegalArgumentException("Unit name is too long.");
        if(countUnit!=null&&!Set.of("PACKAGE","ITEM").contains(countUnit))throw new IllegalArgumentException("Choose packages or individual items.");
        if(lowStockLevel!=null&&lowStockLevel<0)throw new IllegalArgumentException("Low-stock level cannot be negative.");
        PostgresRepositorySupport.requireActor(actor,"Recorded by");if(amount<0||location!=null&&location.length()>200)throw new IllegalArgumentException("Enter a nonnegative quantity and location up to 200 characters.");
        PostgresRepositorySupport.transaction(connections,c->{
            try(var p=c.prepareStatement("SELECT enabled FROM inventory_receipt_settings WHERE singleton=true FOR UPDATE");var r=p.executeQuery()){if(!r.next()||!r.getBoolean(1))throw new IllegalArgumentException("Import your receipts first.");}
            try(var p=c.prepareStatement("SELECT i.product_id,i.quantity_on_hand,i.revision,m.quantity_confirmed,m.units_per_package,m.unit_name,m.count_unit FROM inventory_current i JOIN inventory_receipt_products m USING(product_id) WHERE inventory_id=? FOR UPDATE OF i,m")){
                p.setObject(1,id);try(var r=p.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("Inventory item not found.");if(r.getLong(3)!=expected)throw new ConcurrentModificationException("Inventory changed; refresh before adjusting.");
                    int old=r.getInt(2);boolean confirmed=r.getBoolean(4);int quantity;
                    Integer pack=countUnit==null?(Integer)r.getObject(5):unitsPerPackage;String unit=countUnit==null?r.getString(6):unitName;String mode=countUnit==null?r.getString(7):countUnit;
                    if("ITEM".equals(mode)&&pack==null)throw new IllegalArgumentException("Enter items per package before tracking individual items.");
                    if(confirmed&&!mode.equals(r.getString(7))&&!action.equals("SET"))throw new IllegalArgumentException("Confirm a physical count when changing the stock unit.");
                    if(action.equals("DETAILS"))quantity=old;else if(action.equals("SET"))quantity=amount;else {if(!Set.of("USE","RETURN","DONATE","DISCARD").contains(action))throw new IllegalArgumentException("Choose an inventory action.");if(!confirmed){old=Math.toIntExact(Math.multiplyExact(purchased(c).getOrDefault(r.getObject(1,UUID.class),0L),"ITEM".equals(r.getString(7))?r.getInt(5):1));}quantity=Math.subtractExact(old,amount);if(quantity<0)throw new IllegalArgumentException("Quantity exceeds recorded stock.");}
                    try(var u=c.prepareStatement("UPDATE inventory_current SET quantity_on_hand=?,revision=revision+1 WHERE inventory_id=?")){u.setInt(1,quantity);u.setObject(2,id);u.executeUpdate();}
                    try(var u=c.prepareStatement("UPDATE inventory_receipt_products SET location=?,quantity_confirmed=?,low_stock_level=?,units_per_package=?,unit_name=?,count_unit=? WHERE product_id=?")){u.setString(1,location==null||location.isBlank()?null:location.trim());u.setBoolean(2,action.equals("DETAILS")?confirmed:true);u.setObject(3,lowStockLevel);u.setObject(4,pack);u.setString(5,unit==null||unit.isBlank()?null:unit.trim());u.setString(6,mode);u.setObject(7,r.getObject(1));u.executeUpdate();}
                    writeHistory(c,id,old,quantity,expected,expected+1,actor,action+" "+amount+"; count unit "+mode+"; items per package "+pack+(note==null||note.isBlank()?"":" — "+note));
                }
            }return null;
        });
    }
    /** Receipt purchase evidence is read separately from confirmed physical stock. */
    static List<Object> receiptInventory(Connection c)throws SQLException {
        var out=new ArrayList<Object>();
        var state=PostgresReceiptState.read(c);
        var costs=new HashMap<String,Map<UUID,java.math.BigDecimal>>();
        for(var receipt:state.receipts().values()) costs.put(receipt.transactionId()+":"+receipt.revision(),InventoryCosts.calculate(receipt.contents()));
        for(var entry:state.history().values()) {var snapshot=entry.after();costs.computeIfAbsent(snapshot.transactionId()+":"+snapshot.revision(),key->InventoryCosts.calculate(snapshot.contents()));}
        try(var p=c.prepareStatement("SELECT i.inventory_id,i.product_id,i.quantity_on_hand,i.revision,m.product_name,m.sku,m.location,m.quantity_confirmed,m.low_stock_level,m.units_per_package,m.unit_name,m.count_unit FROM inventory_current i LEFT JOIN inventory_receipt_products m USING(product_id) ORDER BY lower(m.product_name) NULLS LAST,i.product_id");var r=p.executeQuery()){
            while(r.next()){
                var row=new LinkedHashMap<String,Object>();UUID product=r.getObject(2,UUID.class);row.put("inventoryId",r.getObject(1));row.put("productId",product);row.put("quantityOnHand",r.getObject(5)==null||r.getBoolean(8)?r.getInt(3):null);row.put("revision",r.getLong(4));row.put("productName",r.getString(5));row.put("sku",r.getString(6));row.put("location",r.getString(7));row.put("quantityConfirmed",r.getObject(5)==null||r.getBoolean(8));row.put("lowStockLevel",r.getObject(9));row.put("unitsPerPackage",r.getObject(10));row.put("unitName",r.getString(11));row.put("countUnit",r.getString(12)==null?"PACKAGE":r.getString(12));
                var sources=new ArrayList<Object>();var names=new HashSet<String>();long total=0;java.time.LocalDate last=null;
                try(var q=c.prepareStatement("SELECT transaction_id,line_item_id,receipt_revision,quantity,active,product_name,retailer,receipt_number,purchase_date FROM inventory_receipt_lines WHERE product_id=? ORDER BY purchase_date DESC NULLS LAST,transaction_id,line_item_id")){q.setObject(1,product);try(var v=q.executeQuery()){while(v.next()){var source=new LinkedHashMap<String,Object>();source.put("receiptId",v.getObject(1));source.put("lineItemId",v.getObject(2));source.put("revision",v.getLong(3));source.put("quantity",v.getInt(4));source.put("active",v.getBoolean(5));source.put("netCost",costs.getOrDefault(v.getObject(1)+":"+v.getLong(3),Map.of()).get(v.getObject(2,UUID.class)));source.put("productName",v.getString(6));source.put("store",v.getString(7));source.put("receiptNumber",v.getString(8));source.put("date",v.getObject(9));sources.add(source);if(v.getBoolean(5)){total+=v.getInt(4);names.add(v.getString(6).trim().toLowerCase(Locale.ROOT));var day=v.getObject(9,java.time.LocalDate.class);if(day!=null&&(last==null||day.isAfter(last)))last=day;}}}}
                if(row.get("quantityOnHand")==null)row.put("quantityOnHand",Math.multiplyExact(total,"ITEM".equals(r.getString(12))?r.getInt(10):1));row.put("totalPurchased",total);row.put("individualPurchased",r.getObject(10)==null?null:java.math.BigDecimal.valueOf(total).multiply(java.math.BigDecimal.valueOf(r.getInt(10))));row.put("individualOnHand",row.get("quantityOnHand")==null||r.getObject(10)==null?null:"ITEM".equals(r.getString(12))?row.get("quantityOnHand"):new java.math.BigDecimal(row.get("quantityOnHand").toString()).multiply(java.math.BigDecimal.valueOf(r.getInt(10))));row.put("lastPurchased",last);row.put("sources",sources);
                java.math.BigDecimal netCost=java.math.BigDecimal.ZERO;boolean costKnown=true;
                for(var value:sources){var source=(Map<?,?>)value;if(!Boolean.TRUE.equals(source.get("active")))continue;var cost=(java.math.BigDecimal)source.get("netCost");if(cost==null)costKnown=false;else netCost=netCost.add(cost);}
                row.put("netCost",costKnown?netCost:null);row.put("needsProductReview",r.getString(6)==null||names.size()>1);
                var history=new ArrayList<Object>();try(var q=c.prepareStatement("SELECT from_quantity,to_quantity,reason,requested_by,committed_at FROM inventory_history WHERE inventory_id=? ORDER BY committed_at DESC")){q.setObject(1,r.getObject(1));try(var v=q.executeQuery()){while(v.next())history.add(Map.of("before",v.getInt(1),"after",v.getInt(2),"reason",v.getString(3),"actor",v.getString(4),"at",v.getTimestamp(5).toInstant()));}}row.put("history",history);row.put("addedAt",history.isEmpty()?null:((Map<?,?>)history.getLast()).get("at"));out.add(row);
            }
        }return out;
    }

}
