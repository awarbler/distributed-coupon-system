package coupons.repository;

import coupons.promotions.DealTerms;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit catalog deletion: archive evidence and leave all owner-edited plan/receipt data untouched. */
public final class WeeklyDealArchive {
    private final PostgresConnectionFactory db;
    public WeeklyDealArchive(PostgresConnectionFactory db){this.db=db;}
    public Map<String,Object> delete(Map<UUID,Long> expected,String actor,String reason){
        PostgresRepositorySupport.requireActor(actor,"Deleted by");PostgresRepositorySupport.requireActor(reason,"Deletion reason");
        if(expected.isEmpty()||expected.size()>1000)throw new IllegalArgumentException("Select 1 to 1000 recorded Weekly Deals.");
        return PostgresRepositorySupport.transaction(db,c->{
            var snapshots=new LinkedHashMap<UUID,Map<String,Object>>();var sourceIds=new HashMap<UUID,List<UUID>>();
            for(var entry:new TreeMap<>(expected).entrySet()){
                UUID id=entry.getKey();
                try(var p=c.prepareStatement("SELECT * FROM deal_current WHERE deal_id=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){
                    if(!r.next()){try(var q=c.prepareStatement("SELECT 1 FROM deleted_weekly_deals WHERE deal_id=?")){q.setObject(1,id);try(var old=q.executeQuery()){if(old.next())continue;}}throw new NoSuchElementException("Selected deal no longer exists.");}
                    if(r.getLong("revision")!=entry.getValue())throw new ConcurrentModificationException("Weekly Deal changed; refresh before deletion.");
                    var pricing=r.getBytes("pricing");var terms=pricing==null?null:PostgresRepositorySupport.deserialize(pricing,DealTerms.class);
                    if(terms==null||terms.advertisement()==null)throw new IllegalArgumentException("Only Weekly Deal records can be removed by this command.");
                    var snapshot=new LinkedHashMap<String,Object>();snapshot.put("current",row(r));snapshots.put(id,snapshot);
                }}
                var sources=new ArrayList<UUID>();try(var p=c.prepareStatement("SELECT artifact_id FROM deal_sources WHERE deal_id=? UNION SELECT artifact_id FROM deal_history_sources WHERE deal_id=?")){p.setObject(1,id);p.setObject(2,id);try(var r=p.executeQuery()){while(r.next())sources.add(r.getObject(1,UUID.class));}}sourceIds.put(id,sources);
                for(String table:List.of("deal_history","deal_history_sources","deal_sources")){
                    var history=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT * FROM "+table+" WHERE deal_id=?")){p.setObject(1,id);try(var r=p.executeQuery()){while(r.next())history.add(row(r));}}snapshots.get(id).put(table,history);
                }
            }
            for(var entry:snapshots.entrySet()){
                UUID id=entry.getKey();try(var p=c.prepareStatement("INSERT INTO deleted_weekly_deals VALUES(?,?,?,?,?,?,?)")){p.setObject(1,id);p.setLong(2,expected.get(id));p.setString(3,actor);p.setString(4,reason);p.setTimestamp(5,Timestamp.from(Instant.now()));p.setArray(6,c.createArrayOf("uuid",sourceIds.get(id).toArray()));p.setBytes(7,PostgresRepositorySupport.serialize(entry.getValue()));p.executeUpdate();}
                for(String table:List.of("deal_history_sources","deal_sources","deal_history","deal_current"))try(var p=c.prepareStatement("DELETE FROM "+table+" WHERE deal_id=?")){p.setObject(1,id);p.executeUpdate();}
            }
            return Map.of("deleted",snapshots.size(),"ids",List.copyOf(snapshots.keySet()),"plansRequiringReview",referencedPlans(c,expected.keySet()));
        });
    }
    private static List<UUID> referencedPlans(Connection c,Set<UUID> deleted)throws SQLException{
        var plans=new HashSet<UUID>();try(var p=c.prepareStatement("SELECT plan_id,payload FROM planned_purchase_current");var r=p.executeQuery()){while(r.next()){var item=PostgresRepositorySupport.deserialize(r.getBytes(2),coupons.shopping.PlannedPurchase.class);if(deleted.contains(item.advertisedDealId()))plans.add(item.planId());}}
        return List.copyOf(plans);
    }
    public static List<UUID> deletedIds(Connection c)throws SQLException{var ids=new ArrayList<UUID>();try(var p=c.prepareStatement("SELECT deal_id FROM deleted_weekly_deals");var r=p.executeQuery()){while(r.next())ids.add(r.getObject(1,UUID.class));}return ids;}
    private static Map<String,Object> row(ResultSet r)throws SQLException{var out=new LinkedHashMap<String,Object>();var meta=r.getMetaData();for(int i=1;i<=meta.getColumnCount();i++)out.put(meta.getColumnLabel(i),r.getObject(i));return out;}
}
