package coupons.repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Explicit identities and append-only observed activity; never infers legacy account ownership. */
public final class RewardInstrumentRepository {
    private final PostgresConnectionFactory db;
    public RewardInstrumentRepository(PostgresConnectionFactory db){this.db=Objects.requireNonNull(db);}
    /** Additive V13 resource is executed whole so trigger bodies retain their statement boundaries. */
    static void install(Connection connection)throws java.io.IOException,SQLException {
        try(var input=RewardInstrumentRepository.class.getResourceAsStream("/db/migration/V13__reward_instruments.sql")){
            if(input==null)throw new java.io.IOException("Missing reward instrument resource");
            try(var statement=connection.createStatement()){
                statement.execute(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                statement.executeUpdate("INSERT INTO schema_version(version,description) VALUES(13,'individual reward accounts and gift cards') ON CONFLICT(version) DO NOTHING");
            }
        }
    }
    public List<Map<String,Object>> list(){return PostgresRepositorySupport.transaction(db,c->{
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);
        return list(c);
    });}
    static List<Map<String,Object>> list(Connection c)throws SQLException {
        var result=new ArrayList<Map<String,Object>>();try(var r=c.createStatement().executeQuery("SELECT id FROM reward_instruments ORDER BY provider,nickname,id")){while(r.next())result.add(view(c,(UUID)r.getObject(1)));}return result;
    }
    /** Summaries of observed instrument facts; provider transfers are never added again. */
    public static Map<String,Object> cardSummary(List<Map<String,Object>> instruments){
        BigDecimal openings=BigDecimal.ZERO,remaining=BigDecimal.ZERO,spent=BigDecimal.ZERO;int active=0,unknown=0;
        for(var card:instruments)if("GIFT_CARD".equals(card.get("kind"))&&!"ARCHIVED".equals(card.get("status"))){active++;if(card.get("opening") instanceof BigDecimal value)openings=openings.add(value);if(card.get("calculated") instanceof BigDecimal value)remaining=remaining.add(value);else unknown++;
            if(card.get("events") instanceof List<?> events)for(var raw:events)if(raw instanceof Map<?,?> event&&Boolean.TRUE.equals(event.get("active"))&&"REDEMPTION".equals(event.get("kind"))&&event.get("amount") instanceof BigDecimal value)spent=spent.add(value);
        }
        return Map.of("recordedOpenings",openings,"knownRemaining",remaining,"recordedRedemptions",spent,"unknownBalanceCount",unknown,"activeCards",active);
    }
    /** Read-only card facts missing from the provider ledger, evaluated by the existing balance engine. */
    public static List<coupons.rewards.ProviderBalanceSummary.Movement> accountMovements(List<Map<String,Object>> cards,List<coupons.rewards.ProviderBalanceSummary.Movement> ledger){
        var rows=new ArrayList<coupons.rewards.ProviderBalanceSummary.Movement>();
        for(var card:cards)if("GIFT_CARD".equals(card.get("kind"))&&!"ARCHIVED".equals(card.get("status"))){
            String label=Objects.toString(card.get("provider"),"Gift card");var brand=java.util.regex.Pattern.compile("(?i)Brand:\\s*([^|]+)").matcher(label);if(brand.find())label=brand.group(1).trim();label=label.replaceAll("(?i)\\s*Number:.*$","").trim();if(card.get("lastFour")!=null)label+=" · ending "+card.get("lastFour");
            UUID id=(UUID)card.get("id");var day=(LocalDate)card.get("openingDate");String reference=Objects.toString(card.get("evidenceReference"),"");boolean represented=false;
            if(reference.startsWith("PAYOUT:")){String source=reference.substring(7);represented=ledger.stream().anyMatch(m->m.provider().equals("Gift Cards")&&m.source().equals("Redemption "+source+" | received"));}
            if(!represented&&card.get("opening") instanceof BigDecimal opening&&opening.signum()!=0)rows.add(new coupons.rewards.ProviderBalanceSummary.Movement(day,"Gift Cards",null,opening,"Card "+id+" | recorded opening accrual · "+label,null,null,id));
            if(card.get("calculated")==null)rows.add(new coupons.rewards.ProviderBalanceSummary.Movement(day,"Gift Cards",null,null,"Card "+id+" | incomplete card history · "+label,null,null,id));
            if(card.get("events") instanceof List<?> events)for(var raw:events)if(raw instanceof Map<?,?> event&&Boolean.TRUE.equals(event.get("active"))&&!"REPORTED_BALANCE".equals(event.get("kind"))){
                Object receipt=event.get("sourceReceiptId"),source=event.get("sourceEventId");if(receipt!=null&&source!=null&&ledger.stream().anyMatch(m->receipt.equals(m.receiptId())&&source.equals(m.eventId())))continue;
                var amount=(BigDecimal)event.get("amount");rows.add(new coupons.rewards.ProviderBalanceSummary.Movement((LocalDate)event.get("date"),"Gift Cards",null,signedAmount(Objects.toString(event.get("kind")),amount),"Card "+id+" | recorded "+event.get("kind")+" · "+label,null,null,(UUID)event.get("id")));
            }
        }
        return List.copyOf(rows);
    }
    private static BigDecimal signedAmount(String kind,BigDecimal amount){return amount==null?null:Set.of("REDEMPTION","WITHDRAWAL","REVERSAL","EXCHANGE").contains(kind)?amount.negate():amount;}
    public Map<String,Object> get(UUID id){return PostgresRepositorySupport.transaction(db,c->{c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);return view(c,id);});}
    public Map<String,Object> create(Map<String,Object> f,String actor){return PostgresRepositorySupport.transaction(db,c->{PostgresReceiptState.lock(c);UUID id=uuid(f,"id",true);insert(c,id,f,actor);return view(c,id);});}
    public record IssuedCard(String retailer,String lastFour,String recordedNumber) { }
    public static IssuedCard issuedCard(ProviderRedemption payout){
        var brand=java.util.regex.Pattern.compile("(?i)(?:^|\\|)\\s*Brand:\\s*([^|]+)").matcher(Objects.toString(payout.notes(),""));
        var number=java.util.regex.Pattern.compile("(?i)(?:^|\\|)\\s*Number:\\s*([^|]+)").matcher(Objects.toString(payout.notes(),""));
        String retailer=brand.find()?brand.group(1).trim():"Gift card from "+payout.provider(),recorded=number.find()?number.group(1).replaceAll("[\\s-]",""):null;
        String digits=recorded==null?"":recorded.replaceAll("\\D","");return new IssuedCard(retailer,digits.length()>=4?digits.substring(digits.length()-4):null,recorded);
    }
    /** Called only on explicit received-payout writes/reconciliation, never on read or planner preview. */
    static Map<String,Object> ensureReceivedCard(Connection c,ProviderRedemption payout,String actor)throws SQLException{
        if(payout.status()!=ProviderRedemption.Status.RECEIVED||payout.destination()!=ProviderRedemption.Destination.GIFT_CARD)return null;
        String reference="PAYOUT:"+payout.redemptionId();
        try(var p=c.prepareStatement("SELECT id FROM reward_instruments WHERE evidence_reference=?")){p.setString(1,reference);try(var r=p.executeQuery()){if(r.next())return view(c,r.getObject(1,UUID.class));}}
        var details=issuedCard(payout);
        // Legacy records may contain the same complete identifier in the payout and card description.
        // Last-four digits or equal amounts alone never establish identity.
        if(details.recordedNumber()!=null&&details.recordedNumber().matches("[0-9]{8,}")){
            var exact=new ArrayList<Map<String,Object>>();for(var card:list(c))if("GIFT_CARD".equals(card.get("kind"))){
                var raw=Objects.toString(card.get("provider"),"");var matcher=java.util.regex.Pattern.compile("(?i)(?:^|\\|)\\s*Number:\\s*([^|]+)").matcher(raw);var brand=java.util.regex.Pattern.compile("(?i)(?:^|\\|)\\s*Brand:\\s*([^|]+)").matcher(raw);
                if(matcher.find()&&brand.find()&&matcher.group(1).replaceAll("[\\s-]","").equals(details.recordedNumber())&&brand.group(1).trim().equalsIgnoreCase(details.retailer()))exact.add(card);
            }
            if(exact.size()>1)throw new IllegalArgumentException("Multiple cards carry the same recorded payout identity. Review those cards before reconciling.");
            if(exact.size()==1){var card=exact.getFirst();if(card.get("evidenceReference")!=null&&card.get("evidenceReference").toString().startsWith("PAYOUT:"))throw new IllegalArgumentException("This card is already linked to a different payout.");
                try(var p=c.prepareStatement("UPDATE reward_instruments SET evidence_reference=?,revision=revision+1,reason=?,recorded_by=?,recorded_at=? WHERE id=?")){p.setString(1,reference);p.setString(2,"Linked to received payout using the same complete recorded card identifier");p.setString(3,actor);p.setTimestamp(4,Timestamp.from(Instant.now()));p.setObject(5,card.get("id"));p.executeUpdate();}history(c,(UUID)card.get("id"));return view(c,(UUID)card.get("id"));
            }
        }
        UUID id=UUID.nameUUIDFromBytes(("received-gift-card:"+payout.redemptionId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var fields=new LinkedHashMap<String,Object>();fields.put("kind","GIFT_CARD");fields.put("provider",details.retailer());fields.put("lastFour",details.lastFour());fields.put("unit","USD");fields.put("opening",payout.destinationUsdValue());fields.put("date",payout.completedDate().toString());fields.put("historyComplete",false);fields.put("evidenceReference",reference);fields.put("reason","Issued by recorded received payout from "+payout.provider()+"; subsequent spending history not yet verified");insert(c,id,fields,actor);return view(c,id);
    }
    public Map<String,Object> reconcileReceivedPayouts(String actor){return PostgresRepositorySupport.transaction(db,c->{PostgresReceiptState.lock(c);var payouts=new ArrayList<ProviderRedemption>();try(var p=c.prepareStatement("SELECT payload FROM provider_redemptions_current ORDER BY redemption_id");var r=p.executeQuery()){while(r.next())payouts.add(PostgresRepositorySupport.deserialize(r.getBytes(1),ProviderRedemption.class));}int count=0;for(var payout:payouts)if(ensureReceivedCard(c,payout,actor)!=null)count++;return Map.of("receivedGiftCards",count);});}
    /** Associate an observed issuance with its existing payout; no provider earning is inserted. */
    public Map<String,Object> linkPayout(UUID payoutId,long expected,Map<String,Object> f,String actor){return PostgresRepositorySupport.transaction(db,c->{
        PostgresReceiptState.lock(c);confirm(f);
        ProviderRedemption payout;try(var p=c.prepareStatement("SELECT revision,payload FROM provider_redemptions_current WHERE redemption_id=? FOR UPDATE")){p.setObject(1,payoutId);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();if(r.getLong(1)!=expected)throw new ConcurrentModificationException("Payout changed; reopen it.");payout=PostgresRepositorySupport.deserialize(r.getBytes(2),ProviderRedemption.class);}}
        if(payout.status()!=ProviderRedemption.Status.RECEIVED||payout.destination()!=ProviderRedemption.Destination.GIFT_CARD)throw new IllegalArgumentException("Choose a received gift-card payout.");
        String reference="PAYOUT:"+payoutId;
        try(var p=c.prepareStatement("SELECT id FROM reward_instruments WHERE evidence_reference=?")){p.setString(1,reference);try(var r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("This payout is already linked to an individual card.");}}
        UUID existing=uuid(f,"existingInstrumentId",false);
        if(existing!=null){var old=view(c,existing);check(old,Long.parseLong(text(f,"instrumentRevision",true)));if(!"GIFT_CARD".equals(old.get("kind"))||!"ACTIVE".equals(old.get("status")))throw new IllegalArgumentException("Choose an active gift card.");if(old.get("evidenceReference")!=null&&old.get("evidenceReference").toString().startsWith("PAYOUT:"))throw new IllegalArgumentException("This card is already linked to a payout.");
            try(var p=c.prepareStatement("UPDATE reward_instruments SET evidence_reference=?,revision=revision+1,reason=?,recorded_by=?,recorded_at=? WHERE id=?")){p.setString(1,reference);p.setString(2,text(f,"reason",true));p.setString(3,actor);p.setTimestamp(4,Timestamp.from(Instant.now()));p.setObject(5,existing);p.executeUpdate();}history(c,existing);return view(c,existing);
        }
        var fields=new LinkedHashMap<String,Object>(f);fields.put("kind","GIFT_CARD");fields.put("unit","USD");fields.put("opening",payout.destinationUsdValue());fields.put("date",payout.completedDate().toString());fields.put("historyComplete",false);fields.put("evidenceReference",reference);
        UUID id=uuid(f,"id",true);insert(c,id,fields,actor);return view(c,id);
    });}
    public Map<String,Object> correct(UUID id,long expected,Map<String,Object> f,String actor){return PostgresRepositorySupport.transaction(db,c->{
        PostgresReceiptState.lock(c);var old=view(c,id);check(old,expected);confirm(f);
        String kind=f.containsKey("kind")?text(f,"kind",true):old.get("kind").toString();
        String status=f.containsKey("status")?text(f,"status",true):old.get("status").toString();
        if(!Set.of("ACCOUNT_REWARD","GIFT_CARD").contains(kind)||!Set.of("ACTIVE","ARCHIVED").contains(status))throw new IllegalArgumentException("Choose a supported card type and status.");
        if(!kind.equals(old.get("kind"))&&(!((List<?>)old.get("events")).isEmpty()||hasExchange(old)))throw new IllegalArgumentException("Instrument type with financial history must be preserved.");
        if(kind.equals("GIFT_CARD")&&!old.get("unit").equals("USD"))throw new IllegalArgumentException("Gift cards require USD values.");
        String nickname=f.containsKey("nickname")?text(f,"nickname",false):(String)old.get("nickname"),last=f.containsKey("lastFour")?text(f,"lastFour",false):(String)old.get("lastFour");validateLast(kind,last);
        String provider=f.containsKey("provider")?text(f,"provider",true):old.get("provider").toString();
        boolean openingChanged=f.containsKey("opening")||f.containsKey("date")||f.containsKey("historyComplete");
        if(openingChanged&&hasExchange(old))throw new IllegalArgumentException("Opening correction with linked exchanges requires dependency review");
        BigDecimal opening=f.containsKey("opening")?decimal(f,"opening",false):(BigDecimal)old.get("opening");
        LocalDate day=f.containsKey("date")?date(f,"date"):(LocalDate)old.get("openingDate");
        boolean complete=f.containsKey("historyComplete")?Boolean.TRUE.equals(f.get("historyComplete")):Boolean.TRUE.equals(old.get("historyComplete"));
        try(var p=c.prepareStatement("UPDATE reward_instruments SET provider=?,nickname=?,last_four=?,opening=?,opening_date=?,history_complete=?,revision=revision+1,reason=?,recorded_by=?,recorded_at=?,kind=?,status=? WHERE id=?")){
            p.setString(1,provider);p.setString(2,nickname);p.setString(3,last);p.setBigDecimal(4,opening);p.setObject(5,day);p.setBoolean(6,complete);p.setString(7,text(f,"reason",true));p.setString(8,actor);p.setTimestamp(9,Timestamp.from(Instant.now()));p.setString(10,kind);p.setString(11,status);p.setObject(12,id);p.executeUpdate();}
        history(c,id);return view(c,id);
    });}
    public Map<String,Object> event(UUID id,long expected,Map<String,Object> f,String actor){return PostgresRepositorySupport.transaction(db,c->{
        PostgresReceiptState.lock(c);var old=view(c,id);check(old,expected);if(!"ACTIVE".equals(old.get("status")))throw new IllegalArgumentException("Reactivate this instrument before recording new activity.");String kind=text(f,"kind",true);
        if(!Set.of("CREDIT","REDEMPTION","WITHDRAWAL","ADJUSTMENT","REFUND","REVERSAL","RESTORATION","REPORTED_BALANCE").contains(kind))throw new IllegalArgumentException("Unknown event kind");
        if(kind.equals("WITHDRAWAL")&&!old.get("kind").equals("ACCOUNT_REWARD"))throw new IllegalArgumentException("Withdrawals belong to accounts");
        BigDecimal amount=decimal(f,"amount",true);if(!Set.of("ADJUSTMENT","REPORTED_BALANCE").contains(kind)&&amount.signum()<=0)throw new IllegalArgumentException("Amount must be positive");
        UUID supersedes=uuid(f,"supersedes",false),sourceReceipt=uuid(f,"sourceReceiptId",false),sourceEvent=uuid(f,"sourceEventId",false);
        if(supersedes!=null){confirm(f);if(hasExchange(old))throw new IllegalArgumentException("Correction with linked exchanges requires dependency review");try(var p=c.prepareStatement("SELECT instrument_id,exchange_id,kind,source_receipt,source_event FROM reward_instrument_events WHERE id=? AND NOT EXISTS (SELECT 1 FROM reward_instrument_events n WHERE n.supersedes=?)")){
            p.setObject(1,supersedes);p.setObject(2,supersedes);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose a current event");UUID original=(UUID)r.getObject(1);if(!id.equals(original)){var originalAccount=view(c,original);if(hasExchange(originalAccount))throw new IllegalArgumentException("Reassociation with linked exchanges requires dependency review");Object expectedSource=f.get("sourceRevision");if(expectedSource==null)throw new IllegalArgumentException("Source revision required for reassociation");check(originalAccount,Long.parseLong(expectedSource.toString()));if(!old.get("unit").equals(originalAccount.get("unit"))||!old.get("kind").equals(originalAccount.get("kind")))throw new IllegalArgumentException("Reassociation must preserve instrument type and units");bump(c,original);}if(r.getObject(2)!=null)throw new IllegalArgumentException("Linked exchange correction requires dependency review");if(!r.getString(3).equals(kind))throw new IllegalArgumentException("Correction must preserve event type");if(sourceReceipt==null&&sourceEvent==null){sourceReceipt=(UUID)r.getObject(4);sourceEvent=(UUID)r.getObject(5);}}}
        }
        Long sourceRevision=validateReceiptSource(c,old,sourceReceipt,sourceEvent,kind,amount,supersedes);
        addEvent(c,id,uuid(f,"id",true),kind,amount,date(f,"date"),text(f,"reason",true),actor,supersedes,null,null,sourceReceipt,sourceEvent,sourceRevision);bump(c,id);return view(c,id);
    });}
    public Map<String,Object> exchange(UUID source,long expected,Map<String,Object> f,String actor){return PostgresRepositorySupport.transaction(db,c->{
        PostgresReceiptState.lock(c);var account=view(c,source);check(account,expected);if(!account.get("kind").equals("ACCOUNT_REWARD"))throw new IllegalArgumentException("Select a reward account");
        UUID card=uuid(f,"cardId",true),exchange=uuid(f,"id",true);BigDecimal value=decimal(f,"cardValue",true),amount=decimal(f,"sourceAmount",false);if(value.signum()<=0||amount!=null&&amount.signum()<=0)throw new IllegalArgumentException("Positive observed values required");
        var cardFields=new LinkedHashMap<String,Object>();cardFields.put("kind","GIFT_CARD");cardFields.put("provider",text(f,"provider",true));cardFields.put("nickname",text(f,"nickname",false));cardFields.put("lastFour",text(f,"lastFour",false));cardFields.put("unit","USD");cardFields.put("opening",value);cardFields.put("date",text(f,"date",false));cardFields.put("historyComplete",true);cardFields.put("reason",text(f,"reason",true));cardFields.put("evidenceReference",text(f,"evidenceReference",false));
        insert(c,card,cardFields,actor);
        addEvent(c,source,exchange,"EXCHANGE",amount,date(f,"date"),text(f,"reason",true),actor,null,card,exchange,null,null,null);bump(c,source);return view(c,card);
    });}
    /** Receipt assignments and their append-only redemption history commit together. */
    static void syncReceipt(Connection c, ReceiptRecord before, ReceiptRecord after, String actor)throws SQLException {
        record Linked(UUID event, UUID card, BigDecimal amount) {}
        var linked=new LinkedHashMap<UUID,Linked>();
        try(var p=c.prepareStatement("SELECT e.id,e.instrument_id,e.source_event,e.amount FROM reward_instrument_events e JOIN reward_instruments i ON i.id=e.instrument_id WHERE e.source_receipt=? AND e.kind='REDEMPTION' AND i.kind='GIFT_CARD' AND NOT EXISTS(SELECT 1 FROM reward_instrument_events n WHERE n.supersedes=e.id)")){
            p.setObject(1,after.transactionId());try(var r=p.executeQuery()){while(r.next())linked.put((UUID)r.getObject(3),new Linked((UUID)r.getObject(1),(UUID)r.getObject(2),r.getBigDecimal(4)));}
        }
        var payments=new LinkedHashMap<UUID,coupons.receipts.Payment>();
        for(var payment:after.transaction().getPayments())if(payment.getPaymentType()==coupons.receipts.PaymentType.GIFT_CARD)payments.put(payment.getPaymentId(),payment);
        var ids=new LinkedHashSet<>(linked.keySet());payments.values().stream().filter(p->p.getGiftCardId()!=null).forEach(p->ids.add(p.getPaymentId()));
        for(var paymentId:ids){
            var old=linked.get(paymentId);var payment=payments.get(paymentId);
            UUID card=payment==null?null:payment.getGiftCardId();
            boolean previouslyAssigned=before!=null&&before.transaction().getPayments().stream().anyMatch(p->p.getPaymentId().equals(paymentId)&&p.getGiftCardId()!=null);
            // Retain explicit legacy receipt associations until reassigned or removed.
            if(card==null&&payment!=null&&old!=null&&!previouslyAssigned&&(old.amount().signum()!=0||before!=null&&before.status()==ReceiptStatus.DELETED))card=old.card();
            boolean used=after.status()==ReceiptStatus.ACTIVE&&payment!=null&&card!=null;
            if(card==null&&old==null)continue;
            UUID target=card==null?old.card():card;
            try(var p=c.prepareStatement("SELECT kind,unit,status FROM reward_instruments WHERE id=?")){p.setObject(1,target);try(var r=p.executeQuery()){if(!r.next()||!r.getString(1).equals("GIFT_CARD")||!r.getString(2).equals("USD"))throw new IllegalArgumentException("Choose an existing USD gift card.");if(used&&r.getString(3).equals("ARCHIVED")&&(old==null||!old.card().equals(target)||payment.getAmount().compareTo(old.amount())>0))throw new IllegalArgumentException("Archived cards cannot fund new purchases.");}}
            addEvent(c,target,UUID.randomUUID(),"REDEMPTION",used?payment.getAmount():BigDecimal.ZERO,after.transaction().getObservations().purchaseDate(),used?"Gift card used on receipt "+(after.metadata()==null?after.transactionId():after.metadata().receiptNumber()):"Gift-card use released by receipt update",actor,old==null?null:old.event(),null,null,after.transactionId(),paymentId,after.revision());
            bump(c,target);if(old!=null&&!old.card().equals(target))bump(c,old.card());
        }
    }

    private static void insert(Connection c,UUID id,Map<String,Object> f,String actor)throws SQLException{
        String kind=text(f,"kind",true),unit=text(f,"unit",true),last=text(f,"lastFour",false);if(!Set.of("ACCOUNT_REWARD","GIFT_CARD").contains(kind)||!Set.of("USD","POINTS").contains(unit)||kind.equals("GIFT_CARD")&&!unit.equals("USD"))throw new IllegalArgumentException("Select instrument and native unit");validateLast(kind,last);
        UUID replacement=uuid(f,"replacementId",false);if(replacement!=null){if(!kind.equals("GIFT_CARD")||text(f,"evidenceReference",true).isBlank())throw new IllegalArgumentException("Replacement evidence required");var old=view(c,replacement);if(!old.get("kind").equals("GIFT_CARD"))throw new IllegalArgumentException("Replacement must reference a card");}
        try(var p=c.prepareStatement("INSERT INTO reward_instruments(id,kind,provider,nickname,last_four,unit,opening,opening_date,history_complete,revision,replacement_id,evidence_reference,reason,recorded_by,recorded_at) VALUES (?,?,?,?,?,?,?,?,?,1,?,?,?,?,?)")){
            p.setObject(1,id);p.setString(2,kind);p.setString(3,text(f,"provider",true));p.setString(4,text(f,"nickname",false));p.setString(5,last);p.setString(6,unit);p.setBigDecimal(7,decimal(f,"opening",false));p.setObject(8,date(f,"date"));p.setBoolean(9,Boolean.TRUE.equals(f.get("historyComplete")));p.setObject(10,replacement);p.setString(11,text(f,"evidenceReference",false));p.setString(12,text(f,"reason",true));p.setString(13,actor);p.setTimestamp(14,Timestamp.from(Instant.now()));p.executeUpdate();}
        history(c,id);
    }
    private static void addEvent(Connection c,UUID instrument,UUID id,String kind,BigDecimal amount,LocalDate date,String reason,String actor,UUID supersedes,UUID related,UUID exchange,UUID sourceReceipt,UUID sourceEvent,Long sourceRevision)throws SQLException{
        try(var p=c.prepareStatement("INSERT INTO reward_instrument_events VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            p.setObject(1,id);p.setObject(2,instrument);p.setString(3,kind);p.setBigDecimal(4,amount);p.setObject(5,date);p.setString(6,reason);p.setString(7,actor);p.setTimestamp(8,Timestamp.from(Instant.now()));p.setObject(9,supersedes);p.setObject(10,related);p.setObject(11,exchange);p.setObject(12,sourceReceipt);p.setObject(13,sourceEvent);p.setObject(14,sourceRevision);p.executeUpdate();}
    }
    private static void bump(Connection c,UUID id)throws SQLException{try(var p=c.prepareStatement("UPDATE reward_instruments SET revision=revision+1 WHERE id=?")){p.setObject(1,id);p.executeUpdate();}history(c,id);}
    private static void history(Connection c,UUID id)throws SQLException{try(var p=c.prepareStatement("INSERT INTO reward_instrument_history SELECT * FROM reward_instruments WHERE id=?")){p.setObject(1,id);p.executeUpdate();}}
    private static Map<String,Object> view(Connection c,UUID id)throws SQLException{
        var result=new LinkedHashMap<String,Object>();LocalDate openingDate;boolean complete;BigDecimal opening;
        try(var p=c.prepareStatement("SELECT * FROM reward_instruments WHERE id=?")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new NoSuchElementException();
            for(String key:List.of("id","kind","provider","nickname","unit","revision","reason","status"))result.put(key,r.getObject(key));result.put("lastFour",r.getString("last_four"));opening=r.getBigDecimal("opening");openingDate=r.getObject("opening_date",LocalDate.class);complete=r.getBoolean("history_complete");result.put("opening",opening);result.put("openingDate",openingDate);result.put("historyComplete",complete);result.put("replacementId",r.getObject("replacement_id"));result.put("evidenceReference",r.getString("evidence_reference"));}}
        var events=new ArrayList<Map<String,Object>>();var replaced=new HashSet<UUID>();
        try(var p=c.prepareStatement("SELECT * FROM reward_instrument_events WHERE instrument_id=? ORDER BY recorded_at,id")){p.setObject(1,id);try(var r=p.executeQuery()){while(r.next()){
            var event=new LinkedHashMap<String,Object>();for(String key:List.of("id","kind","amount","reason"))event.put(key,r.getObject(key));event.put("date",r.getObject("event_date",LocalDate.class));event.put("supersedes",r.getObject("supersedes"));event.put("relatedInstrument",r.getObject("related_instrument"));event.put("exchangeId",r.getObject("exchange_id"));event.put("sourceReceiptId",r.getObject("source_receipt"));event.put("sourceEventId",r.getObject("source_event"));event.put("sourceRevision",r.getObject("source_revision"));event.put("recordedBy",r.getString("recorded_by"));event.put("recordedAt",r.getTimestamp("recorded_at").toInstant());events.add(event);if(r.getObject("supersedes")!=null)replaced.add((UUID)r.getObject("supersedes"));}}}
        try(var p=c.prepareStatement("SELECT supersedes FROM reward_instrument_events WHERE supersedes IN (SELECT id FROM reward_instrument_events WHERE instrument_id=?)")){p.setObject(1,id);try(var r=p.executeQuery()){while(r.next())replaced.add((UUID)r.getObject(1));}}
        var issuance=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT id,instrument_id,amount,event_date FROM reward_instrument_events WHERE related_instrument=? AND exchange_id IS NOT NULL")){p.setObject(1,id);try(var r=p.executeQuery()){while(r.next()){var link=new LinkedHashMap<String,Object>();link.put("eventId",r.getObject(1));link.put("accountId",r.getObject(2));link.put("sourceAmount",r.getBigDecimal(3));link.put("date",r.getObject(4));issuance.add(link);}}}result.put("issuedFrom",issuance);
        BigDecimal calculated=complete?opening:null,reported=null,earned=BigDecimal.ZERO;LocalDate reportedDate=null,latest=openingDate;boolean datesKnown=openingDate!=null;
        for(var event:events){boolean active=!replaced.contains((UUID)event.get("id"));event.put("active",active);if(!active)continue;
            boolean sourceChanged=false;
            if(event.get("sourceReceiptId")!=null){try(var p=c.prepareStatement("SELECT revision,status FROM receipt_current WHERE transaction_id=?")){p.setObject(1,event.get("sourceReceiptId"));try(var r=p.executeQuery()){sourceChanged=!r.next()||r.getLong(1)!=((Number)event.get("sourceRevision")).longValue()||!r.getString(2).equals("ACTIVE");}}}
            if(event.get("kind").equals("REDEMPTION")&&((BigDecimal)event.get("amount")).signum()==0)sourceChanged=false;
            event.put("sourceChanged",sourceChanged);if(sourceChanged)calculated=null;
            String kind=event.get("kind").toString();BigDecimal amount=(BigDecimal)event.get("amount");LocalDate day=(LocalDate)event.get("date");
            if(kind.equals("REPORTED_BALANCE")){reported=amount;reportedDate=day;continue;}
            if(day==null)datesKnown=false;else if(latest==null||day.isAfter(latest))latest=day;
            if(kind.equals("CREDIT")&&event.get("sourceReceiptId")==null)earned=earned.add(amount);
            if(amount==null)calculated=null;else if(calculated!=null)calculated=calculated.add(signedAmount(kind,amount));
        }
        result.put("calculated",calculated);result.put("reported",reported);result.put("reportedDate",reportedDate);result.put("balanceDate",latest);result.put("comparisonStatus",datesKnown&&reportedDate!=null&&reportedDate.equals(latest)?"SAME_RECORDED_DATE":"DATE_ALIGNMENT_UNRESOLVED");
        result.put("difference",calculated!=null&&reported!=null&&datesKnown&&reportedDate!=null&&reportedDate.equals(latest)?calculated.subtract(reported):null);
        result.put("hasFinancialHistory",!events.isEmpty()||!issuance.isEmpty());result.put("recordedAt",historyDate(c,id));result.put("newEarnings",earned);result.put("events",events);
        var history=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement("SELECT revision,nickname,last_four,reason,recorded_by,recorded_at,provider,opening,opening_date,history_complete FROM reward_instrument_history WHERE id=? ORDER BY revision")){p.setObject(1,id);try(var r=p.executeQuery()){while(r.next()){var row=new LinkedHashMap<String,Object>();row.put("revision",r.getLong(1));row.put("nickname",r.getString(2));row.put("lastFour",r.getString(3));row.put("reason",r.getString(4));row.put("recordedBy",r.getString(5));row.put("recordedAt",r.getTimestamp(6).toInstant());row.put("provider",r.getString(7));row.put("opening",r.getBigDecimal(8));row.put("openingDate",r.getObject(9));row.put("historyComplete",r.getBoolean(10));history.add(row);}}}result.put("history",history);return result;
    }
    private static Long validateReceiptSource(Connection c,Map<String,Object> instrument,UUID receipt,UUID source,String kind,BigDecimal amount,UUID supersedes)throws SQLException{
        if(receipt==null&&source==null)return null;
        if(receipt==null||source==null||!Set.of("CREDIT","REDEMPTION").contains(kind))throw new IllegalArgumentException("Select a receipt credit or redemption");
        try(var p=c.prepareStatement("SELECT id FROM reward_instrument_events e WHERE source_receipt=? AND source_event=? AND NOT EXISTS (SELECT 1 FROM reward_instrument_events n WHERE n.supersedes=e.id)")){
            p.setObject(1,receipt);p.setObject(2,source);try(var r=p.executeQuery()){while(r.next())if(!Objects.equals(supersedes,r.getObject(1)))throw new ConcurrentModificationException("Receipt activity already associated");}}
        var record=PostgresReceiptState.read(c).receipts().get(receipt);
        if(record==null||record.status()!=ReceiptStatus.ACTIVE)throw new IllegalArgumentException("Select an active recorded receipt");
        var tx=record.contents().toTransaction();BigDecimal recorded=null;
        if(kind.equals("CREDIT")){
            var earning=tx.getObservations().earnings().stream().filter(e->e.earningId().equals(source)).findFirst().orElseThrow(()->new IllegalArgumentException("Receipt earning missing"));
            if(earning.confirmed()==null||!Set.of(coupons.promotions.RebateStatus.CREDITED,coupons.promotions.RebateStatus.PAID).contains(earning.status()))throw new IllegalArgumentException("Earning not confirmed");
            var benefit=earning.confirmed();if(benefit.kind()!=coupons.promotions.BenefitKind.POINTS&&!java.util.Currency.getInstance("USD").equals(benefit.currency()))throw new IllegalArgumentException("Preserve native currency; non-USD association is unsupported");if(instrument.get("kind").equals("ACCOUNT_REWARD")&&!coupons.rewards.ProviderBalanceSummary.accountName(earning.provider()).equals(coupons.rewards.ProviderBalanceSummary.accountName(instrument.get("provider").toString())))throw new IllegalArgumentException("Receipt reward provider differs from account");boolean card=benefit.kind()==coupons.promotions.BenefitKind.GIFT_CARD;
            if(card!=instrument.get("kind").equals("GIFT_CARD")||benefit.kind()==coupons.promotions.BenefitKind.STORE_COUPON)throw new IllegalArgumentException("Receipt benefit instrument differs");
            if(!instrument.get("unit").equals(benefit.kind()==coupons.promotions.BenefitKind.POINTS?"POINTS":"USD"))throw new IllegalArgumentException("Native units differ");
            recorded=benefit.amount();
        }else{
            var payment=tx.getPayments().stream().filter(e->e.getPaymentId().equals(source)).findFirst().orElseThrow(()->new IllegalArgumentException("Receipt payment missing"));
            if(!instrument.get("unit").equals("USD")||instrument.get("kind").equals("GIFT_CARD")&&payment.getPaymentType()!=coupons.receipts.PaymentType.GIFT_CARD||instrument.get("kind").equals("ACCOUNT_REWARD")&&payment.getPaymentType()!=coupons.receipts.PaymentType.WALGREENS_CASH)throw new IllegalArgumentException("Use an explicitly classified matching redemption; unclassified roles require review");
            if(instrument.get("kind").equals("ACCOUNT_REWARD")&&!coupons.rewards.ProviderBalanceSummary.accountName(instrument.get("provider").toString()).equals("Walgreens Cash"))throw new IllegalArgumentException("Recorded tender belongs to Walgreens Cash");recorded=payment.getAmount();
        }
        if(recorded==null||recorded.compareTo(amount)!=0)throw new IllegalArgumentException("Association must preserve the recorded receipt amount");
        return record.revision();
    }
    private static Instant historyDate(Connection c,UUID id)throws SQLException{try(var p=c.prepareStatement("SELECT recorded_at FROM reward_instruments WHERE id=?")){p.setObject(1,id);try(var r=p.executeQuery()){r.next();return r.getTimestamp(1).toInstant();}}}
    private static boolean hasExchange(Map<String,Object> instrument){return ((List<?>)instrument.get("events")).stream().anyMatch(v->((Map<?,?>)v).get("exchangeId")!=null)||!((List<?>)instrument.get("issuedFrom")).isEmpty();}
    private static void check(Map<String,Object> row,long expected){if(((Number)row.get("revision")).longValue()!=expected)throw new ConcurrentModificationException();}
    private static void validateLast(String kind,String last){if(last!=null&&(!kind.equals("GIFT_CARD")||!last.matches("[0-9]{4}")))throw new IllegalArgumentException("Only individual cards have optional last four digits");}
    private static void confirm(Map<String,Object> f){if(!Boolean.TRUE.equals(f.get("confirmed")))throw new IllegalArgumentException("Explicit confirmation required");}
    public static String text(Map<String,Object> f,String key,boolean required){Object raw=f.get(key);if(raw!=null&&!(raw instanceof String))throw new IllegalArgumentException("Expected text");String s=raw==null?null:raw.toString().trim();if(s!=null&&s.isEmpty())s=null;if(required&&s==null)throw new IllegalArgumentException("Missing "+key);if(s!=null&&s.length()>1000)throw new IllegalArgumentException("Text too long");return s;}
    private static UUID uuid(Map<String,Object> f,String key,boolean required){String s=text(f,key,required);return s==null?null:UUID.fromString(s);}
    private static LocalDate date(Map<String,Object> f,String key){String s=text(f,key,false);return s==null?null:LocalDate.parse(s);}
    private static BigDecimal decimal(Map<String,Object> f,String key,boolean required){Object v=f.get(key);if(v==null||v.toString().isBlank()){if(required)throw new IllegalArgumentException("Missing amount");return null;}if(!(v instanceof String||v instanceof BigDecimal))throw new IllegalArgumentException("Decimal required");String s=v.toString();if(s.length()>100)throw new IllegalArgumentException("Amount too long");var n=new BigDecimal(s);if(n.precision()>40||Math.abs(n.scale())>20)throw new IllegalArgumentException("Amount precision exceeded");return n;}
}
