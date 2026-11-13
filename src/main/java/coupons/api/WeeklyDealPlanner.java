package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Bounded read-only search. Only already-recorded funds are spendable. */
final class WeeklyDealPlanner {
    record Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario,String group,List<String> includes,String validationIssue) {
        Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario,String group,List<String> includes){this(id,name,store,scenario,group,includes,null);}
        Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario,String group,String validationIssue){this(id,name,store,scenario,group,List.of(id),validationIssue);}
        Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario,String group){this(id,name,store,scenario,group,List.of(id),null);}
        Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario){this(id,name,store,scenario,id);}
        Candidate {group=group==null?id:group;}
    }
    record RequiredItem(String candidateId,UUID lineId,int quantity,String name){RequiredItem(String candidateId,UUID lineId,int quantity){this(candidateId,lineId,quantity,null);}RequiredItem{if(quantity<1)throw new IllegalArgumentException("Must Buy quantity must be positive.");}}
    record Reward(String id,String description,String type,String store,BigDecimal amount,LocalDate expires,
            String registerRewardType,String originProduct,String originBrand,String originProgram,String originPromotion,coupons.rewards.PaymentEligibility.Rule eligibility) {
        Reward(String id,String description,String type,String store,BigDecimal amount,LocalDate expires,String rrType,String product,String brand,String program,String promotion){this(id,description,type,store,amount,expires,rrType,product,brand,program,promotion,null);}
        Reward(String id,String description,String type,String store,BigDecimal amount,LocalDate expires){this(id,description,type,store,amount,expires,null,null,null,null,null,type.equals("GIFT_CARD")&&Set.of("WALGREENS","WALMART","CVS").contains(retailer(store))?new coupons.rewards.PaymentEligibility.Rule(null,List.of(retailer(store)),List.of(),null,expires,null,true,amount):null);}
        private UUID sourceId(){try{return UUID.fromString(id);}catch(IllegalArgumentException ignored){return null;}}
        ShoppingPlan.ScenarioPayment payment(){return new ShoppingPlan.ScenarioPayment(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),type,amount,description+" · source "+id+" · expires "+(expires==null?"unknown":expires),registerRewardType,sourceId(),originProduct,originBrand,originProgram,originPromotion,eligibility);}
    }
    record Step(String id,String name,String store,ShoppingPlan.Scenario scenario,Map<String,Object> estimate,List<Reward> fundsRemaining,BigDecimal walgreensCashRemaining,List<String> candidateIds) {}
    record State(List<Step> steps,Set<String> plans,Set<String> used,BigDecimal wc,BigDecimal oop,BigDecimal earnings,Map<String,Reward> funds,BigDecimal wcSpent,Set<UUID> coupons) {}
    static Map<String,Object> plan(List<Candidate> candidates,List<Reward> available,BigDecimal wc,BigDecimal budget,String mode,LocalDate day) {return plan(candidates,available,wc,budget,mode,day,List.of(),true);}
    static Map<String,Object> plan(List<Candidate> candidates,List<Reward> available,BigDecimal wc,BigDecimal budget,String mode,LocalDate day,List<RequiredItem> required,boolean includeOptional) {return plan(candidates,available,wc,budget,mode,day,required,includeOptional,new Options(false,false,false,null));}
    record Options(boolean rolling,boolean useGiftCards,boolean giftCardFirst,Target target,List<coupons.promotions.ClippedCoupon> coupons) {Options(boolean rolling,boolean useGiftCards,boolean giftCardFirst,Target target){this(rolling,useGiftCards,giftCardFirst,target,null);}}
    record Target(ShoppingPlan.ScenarioPromotion promotion,String scope,Set<String> values,Map<String,String> categories,Set<String> excludedSkus,Set<String> excludedCategories,int usesRemaining) {Target(ShoppingPlan.ScenarioPromotion promotion,String scope,Set<String> values,Map<String,String> categories){this(promotion,scope,values,categories,Set.of(),Set.of(),1);}}
    static Map<String,Object> plan(List<Candidate> candidates,List<Reward> available,BigDecimal wc,BigDecimal budget,String mode,LocalDate day,List<RequiredItem> required,boolean includeOptional,Options options) {
        if(mode==null||mode.isBlank()||mode.equals("AUTO")){
            var alternatives=new ArrayList<Map<String,Object>>();
            for(String strategy:List.of("LOWEST_OOP","EXPIRING_FIRST","WC_FIRST","REWARDS","MAX_REGISTER_REWARDS","MAX_REWARDS")){var report=plan(candidates,available,wc,budget,strategy,day,required,includeOptional,options);report.put("strategy",strategy);alternatives.add(report);}
            alternatives.sort(Comparator.<Map<String,Object>,Boolean>comparing(r->!Boolean.TRUE.equals(r.get("requirementsMet"))).thenComparing(r->r.get("cash") instanceof BigDecimal n?n:new BigDecimal("1E30")).thenComparing(r->(r.get("earnings") instanceof BigDecimal n?n:BigDecimal.ZERO).negate()));
            var result=new LinkedHashMap<>(alternatives.getFirst());result.put("alternatives",alternatives.stream().map(r->Map.of("strategy",r.get("strategy"),"cash",Objects.toString(r.get("cash"),"Unknown"),"earnings",Objects.toString(r.get("earnings"),"Unknown"))).toList());return result;
        }
        if(budget.signum()<0||wc.signum()<0)throw new IllegalArgumentException("Budget and balance must be nonnegative.");
        if(!Set.of("LOWEST_OOP","REWARDS","EXPIRING_FIRST","WC_FIRST","MAX_REGISTER_REWARDS","MAX_REWARDS","AUTO").contains(mode))throw new IllegalArgumentException("Choose a planning mode.");
        var incomplete=candidates.stream().filter(c->c.scenario()==null).toList();
        var requiredIds=new HashSet<String>();var requiredRows=new ArrayList<Map<String,Object>>();var issues=new ArrayList<String>();
        var quantities=new LinkedHashMap<String,Map<UUID,Integer>>();
        for(var item:required){
            if(quantities.computeIfAbsent(item.candidateId(),id->new LinkedHashMap<>()).putIfAbsent(item.lineId(),item.quantity())!=null)throw new IllegalArgumentException("Select each Must Buy item once.");
            requiredIds.add(item.candidateId());
            var source=candidates.stream().filter(c->c.id().equals(item.candidateId())).findFirst().orElse(null);
            var line=source==null||source.scenario()==null?null:source.scenario().items().stream().filter(l->l.id().equals(item.lineId())).findFirst().orElse(null);
            requiredRows.add(Map.of("candidateId",item.candidateId(),"lineId",item.lineId(),"quantity",item.quantity(),"name",line==null?(item.name()==null?"Required item in "+(source==null?"the original deal":source.name()):item.name()):line.name()));
            if(line==null)issues.add((item.name()==null?"Required item in "+(source==null?"the original deal":source.name()):item.name())+" needs review: "+(source!=null&&source.validationIssue()!=null?source.validationIssue():"its product reference, source promotion or pricing is unverified")+". Edit the original deal; no substitute was selected.");
            else if(priceLabel(line.name()))issues.add("The product name in "+source.name()+" is a price label, not an identified product. Correct its name and qualifying products in the original deal before planning.");
        }
        var prepared=new ArrayList<Candidate>();var requiredGroups=new HashMap<String,String>();
        for(var candidate:candidates){
            var chosen=quantities.get(candidate.id());
            if(chosen==null){if(includeOptional&&candidate.scenario()!=null&&candidate.scenario().items().stream().noneMatch(l->priceLabel(l.name())))prepared.add(candidate);continue;}
            if(candidate.scenario()==null)continue;
            var previous=requiredGroups.putIfAbsent(candidate.group(),candidate.id());if(previous!=null&&!previous.equals(candidate.id()))issues.add("Must Buy selections belong to mutually exclusive alternatives: "+previous+" and "+candidate.id()+".");
            var source=candidate.scenario();boolean changed=source.items().size()!=chosen.size()||source.items().stream().anyMatch(l->!Objects.equals(chosen.get(l.id()),l.quantity()));
            if(changed&&source.taxRate()==null&&source.tax().signum()>0)issues.add("Confirm tax for the changed quantities in "+candidate.name()+". Its fixed tax was recorded for a different basket.");
            var lines=source.items().stream().filter(l->chosen.containsKey(l.id())).map(l->{var rule=l.priceRule();if(rule!=null&&!rule.lineIds().isEmpty())rule=new ShoppingPlan.PriceRule(rule.type(),rule.bundlePrice(),rule.lineIds().stream().filter(chosen::containsKey).toList(),rule.buyQuantity(),rule.freeQuantity());return new ShoppingPlan.Line(l.id(),l.name(),l.sku(),l.regular(),l.sale(),chosen.get(l.id()),l.memberRewardRate(),rule);}).toList();
            if(lines.isEmpty())continue;
            var coupons=source.coupons().stream().filter(c->c.wholeReceipt()||c.lineIds().stream().anyMatch(chosen::containsKey)).map(c->new ShoppingPlan.ScenarioCoupon(c.id(),c.description(),c.amount(),c.requiredQuantity(),c.lineIds().stream().filter(chosen::containsKey).toList(),c.savedCouponId(),c.kind(),c.wholeReceipt(),c.pendingVerification())).toList();
            var promotions=source.promotions().stream().filter(c->c.wholeReceipt()||c.lineIds().stream().anyMatch(chosen::containsKey)).map(c->new ShoppingPlan.ScenarioPromotion(c.id(),c.description(),c.rewardType(),c.rewardAmount(),c.requiredQuantity(),c.requiredSpend(),c.spendBasis(),c.allowsWalgreensCash(),c.lineIds().stream().filter(chosen::containsKey).toList(),c.wholeReceipt(),c.allowsWalmartCash(),c.restrictSameRegisterReward(),c.prohibitedRegisterRewardProgram(),c.originProduct(),c.originBrand(),c.originProgram(),c.originPromotion(),c.paymentRulesReviewed(),c.giftCardRule(),c.rollingAllowed(),c.earnedRegisterRewardType(),c.allowsRegisterRewards(),c.allowsEcb())).toList();
            var scenario=new ShoppingPlan.Scenario(lines,coupons,promotions,source.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,source.paymentMethod(),source.fundingNotes(),source.taxRate(),List.of(),source.source(),source.walgreensCashIncrement(),source.walgreensCashLimit());
            prepared.add(new Candidate(candidate.id(),candidate.name(),candidate.store(),scenario,candidate.group()));
        }
        if(!issues.isEmpty()){
            var result=new LinkedHashMap<String,Object>();result.put("requiredItems",requiredRows);result.put("requirementsMet",false);result.put("affordable",false);result.put("issues",issues);result.put("additionalCashNeeded",null);result.put("steps",List.of());result.put("reviews",List.of());result.put("cash",null);result.put("earnings",null);result.put("remaining",budget);result.put("rewardsUsed",List.of());result.put("emptyReason","REQUIRED_ITEMS");result.put("notice","Required items are retained. Complete the missing details before calculating verified checkout payments.");return result;
        }
        candidates=expandTargetBaskets(prepared,options.target()!=null);
        var seen=new HashSet<String>();var rewards=available.stream().filter(r->seen.add(r.id())&&r.amount().signum()>0&&(r.expires()==null||!r.expires().isBefore(day))).sorted(rewardOrder()).toList();
        var pool=new LinkedHashMap<String,Reward>();for(var reward:rewards)pool.put(reward.id(),reward);
        var rank=(Comparator<State>)(a,b)->{
            int c=Long.compare(requiredIds.stream().filter(b.plans()::contains).count(),requiredIds.stream().filter(a.plans()::contains).count());if(c!=0)return c;
            c=Boolean.compare(targetMet(b,options),targetMet(a,options));if(c!=0)return c;
            c=Boolean.compare(b.oop().compareTo(budget)<=0,a.oop().compareTo(budget)<=0);if(c!=0)return c;
            if(mode.equals("MAX_REWARDS")){c=b.earnings().compareTo(a.earnings());if(c!=0)return c;}
            if(options.giftCardFirst()){c=a.oop().compareTo(b.oop());if(c!=0)return c;c=giftSpent(b).compareTo(giftSpent(a));if(c!=0)return c;}
            if(mode.equals("EXPIRING_FIRST"))for(var reward:rewards)if(reward.expires()!=null){c=Boolean.compare(b.used().contains(reward.id()),a.used().contains(reward.id()));if(c!=0)return c;}
            if(mode.equals("MAX_REGISTER_REWARDS"))for(var reward:rewards)if(reward.type().equals("REGISTER_REWARD")&&reward.expires()!=null){c=Boolean.compare(b.used().contains(reward.id()),a.used().contains(reward.id()));if(c!=0)return c;}
            if(mode.equals("WC_FIRST")){c=b.wcSpent().compareTo(a.wcSpent());if(c!=0)return c;}
            c=a.oop().compareTo(b.oop());if(c!=0)return c;
            if(options.giftCardFirst()){c=giftSpent(b).compareTo(giftSpent(a));if(c!=0)return c;}
            if(mode.equals("REWARDS")){c=b.earnings().compareTo(a.earnings());if(c!=0)return c;}
            if(mode.equals("MAX_REGISTER_REWARDS")){c=expectedRegisterRewards(b).compareTo(expectedRegisterRewards(a));if(c!=0)return c;}
            for(var reward:rewards){c=Boolean.compare(b.used().contains(reward.id()),a.used().contains(reward.id()));if(c!=0)return c;}
            c=Integer.compare(b.steps().size(),a.steps().size());if(c!=0)return c;
            c=a.steps().stream().map(Step::id).toList().toString().compareTo(b.steps().stream().map(Step::id).toList().toString());return c!=0?c:new TreeMap<>(a.funds()).toString().compareTo(new TreeMap<>(b.funds()).toString());
        };
        var initial=new State(List.of(),Set.of(),Set.of(),wc,BigDecimal.ZERO,BigDecimal.ZERO,pool,BigDecimal.ZERO,Set.of());
        var frontier=List.of(initial);var all=new ArrayList<State>();var reviews=new LinkedHashMap<String,Map<String,String>>();int evaluations=0;boolean budgetBlocked=false;
        final var originalCandidates=List.copyOf(prepared);
        search: for(int depth=0;depth<prepared.size();depth++){
            var next=new ArrayList<State>();
            for(var state:frontier)for(var basket:candidates){
                if(basket.includes().stream().anyMatch(state.plans()::contains)||conflicts(state,basket,originalCandidates))continue;
                boolean mandatory=basket.includes().stream().anyMatch(requiredIds::contains);
                if(!mandatory&&!state.plans().containsAll(requiredIds))continue;
                var variants=new ArrayList<Candidate>();variants.add(basket);
                if(options.target()!=null&&targetUses(state,options)<options.target().usesRemaining()&&retailer(basket.store()).equals("WALGREENS")){
                    var targeted=withTarget(basket,options.target());if(targeted!=null)variants.add(targeted);
                }
                for(var variant:variants)for(var candidate:couponOptions(automaticCoupons(variant,options.coupons(),day))){
                    boolean targeted=options.target()!=null&&targetUses(state,options)<options.target().usesRemaining()&&candidate.scenario().promotions().stream().anyMatch(p->p.id().equals(options.target().promotion().id()));
                    if(options.target()!=null&&targetUses(state,options)>=options.target().usesRemaining()){
                        var source=candidate.scenario();var promotions=source.promotions().stream().filter(p->!p.id().equals(options.target().promotion().id())).toList();
                        candidate=new Candidate(candidate.id(),candidate.name(),candidate.store(),new ShoppingPlan.Scenario(source.items(),source.coupons(),promotions,source.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,source.paymentMethod(),source.fundingNotes(),source.taxRate(),List.of(),source.source(),source.walgreensCashIncrement(),source.walgreensCashLimit()),candidate.group(),candidate.includes());
                    }
                    var usableCoupons=candidate.scenario().coupons().stream().filter(c->c.savedCouponId()==null||!state.coupons().contains(c.savedCouponId())).toList();
                    candidate=new Candidate(candidate.id(),candidate.name(),candidate.store(),withCoupons(candidate.scenario(),usableCoupons),candidate.group(),candidate.includes());
                    BigDecimal due=amount(funded(candidate.scenario(),List.of()).estimate(candidate.store()),"merchandiseAfterCoupons");
                    var paymentOptions=new ArrayList<List<ShoppingPlan.ScenarioPayment>>();paymentOptions.add(List.of());
                    for(var reward:state.funds().values().stream().sorted(rewardOrder()).toList())if(!partial(reward)&&eligibleStore(reward,candidate.store())&&reward.amount().compareTo(due)<=0){
                        var single=funded(candidate.scenario(),List.of(reward.payment())).estimate(candidate.store());
                        if(needsReview(single))reviews.put(candidate.id()+":"+reward.id(),review(candidate,reward.id(),reward.description(),single,reward.payment()));
                        var additions=new ArrayList<List<ShoppingPlan.ScenarioPayment>>();
                        for(var option:paymentOptions){if(++evaluations>25000)break search;var chosen=new ArrayList<>(option);chosen.add(reward.payment());
                            if(chosen.stream().map(ShoppingPlan.ScenarioPayment::amount).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(due)>0)continue;
                            var check=funded(candidate.scenario(),chosen).estimate(candidate.store());
                            if(valid(check,true))additions.add(List.copyOf(chosen));if(additions.size()>=128)break;
                        }
                        paymentOptions.addAll(additions);if(paymentOptions.size()>256)paymentOptions.subList(256,paymentOptions.size()).clear();
                    }
                    for(var option:paymentOptions)for(var cash:cashOptions(candidate.scenario(),option,retailer(candidate.store()).equals("WALGREENS")?state.wc():BigDecimal.ZERO,candidate.store())){
                        var base=new ArrayList<>(option);if(cash.signum()>0)base.add(new ShoppingPlan.ScenarioPayment(stable(candidate.id()+":wc"),"WALGREENS_CASH",cash,"Recorded/simulated Walgreens Cash available before this transaction"));
                        for(var chosen:partialOptions(candidate,state,base,options,reviews)){
                            if(++evaluations>25000)break search;
                            var scenario=funded(candidate.scenario(),chosen);var calculated=scenario.estimate(candidate.store());
                            if(!valid(calculated,mandatory)||targeted&&!promotionMet(calculated,options.target().promotion().id()))continue;
                            var oop=state.oop().add(checkoutCents(amount(calculated,"calculatedOop")));if(oop.compareTo(budget)>0){budgetBlocked=true;if(!mandatory)continue;}
                            var remaining=new LinkedHashMap<>(state.funds());var ids=new HashSet<>(state.used());
                            for(var payment:chosen)for(var reward:List.copyOf(remaining.values()))if(payment.id().equals(reward.payment().id())){
                                ids.add(reward.id());BigDecimal left=reward.amount().subtract(payment.amount());if(left.signum()==0||!partial(reward))remaining.remove(reward.id());else remaining.put(reward.id(),withAmount(reward,left));
                            }
                            var plans=new HashSet<>(state.plans());plans.addAll(candidate.includes());
                            var couponsUsed=new HashSet<>(state.coupons());for(var coupon:scenario.coupons())if(coupon.savedCouponId()!=null&&couponApplied(calculated,coupon.id()))couponsUsed.add(coupon.savedCouponId());
                            BigDecimal wcNext=state.wc().subtract(cash);
                            if(options.rolling())for(var promotion:scenario.promotions())if(Boolean.TRUE.equals(promotion.rollingAllowed())&&Boolean.TRUE.equals(promotion.paymentRulesReviewed())&&promotionMet(calculated,promotion.id())){
                                if(promotion.rewardType().equals("WALGREENS_CASH")&&retailer(candidate.store()).equals("WALGREENS"))wcNext=wcNext.add(promotion.rewardAmount());
                                else if(Set.of("REGISTER_REWARD","ECB","WALMART_CASH").contains(promotion.rewardType())){
                                    String source="sim:"+candidate.id()+":"+promotion.id();
                                    remaining.put(source,new Reward(source,"Expected "+promotion.description()+" from transaction "+(state.steps().size()+1),promotion.rewardType(),candidate.store(),promotion.rewardAmount(),null,promotion.earnedRegisterRewardType(),promotion.originProduct(),promotion.originBrand(),promotion.originProgram(),promotion.originPromotion()==null?promotion.id().toString():promotion.originPromotion()));
                                }
                            }
                            var steps=new ArrayList<>(state.steps());steps.add(new Step(candidate.id(),candidate.name(),candidate.store(),scenario,calculated,List.copyOf(remaining.values()),wcNext,candidate.includes()));
                            var added=new State(steps,plans,ids,wcNext,oop,state.earnings().add(amount(calculated,"potentialRewards")),remaining,state.wcSpent().add(cash),couponsUsed);next.add(added);all.add(added);
                            if(next.size()>600){next.sort(rank);next.subList(300,next.size()).clear();}if(all.size()>600){all.sort(rank);all.subList(300,all.size()).clear();}
                        }
                    }
                }
            }
            next.sort(rank);var unique=new LinkedHashMap<String,State>();for(var state:next){String key=new TreeSet<>(state.plans())+":"+state.funds()+":"+state.wc()+":"+targetUses(state,options);unique.putIfAbsent(key,state);if(unique.size()>=150)break;}
            frontier=List.copyOf(unique.values());all.addAll(frontier);if(frontier.isEmpty())break;
        }
        all.removeIf(state->!state.plans().containsAll(requiredIds));all.sort(rank);var result=new LinkedHashMap<String,Object>();var best=all.isEmpty()?initial:all.getFirst();
        boolean requirementsMet=best.plans().containsAll(requiredIds),boosterMet=targetMet(best,options);
        var problems=new ArrayList<String>();
        for(var candidate:incomplete)if(candidate.validationIssue()!=null)problems.add(candidate.name()+": "+candidate.validationIssue());if(!requirementsMet)problems.add("No complete verified payment combination was found for every Must Buy selection within the bounded search. Required items were not removed.");
        if(!boosterMet)problems.add("The selected Spend Booster could not be satisfied with the recorded products, payment rules and budget. No booster reward is claimed.");
        result.put("requiredItems",requiredRows);result.put("requirementsMet",requirementsMet);result.put("boosterMet",boosterMet);result.put("affordable",requirementsMet&&best.oop().compareTo(budget)<=0);result.put("additionalCashNeeded",requirementsMet?best.oop().subtract(budget).max(BigDecimal.ZERO):null);result.put("issues",problems);
        var rrUsed=rewards.stream().filter(r->r.type().equals("REGISTER_REWARD")&&best.used().contains(r.id())).map(Reward::amount).reduce(BigDecimal.ZERO,BigDecimal::add);var rrEarned=expectedRegisterRewards(best);
        result.put("emptyReason",budgetBlocked?"BUDGET":"ELIGIBILITY");result.put("reviews",List.copyOf(reviews.values()));result.put("steps",best.steps());result.put("cash",requirementsMet?best.oop():null);result.put("earnings",best.earnings());result.put("remaining",budget.subtract(best.oop()).max(BigDecimal.ZERO));result.put("rewardsUsed",best.used());result.put("registerRewardsUsedValue",rrUsed);result.put("expectedRegisterRewards",rrEarned);result.put("netRegisterRewardChange",rrEarned.subtract(rrUsed));result.put("fundsRemaining",best.funds().values());result.put("walgreensCashRemaining",best.wc());
        result.put("notice","Suggested combination from a bounded search using entered offer rules. "+(options.rolling()?"Only rewards explicitly recorded as available for later transactions are rolled; expected rewards never fund their originating transaction.":"Existing funds only; newly earned rewards are not rolled automatically.")+" Unknown origins and balances are excluded when eligibility depends on them. Saved coupons use the existing SKU/date/store matcher; unverified or redeemed linked coupons are excluded. Search is bounded; a global optimum is not guaranteed.");return result;
    }
    static String retailer(String store){
        if(store==null)return "";String value=store.trim().toLowerCase(Locale.ROOT);
        for(String name:List.of("walgreens","walmart","cvs"))if(value.equals(name)||value.startsWith(name+" ")||value.startsWith(name+"#"))return name.toUpperCase(Locale.ROOT);
        return value.toUpperCase(Locale.ROOT);
    }
    private static boolean priceLabel(String name){return name!=null&&name.matches("(?is)^(?:regular|sale)\\s*(?:price)?\\s*:?\\s*\\$.*");}
    private static BigDecimal checkoutCents(BigDecimal value){var rounded=value.setScale(2,java.math.RoundingMode.HALF_UP);return rounded.compareTo(value)==0?value:rounded;}
    private static UUID stable(String value){return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static Comparator<Reward> rewardOrder(){return Comparator.comparing(Reward::expires,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Reward::id);}
    private static boolean partial(Reward r){return Set.of("GIFT_CARD","WALMART_CASH").contains(r.type());}
    private static Reward withAmount(Reward r,BigDecimal amount){return new Reward(r.id(),r.description(),r.type(),r.store(),amount,r.expires(),r.registerRewardType(),r.originProduct(),r.originBrand(),r.originProgram(),r.originPromotion(),r.eligibility());}
    private static boolean eligibleStore(Reward reward,String store){
        if(reward.type().equals("GIFT_CARD"))return coupons.rewards.PaymentEligibility.check(store,reward.type(),reward.eligibility(),reward.amount(),reward.eligibility()==null?null:reward.eligibility().evaluatedOn()).equals("Met");
        if(!retailer(reward.store()).equals(retailer(store)))return false;
        return switch(retailer(store)){case "WALGREENS"->Set.of("REGISTER_REWARD","GIFT_CARD").contains(reward.type());case "CVS"->Set.of("ECB","GIFT_CARD").contains(reward.type());case "WALMART"->Set.of("WALMART_CASH","GIFT_CARD").contains(reward.type());default->false;};
    }
    private static BigDecimal giftSpent(State state){return state.steps().stream().flatMap(s->s.scenario().payments().stream()).filter(p->p.type().equals("GIFT_CARD")).map(ShoppingPlan.ScenarioPayment::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal expectedRegisterRewards(State state){return state.steps().stream().map(s->amount(s.estimate(),"expectedRegisterRewards")).reduce(BigDecimal.ZERO,BigDecimal::add);}
    @SuppressWarnings("unchecked") private static boolean promotionMet(Map<String,Object> report,UUID id){return ((List<Map<String,Object>>)report.get("promotions")).stream().anyMatch(row->id.equals(row.get("id"))&&Boolean.TRUE.equals(row.get("eligible")));}
    private static long targetUses(State state,Options options){return options.target()==null?0:state.steps().stream().filter(step->promotionMet(step.estimate(),options.target().promotion().id())).count();}
    private static boolean targetMet(State state,Options options){return options.target()==null||state.steps().stream().anyMatch(step->promotionMet(step.estimate(),options.target().promotion().id()));}
    @SuppressWarnings("unchecked") private static boolean couponApplied(Map<String,Object> report,UUID id){return ((List<Map<String,Object>>)report.get("coupons")).stream().anyMatch(row->id.equals(row.get("id"))&&Boolean.TRUE.equals(row.get("applied")));}
    private static ShoppingPlan.Scenario withCoupons(ShoppingPlan.Scenario s,List<ShoppingPlan.ScenarioCoupon> coupons){return new ShoppingPlan.Scenario(s.items(),coupons,s.promotions(),s.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,s.paymentMethod(),s.fundingNotes(),s.taxRate(),List.of(),s.source(),s.walgreensCashIncrement(),s.walgreensCashLimit());}
    private static boolean conflicts(State state,Candidate candidate,List<Candidate> originals){
        Set<String> groups=new HashSet<>();for(var original:originals)if(state.plans().contains(original.id()))groups.add(original.group());
        return originals.stream().anyMatch(original->candidate.includes().contains(original.id())&&groups.contains(original.group()));
    }
    @SuppressWarnings("unchecked") private static List<List<ShoppingPlan.ScenarioPayment>> partialOptions(Candidate candidate,State state,List<ShoppingPlan.ScenarioPayment> base,Options options,Map<String,Map<String,String>> reviews){
        var choices=new ArrayList<List<ShoppingPlan.ScenarioPayment>>();choices.add(List.copyOf(base));
        var funds=state.funds().values().stream().filter(r->partial(r)&&eligibleStore(r,candidate.store())&&(!r.type().equals("GIFT_CARD")||options.useGiftCards())).sorted(Comparator.comparing((Reward r)->options.giftCardFirst()&&!r.type().equals("GIFT_CARD")).thenComparing(rewardOrder())).toList();
        for(var reward:funds){var additions=new ArrayList<List<ShoppingPlan.ScenarioPayment>>();
            for(var choice:choices){var report=funded(candidate.scenario(),choice).estimate(candidate.store());var max=reward.amount().min(amount(report,"calculatedOop")).setScale(2,java.math.RoundingMode.DOWN);var amounts=new TreeSet<BigDecimal>(Comparator.reverseOrder());if(max.signum()>0)amounts.add(max);
                for(var promotion:candidate.scenario().promotions())if((reward.type().equals("GIFT_CARD")&&"REDUCES".equals(promotion.giftCardRule())||reward.type().equals("WALMART_CASH")&&promotion.spendBasis().equals("AFTER_REWARDS_USED"))&&promotion.requiredSpend().signum()>0){
                    var row=((List<Map<String,Object>>)report.get("promotions")).stream().filter(p->promotion.id().equals(p.get("id"))).findFirst().orElseThrow();var safe=((BigDecimal)row.get("qualifyingSpend")).subtract(promotion.requiredSpend()).min(max).setScale(2,java.math.RoundingMode.DOWN);if(safe.signum()>0)amounts.add(safe);
                }
                for(var value:amounts){var selected=new ArrayList<>(choice);var payment=withAmount(reward,value).payment();selected.add(payment);var check=funded(candidate.scenario(),selected).estimate(candidate.store());if(needsReview(check))reviews.put(candidate.id()+":"+reward.id(),review(candidate,reward.id(),reward.description(),check,payment));if(valid(check,true))additions.add(List.copyOf(selected));if(additions.size()>=64)break;}
                if(additions.size()>=64)break;
            }
            choices.addAll(additions);
            var reports=new IdentityHashMap<List<ShoppingPlan.ScenarioPayment>,Map<String,Object>>();for(var choice:choices)reports.put(choice,funded(candidate.scenario(),choice).estimate(candidate.store()));
            boolean targeted=options.target()!=null&&candidate.scenario().promotions().stream().anyMatch(p->p.id().equals(options.target().promotion().id()));
            choices.sort((a,b)->{int c=targeted?Boolean.compare(promotionMet(reports.get(b),options.target().promotion().id()),promotionMet(reports.get(a),options.target().promotion().id())):0;if(c!=0)return c;c=amount(reports.get(a),"calculatedOop").compareTo(amount(reports.get(b),"calculatedOop"));if(c!=0)return c;return options.giftCardFirst()?a.stream().filter(p->p.type().equals("GIFT_CARD")).map(ShoppingPlan.ScenarioPayment::amount).reduce(BigDecimal.ZERO,BigDecimal::add).negate().compareTo(b.stream().filter(p->p.type().equals("GIFT_CARD")).map(ShoppingPlan.ScenarioPayment::amount).reduce(BigDecimal.ZERO,BigDecimal::add).negate()):0;});
            if(choices.size()>64)choices.subList(63,choices.size()).clear();if(choices.stream().noneMatch(base::equals))choices.add(List.copyOf(base));
        }
        return choices;
    }
    private static Candidate withTarget(Candidate candidate,Target target){
        var source=candidate.scenario();var p=target.promotion();
        var ids=source.items().stream().filter(line->{
            String sku=line.sku()==null?null:skuKey(line.sku()),category=sku==null?null:target.categories().get(sku);
            if(!target.excludedSkus().isEmpty()&&(sku==null||target.excludedSkus().contains(sku)))return false;
            if(!target.excludedCategories().isEmpty()&&(category==null||categoryMatches(category,target.excludedCategories())))return false;
            return target.scope().equals("RECEIPT")||sku!=null&&(target.scope().equals("SKUS")?target.values().contains(sku):categoryMatches(category,target.values()));
        }).map(ShoppingPlan.Line::id).toList();
        if(ids.isEmpty())return null;
        var promotions=new ArrayList<>(source.promotions().stream().filter(row->!row.id().equals(p.id())).toList());promotions.add(copyPromotion(p,ids,false));
        var scenario=new ShoppingPlan.Scenario(source.items(),source.coupons(),promotions,source.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,source.paymentMethod(),source.fundingNotes(),source.taxRate(),List.of(),source.source(),source.walgreensCashIncrement(),source.walgreensCashLimit());
        return new Candidate(candidate.id(),candidate.name(),candidate.store(),scenario,candidate.group(),candidate.includes());
    }
    private static boolean categoryMatches(String category,Set<String> choices){return category!=null&&choices.stream().anyMatch(value->category.equalsIgnoreCase(value)||category.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT)+" > "));}
    static String skuKey(String value){String sku=value.trim();return sku.matches("[0-9]{12,14}")?"0".repeat(14-sku.length())+sku:sku;}
    private static ShoppingPlan.ScenarioPromotion copyPromotion(ShoppingPlan.ScenarioPromotion p,List<UUID> ids,boolean whole){return new ShoppingPlan.ScenarioPromotion(p.id(),p.description(),p.rewardType(),p.rewardAmount(),p.requiredQuantity(),p.requiredSpend(),p.spendBasis(),p.allowsWalgreensCash(),ids,whole,p.allowsWalmartCash(),p.restrictSameRegisterReward(),p.prohibitedRegisterRewardProgram(),p.originProduct(),p.originBrand(),p.originProgram(),p.originPromotion(),p.paymentRulesReviewed(),p.giftCardRule(),p.rollingAllowed(),p.earnedRegisterRewardType(),p.allowsRegisterRewards(),p.allowsEcb());}
    private static List<Candidate> expandTargetBaskets(List<Candidate> originals,boolean expand){
        var result=new ArrayList<>(originals);if(!expand)return result;
        for(int i=0;i<originals.size();i++)for(int j=i+1;j<originals.size();j++){
            var combined=merge(originals.get(i),originals.get(j));if(combined!=null)result.add(combined);if(result.size()>=100)return result;
            if(combined!=null)for(int k=j+1;k<originals.size();k++){var three=merge(combined,originals.get(k));if(three!=null)result.add(three);if(result.size()>=100)return result;}
        }return result;
    }
    private static Candidate merge(Candidate a,Candidate b){
        if(!retailer(a.store()).equals("WALGREENS")||!retailer(a.store()).equals(retailer(b.store()))||a.group().equals(b.group()))return null;
        var x=a.scenario();var y=b.scenario();if(!Objects.equals(x.taxRate(),y.taxRate())||!Objects.equals(x.walgreensCashIncrement(),y.walgreensCashIncrement())||!Objects.equals(x.walgreensCashLimit(),y.walgreensCashLimit()))return null;
        var lines=new ArrayList<>(x.items());lines.addAll(y.items());if(lines.stream().map(ShoppingPlan.Line::id).distinct().count()!=lines.size())return null;
        var coupons=new ArrayList<ShoppingPlan.ScenarioCoupon>();var promotions=new ArrayList<ShoppingPlan.ScenarioPromotion>();var couponSources=new HashSet<UUID>();var ids=new HashSet<UUID>();
        for(var scenario:List.of(x,y)){
            for(var coupon:scenario.coupons()){if(coupon.savedCouponId()!=null&&!couponSources.add(coupon.savedCouponId()))continue;if(!ids.add(coupon.id()))return null;coupons.add(new ShoppingPlan.ScenarioCoupon(coupon.id(),coupon.description(),coupon.amount(),coupon.requiredQuantity(),coupon.wholeReceipt()?scenario.items().stream().map(ShoppingPlan.Line::id).toList():coupon.lineIds(),coupon.savedCouponId(),coupon.kind(),false,coupon.pendingVerification()));}
            for(var promotion:scenario.promotions()){if(!ids.add(promotion.id()))return null;promotions.add(copyPromotion(promotion,promotion.wholeReceipt()?scenario.items().stream().map(ShoppingPlan.Line::id).toList():promotion.lineIds(),false));}
        }
        var includes=new ArrayList<>(a.includes());includes.addAll(b.includes());
        return new Candidate(String.join("+",includes),a.name()+" + "+b.name(),a.store(),new ShoppingPlan.Scenario(lines,coupons,promotions,x.tax().add(y.tax()),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH","Combined recorded baskets",x.taxRate(),List.of(),null,x.walgreensCashIncrement(),x.walgreensCashLimit()),String.join("+",includes),includes);
    }
    private static Candidate automaticCoupons(Candidate candidate,List<coupons.promotions.ClippedCoupon> available,LocalDate day){
        if(available==null)return candidate;
        var source=candidate.scenario();var selected=new ArrayList<>(source.coupons().stream().filter(c->c.savedCouponId()==null).toList());
        selected.addAll(ShoppingOfferMatch.match(source,candidate.store(),day,available));
        return new Candidate(candidate.id(),candidate.name(),candidate.store(),withCoupons(source,selected),candidate.group(),candidate.includes());
    }
    private static List<Candidate> couponOptions(Candidate candidate){
        var source=candidate.scenario();var variants=new ArrayList<List<ShoppingPlan.ScenarioCoupon>>();variants.add(source.coupons());
        for(var coupon:source.coupons()){
            for(var existing:List.copyOf(variants))if(existing.contains(coupon)&&variants.size()<64)variants.add(existing.stream().filter(c->!c.id().equals(coupon.id())).toList());
            if(variants.size()>=64)break;
        }
        if(variants.stream().noneMatch(List::isEmpty))variants.add(List.of());
        return variants.stream().map(coupons->new Candidate(candidate.id(),candidate.name(),candidate.store(),new ShoppingPlan.Scenario(source.items(),coupons,source.promotions(),source.tax(),source.walgreensCashUsed(),source.registerRewardsUsed(),source.giftCardUsed(),source.manualOop(),source.paymentMethod(),source.fundingNotes(),source.taxRate(),source.payments(),source.source(),source.walgreensCashIncrement(),source.walgreensCashLimit()),candidate.group(),candidate.includes())).toList();
    }
    @SuppressWarnings("unchecked") private static Map<String,String> review(Candidate candidate,String rewardId,String rewardName,Map<String,Object> report,ShoppingPlan.ScenarioPayment payment){
        var result=new LinkedHashMap<String,String>(Map.of("candidateId",candidate.id(),"candidateName",candidate.name(),"rewardId",rewardId,"rewardName",rewardName,"status","Needs Review"));
        String field="payment.registerRewardType",message="Record the Register Reward type to check coupon-to-item eligibility.";
        if(!"Needs Review".equals(report.get("fundingRuleStatus"))){
            var rows=(List<Map<String,Object>>)report.get("promotions");
            var row=rows.stream().filter(p->"Needs Review".equals(p.get("paymentRuleStatus"))).findFirst().orElseThrow();
            var promotion=candidate.scenario().promotions().stream().filter(p->p.id().equals(row.get("id"))).findFirst().orElseThrow();
            result.put("promotionId",promotion.id().toString());
            if(payment!=null&&payment.type().equals("GIFT_CARD")){field="promotion.giftCardRule";message="Record whether gift cards are permitted and how they affect qualifying spend.";}
            else if(promotion.rewardType().equals("REGISTER_REWARD")&&payment!=null&&(payment.originBrand()==null||payment.originBrand().isBlank())&&(payment.originProduct()==null||payment.originProduct().isBlank())){
                field="payment.originBrand";message="Record the reward brand to check the same-brand payment restriction.";
            }else if(!Boolean.TRUE.equals(promotion.paymentRulesReviewed())&&!promotion.restrictSameRegisterReward()&&(promotion.prohibitedRegisterRewardProgram()==null||promotion.prohibitedRegisterRewardProgram().isBlank())){
                field="promotion.paymentRulesReviewed";message="Review this promotion's recorded payment restrictions and spend basis.";
            }else if(promotion.prohibitedRegisterRewardProgram()!=null&&!promotion.prohibitedRegisterRewardProgram().isBlank()){
                field="payment.originProgram";message="Record the reward's originating program to evaluate the configured program restriction.";
            }else if(promotion.restrictSameRegisterReward()){
                if(promotion.originProduct()==null||promotion.originProduct().isBlank())field="promotion.originProduct";
                else if(promotion.originPromotion()==null||promotion.originPromotion().isBlank())field="promotion.originPromotion";
                else field=payment==null||payment.originProduct()==null||payment.originProduct().isBlank()?"payment.originProduct":"payment.originPromotion";
                message="Record product and promotion identities to evaluate the same-promotion rolling restriction.";
            }
        }
        if(rewardId.startsWith("sim:")){
            int split=rewardId.lastIndexOf(':');result.put("candidateId",rewardId.substring(4,split));result.put("promotionId",rewardId.substring(split+1));result.put("candidateName","Earning promotion");field=field.equals("payment.registerRewardType")?"promotion.earnedRegisterRewardType":field.startsWith("payment.")?"promotion."+field.substring(8):field;message="The expected reward's earning promotion needs these details before it can fund a later transaction. "+message;
        }
        result.put("field",field);result.put("message",message);return result;
    }
    @SuppressWarnings("unchecked") private static boolean valid(Map<String,Object> estimate,boolean required){
        return "Met".equals(estimate.get("fundingRuleStatus"))&&amount(estimate,"overFunding").signum()==0
            && ((List<Map<String,Object>>)estimate.get("promotions")).stream().allMatch(p->required?"Met".equals(p.get("paymentRuleStatus")):Boolean.TRUE.equals(p.get("eligible")));
    }
    @SuppressWarnings("unchecked") private static boolean needsReview(Map<String,Object> estimate){return "Needs Review".equals(estimate.get("fundingRuleStatus"))||((List<Map<String,Object>>)estimate.get("promotions")).stream().anyMatch(p->"Needs Review".equals(p.get("paymentRuleStatus")));}
    private static List<BigDecimal> cashOptions(ShoppingPlan.Scenario scenario,List<ShoppingPlan.ScenarioPayment> payments,BigDecimal balance,String store){
        var report=funded(scenario,payments).estimate(store);
        var maximum=amount(report,"calculatedOop").min(balance);
        if(scenario.walgreensCashLimit()!=null)maximum=maximum.min(scenario.walgreensCashLimit());
        var values=new TreeSet<BigDecimal>();values.add(BigDecimal.ZERO);
        for(var denomination:ShoppingPlan.Scenario.walgreensCashDenominations())if(denomination.compareTo(maximum)<=0&&scenario.allowsWalgreensCashAmount(denomination))values.add(denomination);
        return List.copyOf(values.descendingSet());
    }
    private static BigDecimal amount(Map<String,Object> values,String key){return (BigDecimal)values.get(key);}
    static ShoppingPlan.Scenario funded(ShoppingPlan.Scenario s,List<ShoppingPlan.ScenarioPayment> payments){return new ShoppingPlan.Scenario(s.items(),s.coupons(),s.promotions(),s.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH",s.fundingNotes(),s.taxRate(),payments,s.source(),s.walgreensCashIncrement(),s.walgreensCashLimit());}
}
