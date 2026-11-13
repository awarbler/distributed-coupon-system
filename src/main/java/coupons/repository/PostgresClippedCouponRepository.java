package coupons.repository;

import coupons.promotions.ClippedCoupon;
import coupons.promotions.Coupon;
import coupons.promotions.CouponStatus;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** PostgreSQL clipped-coupon store with one-time redemption enforcement. */
public final class PostgresClippedCouponRepository implements ClippedCouponRepository {
    private final PostgresConnectionFactory connections;
    public PostgresClippedCouponRepository(PostgresConnectionFactory connections){this.connections=Objects.requireNonNull(connections);}
    @Override public void save(ClippedCoupon x,String actor){saveAll(List.of(x),actor);}
    /** Each physical copy has an independent UUID and one-time redemption, inserted atomically. */
    public void saveAll(List<ClippedCoupon> copies,String actor){
        PostgresRepositorySupport.requireActor(actor,"Recorded by");
        PostgresRepositorySupport.transaction(connections,c->{validateWalgreensChanges(c,copies,false);for(var x:copies){
            if(x.revision()!=1)throw new IllegalArgumentException("New coupon revision must be one.");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO clipped_coupon_current(coupon_id,provider,external_id,status,revision,payload) VALUES (?,?,?,?,?,?)")){p.setObject(1,x.couponId());p.setString(2,x.provider());p.setString(3,x.externalId());p.setString(4,x.status().name());p.setLong(5,1);p.setBytes(6,PostgresRepositorySupport.serialize(x));p.executeUpdate();}catch(SQLException e){if("23505".equals(e.getSQLState()))throw new IllegalArgumentException("Coupon already exists.",e);throw e;}history(c,x,actor,"initial coupon");}return null;});
    }
    @Override public void importReviewed(List<ClippedCoupon> changes,Map<UUID,Long> expected,String actor) {
        PostgresRepositorySupport.requireActor(actor,"Imported by");
        PostgresRepositorySupport.transaction(connections,c->{
            try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(79342815)")){lock.execute();}
            var existing=new HashMap<UUID,ClippedCoupon>();
            try(var p=c.prepareStatement("SELECT payload FROM clipped_coupon_current FOR UPDATE");var r=p.executeQuery()) {
                while(r.next()){var x=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);existing.put(x.couponId(),x);}
            }
            validateWalgreensChanges(c,changes,true);
            var ids=new HashSet<UUID>();
            for(var x:changes) {
                if(!ids.add(x.couponId()))throw new IllegalArgumentException("Duplicate coupon identity in import.");
                var old=existing.get(x.couponId());long revision=expected.getOrDefault(x.couponId(),0L);
                if(old==null && (revision!=0||x.revision()!=1)||old!=null&&(revision!=old.revision()||x.revision()!=revision+1))throw new ConcurrentModificationException("Coupon changed. Review the refreshed import.");
                if(old!=null&&(old.status()==CouponStatus.APPLIED||old.redeemedTransactionId()!=null))throw new IllegalArgumentException("Redeemed coupon history is protected.");
                if(!Set.of(CouponStatus.CLIPPED,CouponStatus.AVAILABLE).contains(x.status()))throw new IllegalArgumentException("Imports require a verified Available or Clipped state.");
                if(old==null){try(var p=c.prepareStatement("INSERT INTO clipped_coupon_current(coupon_id,provider,external_id,status,revision,payload) VALUES (?,?,?,?,?,?)")){p.setObject(1,x.couponId());p.setString(2,x.provider());p.setString(3,x.externalId());p.setString(4,x.status().name());p.setLong(5,x.revision());p.setBytes(6,PostgresRepositorySupport.serialize(x));p.executeUpdate();}}
                else write(c,x);
                existing.put(x.couponId(),x);history(c,x,actor,"reviewed Walgreens PDF import");
            }
            return null;
        });
    }
    private static boolean isWalgreens(ClippedCoupon x) {
        var t=x.coupon().getTerms();return t!=null&&t.source().name().startsWith("DIGITAL")&&("Walgreens".equalsIgnoreCase(t.storeRestriction())||"Walgreens".equalsIgnoreCase(x.provider()));
    }
    @Override public Optional<ClippedCoupon> findById(UUID id){return find("coupon_id=?",Objects.requireNonNull(id));}
    @Override public List<ClippedCoupon> findByProvider(String provider){PostgresRepositorySupport.requireActor(provider,"Provider");return PostgresRepositorySupport.transaction(connections,c->{List<ClippedCoupon> out=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT payload FROM clipped_coupon_current WHERE provider=? ORDER BY coupon_id")){p.setString(1,provider);try(ResultSet r=p.executeQuery()){while(r.next())out.add(PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class));}}return List.copyOf(out);});}
    private Optional<ClippedCoupon> find(String where,Object value){return PostgresRepositorySupport.transaction(connections,c->{try(PreparedStatement p=c.prepareStatement("SELECT payload FROM clipped_coupon_current WHERE "+where)){p.setObject(1,value);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class)):Optional.empty();}}});}
    @Override public ClippedCoupon update(ClippedCoupon x,long expected,String actor,String reason) {
        Objects.requireNonNull(x);PostgresRepositorySupport.requireActor(actor,"Changed by");PostgresRepositorySupport.requireActor(reason,"Reason");
        if(expected<1||x.revision()!=expected+1)throw new IllegalArgumentException("Invalid coupon revision.");
        return PostgresRepositorySupport.transaction(connections,c->{
            validateWalgreensChanges(c,List.of(x),false);
            ClippedCoupon old;
            try(var p=c.prepareStatement("SELECT payload FROM clipped_coupon_current WHERE coupon_id=? FOR UPDATE")) {
                p.setObject(1,x.couponId());try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Coupon does not exist.");old=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);}
            }
            if(old.revision()!=expected)throw new ConcurrentModificationException("Coupon revision changed.");
            if(old.status()==CouponStatus.APPLIED||old.redeemedTransactionId()!=null||x.status()==CouponStatus.APPLIED||x.redeemedTransactionId()!=null)throw new IllegalArgumentException("Use the protected receipt/redemption workflow for redeemed coupons.");
            if(!Objects.equals(old.externalId(),x.externalId()))throw new IllegalArgumentException("Coupon external identity cannot change.");
            write(c,x);history(c,x,actor,reason);return x;
        });
    }
    private static void validateWalgreensChanges(Connection c,List<ClippedCoupon> changes,boolean imported)throws SQLException {
        try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(79342815)")){lock.execute();}
        var all=new HashMap<UUID,ClippedCoupon>();
        try(var p=c.prepareStatement("SELECT payload FROM clipped_coupon_current");var r=p.executeQuery()){while(r.next()){var x=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);all.put(x.couponId(),x);}}
        long before=all.values().stream().filter(x->isWalgreens(x)&&x.status()==CouponStatus.CLIPPED).count();
        for(var x:changes) {
            if(isWalgreens(x)&&Set.of(CouponStatus.CLIPPED,CouponStatus.AVAILABLE).contains(x.status()))for(var old:all.values()) {
                if(old.couponId().equals(x.couponId())||!isWalgreens(old))continue;
                if(x.externalId()!=null&&x.externalId().equals(old.externalId()))throw new IllegalArgumentException("That Walgreens offer ID already has a record. Review the existing coupon; redeemed history cannot be reimported.");
                var raw=x.coupon().getTerms().originalOfferText();
                if(imported&&x.externalId()==null&&raw!=null&&old.coupon().getTerms()!=null&&raw.equals(old.coupon().getTerms().originalOfferText())&&
                    (x.coupon().getTerms().offerDetails().get("importKey")==null||Objects.equals(x.coupon().getTerms().offerDetails().get("importKey"),old.coupon().getTerms().offerDetails().get("importKey"))))throw new IllegalArgumentException("Identical source text already has a record. Review the existing coupon instead of importing a duplicate. Distinct offers without identifiers can be entered manually.");
            }
            all.put(x.couponId(),x);
        }
        long after=all.values().stream().filter(x->isWalgreens(x)&&x.status()==CouponStatus.CLIPPED).count();
        Integer limit=null;
        var recorded=all.values().stream().filter(PostgresClippedCouponRepository::isWalgreens).filter(v->v.coupon().getTerms()!=null).sorted(Comparator.comparing((ClippedCoupon v)->Optional.ofNullable(v.coupon().getTerms().importedOn()).orElse(LocalDate.MIN)).reversed()).toList();
        for(var v:recorded)try{var text=v.coupon().getTerms().offerDetails().get("recordedClipLimit");if(text!=null&&Integer.parseInt(text)>=0){limit=Integer.parseInt(text);break;}}catch(NumberFormatException ignored){}
        if(limit!=null&&after>Math.max(limit,before))throw new IllegalArgumentException("Your recorded Walgreens limit is "+limit+" clipped coupons. Remove or review coupons before adding more.");
    }
    @Override public ClippedCoupon redeem(UUID id,UUID transactionId,LocalDate date,long expected,String actor){Objects.requireNonNull(id);Objects.requireNonNull(transactionId);Objects.requireNonNull(date);PostgresRepositorySupport.requireActor(actor,"Redeemed by");return PostgresRepositorySupport.transaction(connections,c->redeem(c,id,transactionId,date,expected,actor));}
    static ClippedCoupon redeem(Connection c,UUID id,UUID transactionId,LocalDate date,long expected,String actor)throws SQLException {
ClippedCoupon old;try(PreparedStatement p=c.prepareStatement("SELECT payload FROM clipped_coupon_current WHERE coupon_id=? FOR UPDATE")){p.setObject(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Coupon does not exist.");old=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);}}if(old.revision()!=expected)throw new ConcurrentModificationException("Coupon revision changed.");if(old.status()==CouponStatus.APPLIED||old.redeemedTransactionId()!=null)throw new IllegalStateException("Coupon has already been redeemed.");if(old.status()!=CouponStatus.CLIPPED&&old.status()!=CouponStatus.AVAILABLE)throw new IllegalStateException("Only clipped or available coupons can be confirmed used.");if(old.coupon().getExpirationDate()!=null&&date.isAfter(old.coupon().getExpirationDate()))throw new IllegalArgumentException("Coupon expired on "+old.coupon().getExpirationDate()+".");if(old.coupon().getTerms()!=null && old.coupon().getTerms().startsOn()!=null && date.isBefore(old.coupon().getTerms().startsOn()))throw new IllegalArgumentException("Coupon is not valid yet.");validateQualification(c,old,transactionId);ClippedCoupon next=new ClippedCoupon(old.couponId(),old.provider(),old.externalId(),old.coupon(),CouponStatus.APPLIED,old.clippedOn(),date,transactionId,expected+1);try{try(PreparedStatement p=c.prepareStatement("INSERT INTO clipped_coupon_redemptions(coupon_id,transaction_id,redeemed_on) VALUES (?,?,?)")){p.setObject(1,id);p.setObject(2,transactionId);p.setObject(3,date);p.executeUpdate();}}catch(SQLException e){if("23505".equals(e.getSQLState()))throw new IllegalStateException("Coupon or transaction has already been used.",e);throw e;}write(c,next);history(c,next,actor,"redeemed");return next;
    }
    private static void validateQualification(Connection c,ClippedCoupon old,UUID receipt)throws SQLException {
        var terms=old.coupon().getTerms();if(terms==null)return; // existing legacy contract stays compatible
        ReceiptSnapshot snapshot;ReceiptMeta metadata;
        try(var p=c.prepareStatement("SELECT contents,metadata FROM receipt_current WHERE transaction_id=? AND status='ACTIVE' FOR UPDATE")) {
            p.setObject(1,receipt);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Active receipt required.");snapshot=PostgresRepositorySupport.deserialize(r.getBytes(1),ReceiptSnapshot.class);metadata=r.getBytes(2)==null?null:PostgresRepositorySupport.deserialize(r.getBytes(2),ReceiptMeta.class);}
        }
        if(terms.storeRestriction()!=null && (metadata==null||!terms.storeRestriction().equalsIgnoreCase(metadata.retailer())))throw new IllegalArgumentException("Coupon store restriction is not satisfied.");
        var lines=snapshot.purchaseItems().stream().filter(l->terms.qualifyingSkus().isEmpty()||terms.qualifyingSkus().contains(l.productCode())).toList();
        if(lines.stream().mapToLong(PurchaseLineSnap::quantity).sum()<old.coupon().getRequiredQuantity())throw new IllegalArgumentException("Coupon qualifying quantity not met.");
        var spend=lines.stream().map(l->l.saleUnitPrice().multiply(java.math.BigDecimal.valueOf(l.quantity()))).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        if(terms.minimumPurchase()!=null&&spend.compareTo(terms.minimumPurchase())<0)throw new IllegalArgumentException("Coupon qualifying purchase amount not met.");
        if(terms.offerId()!=null)try(var p=c.prepareStatement("SELECT cc.payload FROM clipped_coupon_redemptions r JOIN clipped_coupon_current cc ON cc.coupon_id=r.coupon_id WHERE r.transaction_id=?")) {
            p.setObject(1,receipt);int used=0;try(var r=p.executeQuery()){while(r.next()){var coupon=PostgresRepositorySupport.deserialize(r.getBytes(1),ClippedCoupon.class);if(coupon.coupon().getTerms()!=null&&terms.offerId().equals(coupon.coupon().getTerms().offerId()))used++;}}
            if(used>=terms.usageLimit())throw new IllegalArgumentException("Coupon per-transaction usage limit reached for \"" + old.coupon().getCouponDescription() + "\". Remove the saved-coupon link and record this printed deduction as an ordinary manufacturer or store coupon.");
        }
    }
    private static void write(Connection c,ClippedCoupon x)throws SQLException{try(PreparedStatement p=c.prepareStatement("UPDATE clipped_coupon_current SET provider=?,external_id=?,status=?,revision=?,payload=? WHERE coupon_id=?")){p.setString(1,x.provider());p.setString(2,x.externalId());p.setString(3,x.status().name());p.setLong(4,x.revision());p.setBytes(5,PostgresRepositorySupport.serialize(x));p.setObject(6,x.couponId());p.executeUpdate();}}
    private static void history(Connection c,ClippedCoupon x,String actor,String reason)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO clipped_coupon_history(coupon_id,revision,changed_by,reason,payload) VALUES (?,?,?,?,?)")){p.setObject(1,x.couponId());p.setLong(2,x.revision());p.setString(3,actor);p.setString(4,reason);p.setBytes(5,PostgresRepositorySupport.serialize(x));p.executeUpdate();}}
}
