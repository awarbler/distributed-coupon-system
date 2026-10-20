package coupons.repository;

import coupons.AdvertisedDeal;
import coupons.DealTerms;
import coupons.SourceArtifact;
import java.sql.*;
import java.util.*;

/** PostgreSQL implementation preserving every advertised-deal revision. */
public final class PostgresDealRepository implements DealRepository {
    private final PostgresConnectionFactory connections;
    private final java.time.Clock clock;
    public PostgresDealRepository(PostgresConnectionFactory c) { this(c, java.time.Clock.systemUTC()); }
    public PostgresDealRepository(PostgresConnectionFactory c, java.time.Clock clock) {
        connections=Objects.requireNonNull(c); this.clock=Objects.requireNonNull(clock);
    }
    @Override public void save(AdvertisedDeal d,String actor) {
        save(d,actor,null);
    }
    /** Manual input is original provenance; optional uploaded bytes are preserved in the same transaction. */
    public void save(AdvertisedDeal d,String actor,SourceArtifact source) {
        Objects.requireNonNull(d); PostgresRepositorySupport.requireActor(actor,"Recorded by");
        if(d.revision()!=1) throw new IllegalArgumentException("New deal revision must be one.");
        PostgresRepositorySupport.transaction(connections,c->{
            if(source!=null) {
                try(var p=c.prepareStatement("INSERT INTO source_artifacts VALUES (?,?,?,?,?,?)")) {
                    p.setObject(1,source.getArtifactId());p.setString(2,source.getLabel());p.setString(3,source.getMediaType());
                    p.setString(4,source.getSourceReference());p.setTimestamp(5,Timestamp.from(source.getCapturedAt()));p.setBytes(6,source.getContent());p.executeUpdate();
                }
            }
            PostgresRepositorySupport.validateSources(c,d.sourceArtifactIds());
            try(PreparedStatement p=c.prepareStatement("INSERT INTO deal_current(deal_id,title,interpreted_terms,retailer,valid_from,valid_through,revision,recorded_by,pricing,manual_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                bind(p,d);p.setLong(7,1);p.setString(8,actor);p.setBytes(9,d.pricing()==null?null:PostgresRepositorySupport.serialize(d.pricing()));p.setString(10,source==null?null:fingerprint(d));p.executeUpdate();
            } catch(SQLException e){if("23505".equals(e.getSQLState()))throw new IllegalArgumentException("Deal ID already exists.",e);throw e;}
            PostgresRepositorySupport.insertSources(c,"deal_sources","deal_id",d.dealId(),d.sourceArtifactIds());
            insertHistory(c,d,actor,"initial interpretation"); return null;
        });
    }
    @Override public Optional<AdvertisedDeal> findById(UUID id){Objects.requireNonNull(id);return row(id,null);}
    @Override public Optional<AdvertisedDeal> findByRevision(UUID id,long rev){
        Objects.requireNonNull(id);if(rev<1)throw new IllegalArgumentException("Revision must be positive.");return row(id,rev);
    }
    private Optional<AdvertisedDeal> row(UUID id,Long revision){
        return PostgresRepositorySupport.transaction(connections,c->{
            String table=revision==null?"deal_current":"deal_history"; String where=revision==null?"deal_id=?":"deal_id=? AND revision=?";
            try(PreparedStatement p=c.prepareStatement("SELECT deal_id,title,interpreted_terms,retailer,valid_from,valid_through,revision,pricing FROM "+table+" WHERE "+where)){
                p.setObject(1,id);if(revision!=null)p.setLong(2,revision);try(ResultSet r=p.executeQuery()){
                    if(!r.next())return Optional.empty();AdvertisedDeal d=read(r);return Optional.of(withSources(c,d,revision==null?"deal_sources":"deal_history_sources"));
                }
            }
        });
    }
    @Override public List<AdvertisedDeal> findBySourceId(UUID source){
        Objects.requireNonNull(source);return PostgresRepositorySupport.transaction(connections,c->{
            List<AdvertisedDeal> out=new ArrayList<>();try(PreparedStatement p=c.prepareStatement("SELECT d.deal_id,d.title,d.interpreted_terms,d.retailer,d.valid_from,d.valid_through,d.revision,d.pricing FROM deal_current d JOIN deal_sources s ON s.deal_id=d.deal_id WHERE s.artifact_id=? ORDER BY d.deal_id")){
                p.setObject(1,source);try(ResultSet r=p.executeQuery()){while(r.next()){AdvertisedDeal d=read(r);out.add(withSources(c,d,"deal_sources"));}}
            }return List.copyOf(out);
        });
    }
    @Override public AdvertisedDeal update(AdvertisedDeal d,long expected,String actor,String reason){
        Objects.requireNonNull(d);PostgresRepositorySupport.requireActor(actor,"Changed by");PostgresRepositorySupport.requireActor(reason,"Reason");
        if(d.revision()!=expected+1||expected<1)throw new IllegalArgumentException("Invalid deal revision.");
        return PostgresRepositorySupport.transaction(connections,c->{PostgresRepositorySupport.validateSources(c,d.sourceArtifactIds());
            long stored;try(PreparedStatement p=c.prepareStatement("SELECT revision FROM deal_current WHERE deal_id=? FOR UPDATE")){p.setObject(1,d.dealId());try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Deal does not exist.");stored=r.getLong(1);}}
            if(stored!=expected)throw new java.util.ConcurrentModificationException("Deal revision changed.");
            try(PreparedStatement p=c.prepareStatement("UPDATE deal_current SET title=?,interpreted_terms=?,retailer=?,valid_from=?,valid_through=?,revision=?,pricing=? WHERE deal_id=?")){p.setString(1,d.title());p.setString(2,d.interpretedTerms());p.setString(3,d.retailer());p.setObject(4,d.validFrom());p.setObject(5,d.validThrough());p.setLong(6,d.revision());p.setBytes(7,d.pricing()==null?null:PostgresRepositorySupport.serialize(d.pricing()));p.setObject(8,d.dealId());p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("DELETE FROM deal_sources WHERE deal_id=?")){p.setObject(1,d.dealId());p.executeUpdate();}PostgresRepositorySupport.insertSources(c,"deal_sources","deal_id",d.dealId(),d.sourceArtifactIds());insertHistory(c,d,actor,reason);return d;
        });
    }
    private static String fingerprint(AdvertisedDeal d)throws java.security.NoSuchAlgorithmException {
        var t=d.pricing();var values=new ArrayList<Object>();
        values.add(d.retailer()==null?null:d.retailer().trim().toLowerCase(Locale.ROOT));values.add(d.title().trim().toLowerCase(Locale.ROOT));values.add(d.interpretedTerms().trim());values.add(d.validFrom());values.add(d.validThrough());
        if(t!=null){values.addAll(Arrays.asList(t.type(),t.productDescription(),t.minimumQuantity(),t.freeQuantity(),t.repeatable(),t.limit(),t.qualifications(),t.loyaltyRequirement(),t.exclusions(),t.cheapestItemsDiscounted()));for(var n:new java.math.BigDecimal[]{t.regularUnitPrice(),t.promotionalPrice(),t.minimumSpend(),t.rewardAmount()})values.add(n==null?null:n.stripTrailingZeros().toPlainString());values.add(t.qualifyingSkus().stream().sorted().toList());}
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(values.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static AdvertisedDeal read(ResultSet r)throws SQLException{return new AdvertisedDeal((UUID)r.getObject(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,java.time.LocalDate.class),r.getObject(6,java.time.LocalDate.class),List.of(UUID.randomUUID()),r.getLong(7),r.getBytes(8)==null?null:PostgresRepositorySupport.deserialize(r.getBytes(8),DealTerms.class));}
    private static AdvertisedDeal withSources(Connection c,AdvertisedDeal d,String table)throws SQLException{return new AdvertisedDeal(d.dealId(),d.title(),d.interpretedTerms(),d.retailer(),d.validFrom(),d.validThrough(),new ArrayList<>(table.equals("deal_sources")?PostgresRepositorySupport.sourceIds(c,table,"deal_id",d.dealId()):PostgresRepositorySupport.sourceIds(c,table,"deal_id",d.dealId(),d.revision())),d.revision(),d.pricing());}
    private static void bind(PreparedStatement p,AdvertisedDeal d)throws SQLException{p.setObject(1,d.dealId());p.setString(2,d.title());p.setString(3,d.interpretedTerms());p.setString(4,d.retailer());p.setObject(5,d.validFrom());p.setObject(6,d.validThrough());}
    private void insertHistory(Connection c,AdvertisedDeal d,String actor,String reason)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO deal_history(deal_id,revision,title,interpreted_terms,retailer,valid_from,valid_through,changed_by,reason,committed_at,pricing) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){p.setObject(1,d.dealId());p.setLong(2,d.revision());p.setString(3,d.title());p.setString(4,d.interpretedTerms());p.setString(5,d.retailer());p.setObject(6,d.validFrom());p.setObject(7,d.validThrough());p.setString(8,actor);p.setString(9,reason);p.setTimestamp(10,Timestamp.from(clock.instant()));p.setBytes(11,d.pricing()==null?null:PostgresRepositorySupport.serialize(d.pricing()));p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO deal_history_sources(deal_id,revision,artifact_id) VALUES (?,?,?)")){for(UUID s:d.sourceArtifactIds()){p.setObject(1,d.dealId());p.setLong(2,d.revision());p.setObject(3,s);p.addBatch();}p.executeBatch();}
    }
}
