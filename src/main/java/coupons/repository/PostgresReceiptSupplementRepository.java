package coupons.repository;

import coupons.repository.PostgresPlatformRepository.ReceiptRRMatching;

import java.time.Clock;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.*;

/** Related receipt aggregates using the existing local domain validation, without local files. */
public final class PostgresReceiptSupplementRepository {
    private final PostgresConnectionFactory connections;
    public PostgresReceiptSupplementRepository(PostgresConnectionFactory connections) {this.connections=Objects.requireNonNull(connections);}
    public void unlinkRebateReceipt(UUID rebateId,UUID receiptId,String expectedContribution){
        transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            var rebate=Optional.ofNullable(state.giftCardPromotions().get(rebateId)).orElseThrow(()->new NoSuchElementException("Rebate not found."));
            var existing=rebate.contributions().get(receiptId);
            if(existing==null||!GiftCardPromotion.contributionToken(existing).equals(expectedContribution))throw new ConcurrentModificationException("Rebate receipt link changed; refresh before unlinking.");
            var links=new HashMap<>(rebate.contributions());links.remove(receiptId);
            var updated=new GiftCardPromotion(rebate.id(),rebate.name(),rebate.threshold(),rebate.giftCardValue(),rebate.start(),rebate.end(),rebate.basis(),rebate.terms(),rebate.status(),links);
            PostgresReceiptState.promotions(c,Map.of(rebateId,updated));return null;
        });
    }
    public Map<String,Object> saveRebateReceipt(UUID rebateId,UUID receiptId,long receiptRevision,List<UUID> lines,java.math.BigDecimal netAmount,
            String note,boolean submitted,java.time.LocalDate submittedOn,String reference,String expectedContribution){
        return transaction(connections,c->{
            PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);
            var rebate=Optional.ofNullable(state.giftCardPromotions().get(rebateId)).orElseThrow(()->new NoSuchElementException("Rebate not found."));
            var stored=Optional.ofNullable(state.receipts().get(receiptId)).orElseThrow(()->new NoSuchElementException("Receipt not found."));
            var savedLink=rebate.contributions().get(receiptId);
            if(savedLink!=null&&savedLink.reviewedRevision()!=receiptRevision||savedLink==null&&stored.revision()!=receiptRevision)throw new ConcurrentModificationException("Reload the linked receipt before editing.");
            var captured=savedLink==null?stored:state.history().values().stream().map(ReceiptHistoryEntry::after).filter(r->r.transactionId().equals(receiptId)&&r.revision()==savedLink.reviewedRevision()).findFirst().orElseThrow(()->new IllegalArgumentException("Linked receipt revision is unavailable."));
            var receipt=new ReceiptRecord(captured.transactionId(),captured.status(),captured.revision(),captured.contents().toTransaction(),captured.metadata(),captured.evidenceIds());
            if(receipt.status()!=ReceiptStatus.ACTIVE)throw new IllegalArgumentException("Only active receipts can be linked.");
            if(submittedOn!=null&&receipt.transaction().getObservations().purchaseDate()!=null&&submittedOn.isBefore(receipt.transaction().getObservations().purchaseDate()))throw new IllegalArgumentException("Submission date cannot be before the purchase date.");
            var existing=rebate.contributions().get(receiptId);
            if(!GiftCardPromotion.contributionToken(existing).equals(expectedContribution))throw new ConcurrentModificationException("Rebate receipt link changed; refresh before editing.");
            if(netAmount==null)throw new IllegalArgumentException("Enter the eligible amount after coupons.");
            var contribution=new GiftCardPromotion.Contribution(receiptId,receiptRevision,lines,netAmount,note==null?"":note,true,submitted,submittedOn,reference);
            if(rebate.eligibleAmount(contribution,receipt).isEmpty())throw new IllegalArgumentException("Check receipt date, eligible products and rebate dates.");
            // Validate against the pinned audit revision above, not today's receipt.
            PostgresReceiptState.promotions(c,Map.of(rebateId,rebate.withContribution(contribution)));
            return Map.of("receiptId",receiptId,"token",GiftCardPromotion.contributionToken(contribution));
        });
    }
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
    public void correctStandaloneAmount(UUID id,java.math.BigDecimal expected,java.math.BigDecimal amount) {
        if(amount.signum()<=0)throw new IllegalArgumentException("Enter a positive reward amount.");
        transaction(connections,c->{PostgresReceiptState.lock(c);var state=PostgresReceiptState.read(c);var old=state.rewards().get(id);
            if(old==null)throw new NoSuchElementException();
            if(old.getEarnedReceiptId()!=null||state.rrUsageLinks().containsKey(id)||state.redemptions().values().stream().anyMatch(r->r.getRewardId().equals(id)))throw new IllegalArgumentException("Unmatch or review the used reward before correcting its denomination.");
            if(old.getFaceValue().compareTo(expected)!=0)throw new ConcurrentModificationException("Reward amount changed; refresh first.");
            var updated=new coupons.RegisterReward(id,old.getSourceDescription(),amount,old.getEarnedDate(),old.getExpirationDate(),null);
            PostgresReceiptState.execute(c,"UPDATE register_rewards SET payload=? WHERE reward_id=?",serialize(updated),id);return null;});
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
