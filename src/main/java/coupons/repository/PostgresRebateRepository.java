package coupons.repository;

import coupons.promotions.RebateClaim;
import coupons.promotions.RebateStatus;

import java.sql.*;
import java.util.*;

/** PostgreSQL implementation preserving claim revisions and evidence links. */
public final class PostgresRebateRepository implements RebateRepository {
    private final PostgresConnectionFactory connections;
    private final java.time.Clock clock;
    public PostgresRebateRepository(PostgresConnectionFactory c){this(c,java.time.Clock.systemUTC());}
    public PostgresRebateRepository(PostgresConnectionFactory c,java.time.Clock clock){connections=Objects.requireNonNull(c);this.clock=Objects.requireNonNull(clock);}
    @Override public void save(RebateClaim claim,String actor){
        Objects.requireNonNull(claim);PostgresRepositorySupport.requireActor(actor,"Recorded by");
        if(claim.revision()!=1)throw new IllegalArgumentException("New claim revision must be one.");
        PostgresRepositorySupport.transaction(connections,c->{validate(c,claim);insertCurrent(c,claim,actor);insertHistory(c,claim,actor,"initial claim");return null;});
    }
    private void validate(Connection c,RebateClaim q)throws SQLException{
        PostgresRepositorySupport.validateSources(c,q.sourceArtifactIds());
        if(q.transactionId()!=null)try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM receipt_current WHERE transaction_id=?")){p.setObject(1,q.transactionId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Receipt does not exist: "+q.transactionId());}}
    }
    @Override public Optional<RebateClaim> findById(UUID id){Objects.requireNonNull(id);return row(id,null);}
    @Override public Optional<RebateClaim> findByRevision(UUID id,long rev){Objects.requireNonNull(id);if(rev<1)throw new IllegalArgumentException("Revision must be positive.");return row(id,rev);}
    private Optional<RebateClaim> row(UUID id,Long rev){return PostgresRepositorySupport.transaction(connections,c->{String table=rev==null?"rebate_current":"rebate_history";String sourceTable=rev==null?"rebate_sources":"rebate_history_sources";String w=rev==null?"claim_id=?":"claim_id=? AND revision=?";try(PreparedStatement p=c.prepareStatement("SELECT claim_id,provider,external_claim_ref,planned_purchase_id,transaction_id,status,provider_status,expected_benefits,confirmed_benefits,submitted_on,fulfilled_on,revision FROM "+table+" WHERE "+w)){p.setObject(1,id);if(rev!=null)p.setLong(2,rev);try(ResultSet r=p.executeQuery()){if(!r.next())return Optional.empty();return Optional.of(withSources(c,read(r),sourceTable));}}});}
    @Override public List<RebateClaim> findByReceiptId(UUID id){return findRefs("transaction_id",id);}
    @Override public List<RebateClaim> findByPlannedPurchaseId(UUID id){return findRefs("planned_purchase_id",id);}
    private List<RebateClaim> findRefs(String col,UUID id){Objects.requireNonNull(id);return PostgresRepositorySupport.transaction(connections,c->{List<RebateClaim> out=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT claim_id,provider,external_claim_ref,planned_purchase_id,transaction_id,status,provider_status,expected_benefits,confirmed_benefits,submitted_on,fulfilled_on,revision FROM rebate_current WHERE "+col+"=? ORDER BY claim_id")){p.setObject(1,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(withSources(c,read(r),"rebate_sources"));}}return List.copyOf(out);});}
    @Override public RebateClaim update(RebateClaim q,long expected,String actor,String reason){
        Objects.requireNonNull(q);PostgresRepositorySupport.requireActor(actor,"Changed by");PostgresRepositorySupport.requireActor(reason,"Reason");
        if(expected<1||q.revision()!=expected+1)throw new IllegalArgumentException("Invalid claim revision.");
        return PostgresRepositorySupport.transaction(connections,c->{validate(c,q);long stored;try(PreparedStatement p=c.prepareStatement("SELECT revision FROM rebate_current WHERE claim_id=? FOR UPDATE")){p.setObject(1,q.claimId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Claim does not exist.");stored=r.getLong(1);}}if(stored!=expected)throw new ConcurrentModificationException("Claim revision changed.");
            try(PreparedStatement p=c.prepareStatement("DELETE FROM rebate_sources WHERE claim_id=?")){p.setObject(1,q.claimId());p.executeUpdate();}insertCurrent(c,q,actor);insertHistory(c,q,actor,reason);return q;});
    }
    private void insertCurrent(Connection c,RebateClaim q,String actor)throws SQLException{
        String sql="INSERT INTO rebate_current VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (claim_id) DO UPDATE SET provider=EXCLUDED.provider,external_claim_ref=EXCLUDED.external_claim_ref,planned_purchase_id=EXCLUDED.planned_purchase_id,transaction_id=EXCLUDED.transaction_id,status=EXCLUDED.status,provider_status=EXCLUDED.provider_status,expected_benefits=EXCLUDED.expected_benefits,confirmed_benefits=EXCLUDED.confirmed_benefits,submitted_on=EXCLUDED.submitted_on,fulfilled_on=EXCLUDED.fulfilled_on,revision=EXCLUDED.revision,recorded_by=EXCLUDED.recorded_by";
        try(PreparedStatement p=c.prepareStatement(sql)){bind(p,q);p.setLong(12,q.revision());p.setString(13,actor);p.executeUpdate();}PostgresRepositorySupport.insertSources(c,"rebate_sources","claim_id",q.claimId(),q.sourceArtifactIds());
    }
    private void insertHistory(Connection c,RebateClaim q,String actor,String reason)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO rebate_history VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            p.setObject(1,q.claimId());
            p.setLong(2,q.revision());
            p.setString(3,q.provider());
            p.setString(4,q.externalClaimRef());
            p.setObject(5,q.plannedPurchaseId());
            p.setObject(6,q.transactionId());
            p.setString(7,q.status().name());
            p.setString(8,q.providerStatus());
            p.setBytes(9,PostgresRepositorySupport.serialize(q.expectedBenefits()));
            p.setBytes(10,PostgresRepositorySupport.serialize(q.confirmedBenefits()));
            p.setObject(11,q.submittedOn());
            p.setObject(12,q.fulfilledOn());
            p.setString(13,actor);
            p.setString(14,reason);
            p.setTimestamp(15,Timestamp.from(clock.instant()));
            p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("INSERT INTO rebate_history_sources VALUES (?,?,?)")){for(UUID s:q.sourceArtifactIds()){p.setObject(1,q.claimId());p.setLong(2,q.revision());p.setObject(3,s);p.addBatch();}p.executeBatch();}
    }
    private static void bind(PreparedStatement p,RebateClaim q)throws SQLException{p.setObject(1,q.claimId());p.setString(2,q.provider());p.setString(3,q.externalClaimRef());p.setObject(4,q.plannedPurchaseId());p.setObject(5,q.transactionId());p.setString(6,q.status().name());p.setString(7,q.providerStatus());p.setBytes(8,PostgresRepositorySupport.serialize(q.expectedBenefits()));p.setBytes(9,PostgresRepositorySupport.serialize(q.confirmedBenefits()));p.setObject(10,q.submittedOn());p.setObject(11,q.fulfilledOn());}
    private static RebateClaim read(ResultSet r)throws SQLException{return new RebateClaim((UUID)r.getObject(1),r.getString(2),r.getString(3),(UUID)r.getObject(4),(UUID)r.getObject(5),RebateStatus.valueOf(r.getString(6)),r.getString(7),PostgresRepositorySupport.deserialize(r.getBytes(8),List.class),PostgresRepositorySupport.deserialize(r.getBytes(9),List.class),List.of(UUID.randomUUID()),r.getObject(10,java.time.LocalDate.class),r.getObject(11,java.time.LocalDate.class),r.getLong(12));}
    private static RebateClaim withSources(Connection c,RebateClaim q,String table)throws SQLException{return new RebateClaim(q.claimId(),q.provider(),q.externalClaimRef(),q.plannedPurchaseId(),q.transactionId(),q.status(),q.providerStatus(),q.expectedBenefits(),q.confirmedBenefits(),new ArrayList<>(table.equals("rebate_sources")?PostgresRepositorySupport.sourceIds(c,table,"claim_id",q.claimId()):PostgresRepositorySupport.sourceIds(c,table,"claim_id",q.claimId(),q.revision())),q.submittedOn(),q.fulfilledOn(),q.revision());}
}
