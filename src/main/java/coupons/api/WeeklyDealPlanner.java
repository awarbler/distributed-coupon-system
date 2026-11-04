package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Bounded read-only search. Only already-recorded funds are spendable. */
final class WeeklyDealPlanner {
    record Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario,String group) {
        Candidate(String id,String name,String store,ShoppingPlan.Scenario scenario){this(id,name,store,scenario,id);}
        Candidate {group=group==null?id:group;}
    }
    record Reward(String id,String description,String type,String store,BigDecimal amount,LocalDate expires) {}
    record Step(String id,String name,String store,ShoppingPlan.Scenario scenario,Map<String,Object> estimate) {}
    record State(List<Step> steps,Set<String> plans,Set<String> used,BigDecimal wc,BigDecimal oop,BigDecimal earnings) {}
    static Map<String,Object> plan(List<Candidate> candidates,List<Reward> available,BigDecimal wc,BigDecimal budget,String mode,LocalDate day) {
        if(budget.signum()<0||wc.signum()<0)throw new IllegalArgumentException("Budget and balance must be nonnegative.");
        if(!Set.of("LOWEST_OOP","REWARDS").contains(mode))throw new IllegalArgumentException("Choose a planning mode.");
        var seen=new HashSet<String>();var rewards=available.stream().filter(r->seen.add(r.id())&&r.amount().signum()>0&&(r.expires()==null||!r.expires().isBefore(day))).sorted(Comparator.comparing(Reward::expires,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Reward::id)).toList();
        var rank=(Comparator<State>)(a,b)->{
            // Rescue rewards expiring during this shopping week before other preferences.
            for(var r:rewards)if(r.expires()!=null&&!r.expires().isAfter(day.plusDays(6))){int c=Boolean.compare(b.used().contains(r.id()),a.used().contains(r.id()));if(c!=0)return c;}
            int c=mode.equals("REWARDS")?b.earnings().compareTo(a.earnings()):a.oop().compareTo(b.oop());if(c!=0)return c;
            c=mode.equals("REWARDS")?a.oop().compareTo(b.oop()):b.earnings().compareTo(a.earnings());if(c!=0)return c;
            return Integer.compare(b.steps().size(),a.steps().size());
        };
        var initial=new State(List.of(),Set.of(),Set.of(),wc,BigDecimal.ZERO,BigDecimal.ZERO);var frontier=List.of(initial);var all=new ArrayList<State>();int evaluations=0;
        search: for(int depth=0;depth<candidates.size();depth++) {
            var next=new ArrayList<State>();
            for(var state:frontier)for(var candidate:candidates)if(!state.plans().contains(candidate.id())&&state.steps().stream().noneMatch(step->candidates.stream().anyMatch(other->other.id().equals(step.id())&&other.group().equals(candidate.group())))){
                var payments=new ArrayList<ShoppingPlan.ScenarioPayment>();var used=new HashSet<>(state.used());
                boolean walgreens="walgreens".equalsIgnoreCase(candidate.store()==null?"":candidate.store().trim());
                var base=funded(candidate.scenario(),payments);var estimate=base.estimate(candidate.store());BigDecimal due=amount(estimate,"merchandiseAfterCoupons");
                // Prefixes allow leaving an RR for another candidate while trying earliest-expiry first.
                var options=new ArrayList<List<ShoppingPlan.ScenarioPayment>>();options.add(List.of());
                for(var reward:rewards)if(!used.contains(reward.id())&&reward.store().equalsIgnoreCase(candidate.store()==null?"":candidate.store())&&reward.amount().compareTo(due)<=0){
                    payments.add(new ShoppingPlan.ScenarioPayment(UUID.nameUUIDFromBytes(reward.id().getBytes(java.nio.charset.StandardCharsets.UTF_8)),reward.type(),reward.amount(),reward.description()+" · source "+reward.id()+" · expires "+(reward.expires()==null?"unknown":reward.expires())));
                    due=due.subtract(reward.amount());options.add(List.copyOf(payments));
                }
                for(var option:options)for(int denomination:new int[]{0,1,2,3,4,5,10,20,50}) {
                    if(++evaluations>25000)break search;
                    var cash=BigDecimal.valueOf(denomination);if(cash.signum()>0&&(!walgreens||cash.compareTo(state.wc())>0))continue;
                    var chosen=new ArrayList<>(option);if(cash.signum()>0)chosen.add(new ShoppingPlan.ScenarioPayment(UUID.randomUUID(),"WALGREENS_CASH",cash,"Recorded Walgreens Cash balance"));
                    var scenario=funded(candidate.scenario(),chosen);var calculated=scenario.estimate(candidate.store());
                    if(amount(calculated,"overFunding").signum()>0)continue;
                    // Do not recommend funding which invalidates an entered earning condition.
                    if(enteredEarnings(calculated).compareTo(enteredEarnings(estimate))<0)continue;
                    var oop=state.oop().add(amount(calculated,"calculatedOop"));if(oop.compareTo(budget)>0)continue;
                    var ids=new HashSet<>(state.used());for(var payment:option)for(var reward:rewards)if(payment.id().equals(UUID.nameUUIDFromBytes(reward.id().getBytes(java.nio.charset.StandardCharsets.UTF_8))))ids.add(reward.id());
                    var plans=new HashSet<>(state.plans());plans.add(candidate.id());var steps=new ArrayList<>(state.steps());steps.add(new Step(candidate.id(),candidate.name(),candidate.store(),scenario,calculated));
                    var added=new State(steps,plans,ids,state.wc().subtract(cash),oop,state.earnings().add(amount(calculated,"potentialRewards")));next.add(added);all.add(added);
                    if(next.size()>600){next.sort(rank);next.subList(300,next.size()).clear();}
                    if(all.size()>600){all.sort(rank);all.subList(300,all.size()).clear();}
                }
            }
            next.sort(rank);var unique=new LinkedHashMap<String,State>();for(var state:next){String key=new TreeSet<>(state.plans())+":"+new TreeSet<>(state.used())+":"+state.wc().toPlainString();unique.putIfAbsent(key,state);if(unique.size()>=150)break;}
            frontier=List.copyOf(unique.values());all.addAll(frontier);if(frontier.isEmpty())break;
        }
        all.sort(rank);var result=new LinkedHashMap<String,Object>();var best=all.isEmpty()?initial:all.getFirst();
        result.put("steps",best.steps());result.put("cash",best.oop());result.put("earnings",best.earnings());result.put("remaining",budget.subtract(best.oop()));result.put("rewardsUsed",best.used());
        result.put("notice","Suggested combination from a bounded search using entered offer rules. Existing funds only; newly earned rewards are not rolled automatically. Review RR eligibility and tax before shopping. Expiring rewards within seven days are prioritized.");return result;
    }
    @SuppressWarnings("unchecked") private static BigDecimal enteredEarnings(Map<String,Object> estimate){return ((List<Map<String,Object>>)estimate.get("promotions")).stream().map(p->(BigDecimal)p.get("potentialReward")).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static BigDecimal amount(Map<String,Object> values,String key){return (BigDecimal)values.get(key);}
    static ShoppingPlan.Scenario funded(ShoppingPlan.Scenario s,List<ShoppingPlan.ScenarioPayment> payments){return new ShoppingPlan.Scenario(s.items(),s.coupons(),s.promotions(),s.tax(),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,"CASH",s.fundingNotes(),s.taxRate(),payments,null);}
}
