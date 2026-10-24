package coupons.repository;

import coupons.repository.PostgresPlatformRepository.ReceiptRRMatching;

import java.time.Clock;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.*;

/** Related receipt aggregates using the existing local domain validation, without local files. */
public final class PostgresReceiptSupplementRepository {
    private final PostgresConnectionFactory connections;
    public PostgresReceiptSupplementRepository(PostgresConnectionFactory connections) {this.connections=Objects.requireNonNull(connections);}
    public List<GiftCardPromotion> giftCardPromotions() {
        return transaction(connections,c->PostgresReceiptState.rules(PostgresReceiptState.read(c)).giftCardPromotions());
    }
    public void saveGiftCardPromotion(GiftCardPromotion promotion) {
        transaction(connections,c->{
            PostgresReceiptState.lock(c);
            var state=PostgresReceiptState.read(c);var store=new InMemoryStore(null,state);
            new InMemoryTransactionRepository(store,Clock.systemUTC()).saveGiftCardPromotion(promotion);
            PostgresReceiptState.promotions(c,store.state.giftCardPromotions());return null;
        });
    }
    public List<RRUsageLink> rrUsageLinks() {
        return transaction(connections,c->PostgresReceiptState.rules(PostgresReceiptState.read(c)).rrUsageLinks());
    }
    public boolean rrUsageNeedsReview(RRUsageLink link) {
        return transaction(connections,c->PostgresReceiptState.rules(PostgresReceiptState.read(c)).rrUsageNeedsReview(link));
    }
    public void addStandaloneReward(coupons.RegisterReward reward) {
        if(reward.getEarnedReceiptId()!=null)throw new IllegalArgumentException("Use receipt earning entry for a linked reward.");
        transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            PostgresReceiptState.rules(state).issuedRewards().save(reward);
            PostgresReceiptState.execute(c,"INSERT INTO register_rewards(reward_id,earned_receipt_id,payload) VALUES (?,?,?)",reward.getRewardId(),null,serialize(reward));return null;
        });
    }
    public RRUsageLink addStandaloneMatch(UUID receipt,long revision,UUID use,UUID reward,String description,java.time.LocalDate earned,java.time.LocalDate expires,String reason,String actor) {
        return transaction(connections,c->{
            PostgresReceiptState.lock(c);
            var result=ReceiptRRMatching.standalone(PostgresReceiptState.read(c),receipt,revision,use,reward,description,earned,expires,reason,actor);
            PostgresReceiptState.execute(c,"INSERT INTO register_rewards(reward_id,earned_receipt_id,payload) VALUES (?,?,?)",reward,null,serialize(result.reward()));
            var link=result.link();
            PostgresReceiptState.execute(c,"INSERT INTO rr_usage_links(source_id,usage_event_id,spent_receipt_id,payload) VALUES (?,?,?,?)",reward,use,receipt,serialize(link));
            return link;
        });
    }
    public RRUsageLink matchRR(UUID receiptId,long revision,UUID useId,UUID sourceId,boolean receiptEarning,String reason,String actor,boolean confirmUnknownHistory) {
        return transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            var receipt=state.receipts().get(receiptId);
            if(receipt==null)throw new NoSuchElementException();
            if(receipt.revision()!=revision)throw new ConcurrentModificationException();
            var use=ReceiptRRMatching.options(state,receiptId).stream().filter(u->u.eventId().equals(useId)).findFirst().orElseThrow(()->new IllegalArgumentException("RR use no longer exists."));
            var option=ReceiptRRMatching.select(state,receiptId,useId,sourceId,receiptEarning,confirmUnknownHistory);
            var link=new RRUsageLink(sourceId,receiptEarning,useId,receiptId,use.amount(),reason+(option.historyUnknown()?" | Confirmed available despite unknown earning date/time":""),actor,java.time.Instant.now());
            PostgresReceiptState.rules(state).linkRRUsage(link);
            PostgresReceiptState.execute(c,"INSERT INTO rr_usage_links(source_id,usage_event_id,spent_receipt_id,payload) VALUES (?,?,?,?)",sourceId,useId,receiptId,serialize(link));
            return link;
        });
    }
    public RRUsageLink linkRRUsage(RRUsageLink link) {
        return transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            var store=new InMemoryStore(null,state);
            var result=new InMemoryTransactionRepository(store,Clock.systemUTC()).linkRRUsage(link);
            if(!state.rrUsageLinks().containsKey(link.sourceId()))PostgresReceiptState.execute(c,
                    "INSERT INTO rr_usage_links(source_id,usage_event_id,spent_receipt_id,payload) VALUES (?,?,?,?)",
                    link.sourceId(),link.usageEventId(),link.spentReceiptId(),serialize(link));
            return result;
        });
    }
    public List<RewardVoid> rewardRemovalHistory() {
        return transaction(connections,c->PostgresReceiptState.rules(PostgresReceiptState.read(c)).rewardRemovalHistory());
    }
    public RewardVoid voidRecordedReward(UUID id,String reason,String actor) {
        return transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            var result=PostgresReceiptState.rules(state).voidRecordedReward(id,reason,actor);
            if(!state.rewardVoids().containsKey(id))PostgresReceiptState.execute(c,
                    "INSERT INTO reward_removals(reward_id,payload) VALUES (?,?)",id,serialize(result));
            return result;
        });
    }
}
