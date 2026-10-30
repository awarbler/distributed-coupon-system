package coupons.repository;

import coupons.rewards.RegisterReward;
import coupons.rewards.RegisterRewardRedemption;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.*;

/** Shared exact snapshot mapping for migration and existing receipt-reference validation. */
final class PostgresReceiptState {
    private PostgresReceiptState() {}
    static final long LOCK = 7241873006L;
    static void lock(Connection c) throws SQLException {
        try (var p=c.prepareStatement("SELECT pg_advisory_xact_lock(?)")) { p.setLong(1,LOCK);p.execute(); }
    }
    static InMemoryTransactionRepository rules(InMemoryStore.State state) {
        return new InMemoryTransactionRepository(new InMemoryStore(null,state),Clock.systemUTC());
    }
    static InMemoryStore.State read(Connection c) throws SQLException {
        Map<UUID,ReceiptRevSnap> receipts=new HashMap<>();
        try(var p=c.prepareStatement("SELECT transaction_id,status,revision,contents,metadata,evidence_ids FROM receipt_current");var r=p.executeQuery()) {
            while(r.next()) {
                UUID id=r.getObject(1,UUID.class);
                @SuppressWarnings("unchecked") List<UUID> evidence=(List<UUID>)deserialize(r.getBytes(6),List.class);
                receipts.put(id,new ReceiptRevSnap(id,r.getLong(3),ReceiptStatus.valueOf(r.getString(2)),
                        deserialize(r.getBytes(4),ReceiptSnapshot.class),r.getBytes(5)==null?null:deserialize(r.getBytes(5),ReceiptMeta.class),evidence));
            }
        }
        var rewards=map(c,"SELECT reward_id,payload FROM register_rewards",RegisterReward.class);
        var redemptions=map(c,"SELECT redemption_id,payload FROM register_reward_redemptions",RegisterRewardRedemption.class);
        Map<UUID,ReceiptEvidence> evidence=new HashMap<>();
        try(var p=c.prepareStatement("SELECT evidence_id,transaction_id,media_type,content FROM receipt_evidence");var r=p.executeQuery()) {
            while(r.next()) {UUID id=r.getObject(1,UUID.class);evidence.put(id,new ReceiptEvidence(id,r.getObject(2,UUID.class),r.getString(3),r.getBytes(4)));}
        }
        var history=map(c,"SELECT history_id,payload FROM receipt_history",ReceiptHistoryEntry.class);
        Map<UUID,InMemoryStore.CommittedWrite> requests=new HashMap<>();
        try(var p=c.prepareStatement("SELECT request_id,payload,result FROM receipt_write_requests");var r=p.executeQuery()) {
            while(r.next())requests.put(r.getObject(1,UUID.class),new InMemoryStore.CommittedWrite(
                    PostgresTransactionRepository.localInput(deserialize(r.getBytes(2),Object.class)),deserialize(r.getBytes(3),ReceiptWriteResult.class)));
        }
        var voids=map(c,"SELECT reward_id,payload FROM reward_removals",RewardVoid.class);
        var promotions=map(c,"SELECT promotion_id,payload FROM gift_card_promotions",GiftCardPromotion.class);
        var links=map(c,"SELECT source_id,payload FROM rr_usage_links",RRUsageLink.class);
        var openings=list(c,"SELECT payload FROM provider_account_openings ORDER BY source_sequence NULLS LAST,recorded_at,observation_id",AccountOpening.class);
        var adjustments=list(c,"SELECT payload FROM provider_account_adjustments ORDER BY source_sequence NULLS LAST,recorded_at,event_id",AccountAdjustment.class);
        var providerHistory=list(c,"SELECT payload FROM provider_redemptions_history ORDER BY source_sequence NULLS LAST,recorded_at,redemption_id,revision",ProviderRedemption.class);
        return new InMemoryStore.State(receipts,rewards,redemptions,evidence,history,requests,voids,promotions,
                new AccountBalanceState(openings,adjustments,providerHistory),links);
    }
    private static <T> Map<UUID,T> map(Connection c,String sql,Class<T> type)throws SQLException {
        Map<UUID,T> out=new HashMap<>();try(var p=c.prepareStatement(sql);var r=p.executeQuery()){while(r.next())out.put(r.getObject(1,UUID.class),deserialize(r.getBytes(2),type));}return out;
    }
    private static <T> List<T> list(Connection c,String sql,Class<T> type)throws SQLException {
        List<T> out=new ArrayList<>();try(var p=c.prepareStatement(sql);var r=p.executeQuery()){while(r.next())out.add(deserialize(r.getBytes(1),type));}return out;
    }
    static void execute(Connection c,String sql,Object... values)throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
    }
    static void promotions(Connection c,Map<UUID,GiftCardPromotion> values)throws SQLException {
        for(var p:values.values())execute(c,"INSERT INTO gift_card_promotions(promotion_id,payload) VALUES (?,?) ON CONFLICT(promotion_id) DO UPDATE SET payload=excluded.payload",p.id(),serialize(p));
    }
    /** Destination must be empty. Copies committed records, never replays business operations. */
    static void insert(Connection c,InMemoryStore.State s)throws SQLException {
        for(var r:s.receipts().values())execute(c,"INSERT INTO receipt_current(transaction_id,status,revision,contents,metadata,evidence_ids) VALUES (?,?,?,?,?,?)",r.transactionId(),r.status().name(),r.revision(),serialize(r.contents()),r.metadata()==null?null:serialize(r.metadata()),serialize(r.evidenceIds()));
        for(var r:s.rewards().values())execute(c,"INSERT INTO register_rewards(reward_id,earned_receipt_id,payload) VALUES (?,?,?)",r.getRewardId(),r.getEarnedReceiptId(),serialize(r));
        for(var r:s.redemptions().values())execute(c,"INSERT INTO register_reward_redemptions(redemption_id,reward_id,receipt_id,payload) VALUES (?,?,?,?)",r.getRedemptionId(),r.getRewardId(),r.getRedeemedReceiptId(),serialize(r));
        for(var e:s.evidence().values())execute(c,"INSERT INTO receipt_evidence(evidence_id,transaction_id,media_type,content) VALUES (?,?,?,?)",e.getEvidenceId(),e.getTransactionId(),e.getMediaType(),e.getContent());
        for(var h:s.history().values())execute(c,"INSERT INTO receipt_history(history_id,transaction_id,revision,request_id,payload) VALUES (?,?,?,?,?)",h.historyId(),h.after().transactionId(),h.after().revision(),h.request().requestId(),serialize(h));
        for(var entry:s.requests().entrySet()) {
            var w=entry.getValue();execute(c,"INSERT INTO receipt_write_requests(request_id,transaction_id,payload,result) VALUES (?,?,?,?)",entry.getKey(),w.result().transactionId(),serialize(PostgresTransactionRepository.postgresInput(w.input())),serialize(w.result()));
        }
        promotions(c,s.giftCardPromotions());
        for(var r:s.rewardVoids().values())execute(c,"INSERT INTO reward_removals(reward_id,payload) VALUES (?,?)",r.rewardId(),serialize(r));
        for(var r:s.rrUsageLinks().values())execute(c,"INSERT INTO rr_usage_links(source_id,usage_event_id,spent_receipt_id,payload) VALUES (?,?,?,?)",r.sourceId(),r.usageEventId(),r.spentReceiptId(),serialize(r));
        long sequence=0;
        for(var o:s.balances().openings())execute(c,"INSERT INTO provider_account_openings(observation_id,provider,observed_date,original_units,usd_value,month_start,valuation_basis,source_note,recorded_at,payload,source_sequence) VALUES (?,?,?,?,?,?,?,?,?,?,?)",o.observationId(),o.provider(),o.date(),o.originalUnits(),o.usdValue(),o.monthStart(),o.valuationBasis(),o.sourceNote(),Timestamp.from(o.recordedAt()),serialize(o),sequence++);
        sequence=0;
        for(var a:s.balances().adjustments())execute(c,"INSERT INTO provider_account_adjustments(event_id,provider,event_date,unit_change,usd_change,reason,recorded_by,recorded_at,payload,source_sequence) VALUES (?,?,?,?,?,?,?,?,?,?)",a.eventId(),a.provider(),a.date(),a.unitChange(),a.usdChange(),a.reason(),a.recordedBy(),Timestamp.from(a.recordedAt()),serialize(a),sequence++);
        sequence=0;Map<UUID,Long> revisions=new HashMap<>();
        for(var r:s.balances().redemptionHistory()) {
            long revision=revisions.merge(r.redemptionId(),1L,Long::sum);
            execute(c,"INSERT INTO provider_redemptions_current(redemption_id,provider,revision,status,payload,recorded_at) VALUES (?,?,?,?,?,NULL) ON CONFLICT(redemption_id) DO UPDATE SET provider=excluded.provider,revision=excluded.revision,status=excluded.status,payload=excluded.payload",r.redemptionId(),r.provider(),revision,r.status().name(),serialize(r));
            execute(c,"INSERT INTO provider_redemptions_history(redemption_id,revision,provider,status,payload,recorded_at,source_sequence) VALUES (?,?,?,?,?,NULL,?)",r.redemptionId(),revision,r.provider(),r.status().name(),serialize(r),sequence++);
        }
    }
    /** Verify queryable columns as well as serialized values, including latest transfer revisions. */
    static void validateProjections(Connection c,InMemoryStore.State s)throws SQLException {
        for(var r:s.rewards().values())check(c,"SELECT earned_receipt_id FROM register_rewards WHERE reward_id=?",List.of(r.getRewardId()),r.getEarnedReceiptId());
        for(var r:s.redemptions().values())check(c,"SELECT reward_id,receipt_id FROM register_reward_redemptions WHERE redemption_id=?",List.of(r.getRedemptionId()),r.getRewardId(),r.getRedeemedReceiptId());
        for(var h:s.history().values())check(c,"SELECT transaction_id,revision,request_id FROM receipt_history WHERE history_id=?",List.of(h.historyId()),h.after().transactionId(),h.after().revision(),h.request().requestId());
        for(var w:s.requests().entrySet())check(c,"SELECT transaction_id FROM receipt_write_requests WHERE request_id=?",List.of(w.getKey()),w.getValue().result().transactionId());
        for(var l:s.rrUsageLinks().values())check(c,"SELECT usage_event_id,spent_receipt_id FROM rr_usage_links WHERE source_id=?",List.of(l.sourceId()),l.usageEventId(),l.spentReceiptId());
        long sequence=0;
        for(var o:s.balances().openings())check(c,"SELECT provider,observed_date,original_units,usd_value,month_start,valuation_basis,source_note,source_sequence FROM provider_account_openings WHERE observation_id=?",List.of(o.observationId()),o.provider(),o.date(),o.originalUnits(),o.usdValue(),o.monthStart(),o.valuationBasis(),o.sourceNote(),sequence++);
        sequence=0;
        for(var a:s.balances().adjustments())check(c,"SELECT provider,event_date,unit_change,usd_change,reason,recorded_by,source_sequence FROM provider_account_adjustments WHERE event_id=?",List.of(a.eventId()),a.provider(),a.date(),a.unitChange(),a.usdChange(),a.reason(),a.recordedBy(),sequence++);
        sequence=0;Map<UUID,Long> revisions=new HashMap<>();Map<UUID,ProviderRedemption> latest=new HashMap<>();
        for(var r:s.balances().redemptionHistory()) {
            long revision=revisions.merge(r.redemptionId(),1L,Long::sum);latest.put(r.redemptionId(),r);
            check(c,"SELECT provider,status,source_sequence FROM provider_redemptions_history WHERE redemption_id=? AND revision=?",List.of(r.redemptionId(),revision),r.provider(),r.status().name(),sequence++);
        }
        for(var r:latest.values()) {
            check(c,"SELECT provider,status,revision FROM provider_redemptions_current WHERE redemption_id=?",List.of(r.redemptionId()),r.provider(),r.status().name(),revisions.get(r.redemptionId()));
            try(var p=c.prepareStatement("SELECT payload FROM provider_redemptions_current WHERE redemption_id=?")) {
                p.setObject(1,r.redemptionId());try(var rows=p.executeQuery()){if(!rows.next()||!r.equals(deserialize(rows.getBytes(1),ProviderRedemption.class)))throw new IllegalStateException("Validation failed: latest provider redemption.");}
            }
        }
        try(var p=c.prepareStatement("SELECT count(*) FROM provider_redemptions_current");var r=p.executeQuery()){r.next();if(r.getLong(1)!=latest.size())throw new IllegalStateException("Unexpected provider redemptions.");}
    }
    private static void check(Connection c,String sql,List<?> parameters,Object... expected)throws SQLException {
        try(var p=c.prepareStatement(sql)) {
            for(int i=0;i<parameters.size();i++)p.setObject(i+1,parameters.get(i));
            try(var r=p.executeQuery()) {
                if(!r.next())throw new IllegalStateException("Missing migration projection.");
                for(int i=0;i<expected.length;i++) {
                    Object actual=r.getObject(i+1),value=expected[i];boolean equal;
                    if(actual==null||value==null)equal=actual==value;
                    else if(actual instanceof Number&&value instanceof Number)equal=new java.math.BigDecimal(actual.toString()).compareTo(new java.math.BigDecimal(value.toString()))==0;
                    else equal=actual.toString().equals(value.toString());
                    if(!equal)throw new IllegalStateException("Validation failed: relational projection.");
                }
                if(r.next())throw new IllegalStateException("Duplicate migration projection.");
            }
        }
    }
    static void compare(InMemoryStore.State expected,InMemoryStore.State actual) {
        same("receipts (including all financial values and observations)",expected.receipts(),actual.receipts());
        same("history",expected.history(),actual.history());same("requests",expected.requests(),actual.requests());
        same("evidence",expected.evidence(),actual.evidence());same("redemptions",expected.redemptions(),actual.redemptions());
        same("promotion progress",expected.giftCardPromotions(),actual.giftCardPromotions());
        same("RR matches",expected.rrUsageLinks(),actual.rrUsageLinks());same("reward removals",expected.rewardVoids(),actual.rewardVoids());
        same("provider records and original ordering",expected.balances(),actual.balances());
        same("reward IDs",expected.rewards().keySet(),actual.rewards().keySet());
        for(var id:expected.rewards().keySet())if(!Arrays.equals(serialize(expected.rewards().get(id)),serialize(actual.rewards().get(id))))throw new IllegalStateException("Validation failed: reward "+id);
    }
    private static void same(String field,Object a,Object b){if(!Objects.equals(a,b))throw new IllegalStateException("Validation failed: "+field);}
}
