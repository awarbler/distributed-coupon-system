package coupons.repository;

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
