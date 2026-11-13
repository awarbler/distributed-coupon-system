package coupons.shopping;

import coupons.promotions.Coupon;
import coupons.promotions.Promotion;
import coupons.rewards.PaymentEligibility;

import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** A dated, revisioned shopping plan. Planned purchases are stored separately. */
public record ShoppingPlan(UUID planId, String name, String retailer,
        LocalDate shoppingDate, List<UUID> plannedPurchaseIds, long revision, Scenario scenario, UUID purchasedReceiptId, Organization organization) implements java.io.Serializable {
    public ShoppingPlan(UUID id,String name,String retailer,LocalDate date,List<UUID> purchases,long revision,Scenario scenario,UUID receipt){this(id,name,retailer,date,purchases,revision,scenario,receipt,null);}
    public record Organization(String stage,UUID alternativeOf,Integer checkoutOrder,List<UUID> rewardSources) implements java.io.Serializable {
        public Organization {
            if(!java.util.Set.of("IDEA","TRIP").contains(stage)||checkoutOrder!=null&&checkoutOrder<0)throw new IllegalArgumentException("Invalid trip order.");
            rewardSources=rewardSources==null?List.of():List.copyOf(rewardSources);
        }
    }
    public ShoppingPlan(UUID id,String name,String retailer,LocalDate date,List<UUID> purchases,long revision,Scenario scenario){this(id,name,retailer,date,purchases,revision,scenario,null);}
    public ShoppingPlan(UUID id,String name,String retailer,LocalDate date,List<UUID> purchases,long revision){this(id,name,retailer,date,purchases,revision,null);}

    /** A saved what-if scenario. Never creates a receipt, redemption or account movement. */
    public record Line(UUID id,String name,String sku,java.math.BigDecimal regular,java.math.BigDecimal sale,int quantity,Integer memberRewardRate, PriceRule priceRule) implements java.io.Serializable {
        public Line(UUID id,String name,String sku,java.math.BigDecimal regular,java.math.BigDecimal sale,int quantity,Integer rate){this(id,name,sku,regular,sale,quantity,rate,null);}
        public Line(UUID id,String name,String sku,java.math.BigDecimal regular,java.math.BigDecimal sale,int quantity){this(id,name,sku,regular,sale,quantity,null);}
        public Line { if(memberRewardRate!=null&&!java.util.Set.of(0,1,5).contains(memberRewardRate))throw new IllegalArgumentException("Member reward rate must be 0, 1 or 5 percent."); Objects.requireNonNull(id);requireText(name,"Product");if(regular==null&&sale==null||regular!=null&&regular.signum()<0||sale!=null&&sale.signum()<0||quantity<1)throw new IllegalArgumentException("Enter a purchase price and positive quantity for every product."); }
    }
    /** Explicit same-product sale grouping. Existing scenarios without a rule retain their prices. */
    public record PriceRule(String type, java.math.BigDecimal bundlePrice,List<UUID> lineIds,Integer buyQuantity,Integer freeQuantity) implements java.io.Serializable {
        public PriceRule(String type,java.math.BigDecimal bundlePrice,List<UUID> lineIds){this(type,bundlePrice,lineIds,null,null);}
        public PriceRule(String type,java.math.BigDecimal bundlePrice){this(type,bundlePrice,List.of());}
        public PriceRule {
            lineIds=lineIds==null?List.of():List.copyOf(lineIds);
            if(type.equals("BUY_GET")&&(buyQuantity==null||freeQuantity==null||buyQuantity<1||freeQuantity<1||buyQuantity>1000||freeQuantity>1000))throw new IllegalArgumentException("Enter buy and free quantities from 1 to 1000.");
            if(!java.util.Set.of("BOGO_FREE","BOGO_HALF","BUY_ONE_GET_TWO","BUY_TWO_GET_ONE","TWO_FOR","BUY_GET").contains(type))
                throw new IllegalArgumentException("Choose a supported sale button.");
            if(type.equals("TWO_FOR")&&(bundlePrice==null||bundlePrice.signum()<0))
                throw new IllegalArgumentException("Enter the price for two products.");
        }
        public java.math.BigDecimal total(java.math.BigDecimal unit,int quantity) {
            var q=java.math.BigDecimal.valueOf(quantity);
            return switch(type) {
                case "BUY_GET" -> unit.multiply(java.math.BigDecimal.valueOf(quantity-(long)freeQuantity*(quantity/(buyQuantity+freeQuantity))));
                case "BOGO_FREE" -> unit.multiply(java.math.BigDecimal.valueOf(quantity-quantity/2));
                case "BUY_TWO_GET_ONE" -> unit.multiply(java.math.BigDecimal.valueOf(quantity-quantity/3));
                case "BUY_ONE_GET_TWO" -> unit.multiply(java.math.BigDecimal.valueOf(quantity-2*(quantity/3)));
                case "BOGO_HALF" -> unit.multiply(q).subtract(unit.multiply(new java.math.BigDecimal("0.5")).multiply(java.math.BigDecimal.valueOf(quantity/2)));
                case "TWO_FOR" -> bundlePrice.multiply(java.math.BigDecimal.valueOf(quantity/2)).add(unit.multiply(java.math.BigDecimal.valueOf(quantity%2)));
                default -> throw new IllegalStateException();
            };
        }
    }
    public record PlanningSource(String text,String mediaType,String contentBase64,List<PlanningSource> earlier,String purchaseChannel,List<UUID> advertisedDealIds) implements java.io.Serializable {
        public PlanningSource(String text,String mediaType,String contentBase64,List<PlanningSource> earlier,String channel){this(text,mediaType,contentBase64,earlier,channel,List.of());}
        public PlanningSource(String text,String mediaType,String contentBase64,List<PlanningSource> earlier){this(text,mediaType,contentBase64,earlier,null);}
        public PlanningSource(String text,String mediaType,String contentBase64){this(text,mediaType,contentBase64,List.of());}
        public PlanningSource {
            advertisedDealIds=advertisedDealIds==null?List.of():List.copyOf(advertisedDealIds);
            if(advertisedDealIds.size()>100)throw new IllegalArgumentException("Too many advertisement links.");
            if(purchaseChannel!=null&&!java.util.Set.of("ONLINE","IN_STORE","UNKNOWN").contains(purchaseChannel))throw new IllegalArgumentException("Unknown purchase channel.");
            earlier=earlier==null?List.of():List.copyOf(earlier);
            if(earlier.size()>20)throw new IllegalArgumentException("Use at most 20 source attachments per plan.");
            text=text==null?"":text;
            if(text.length()>150000)throw new IllegalArgumentException("Paste at most 150,000 characters.");
            if(contentBase64!=null) {
                if(!java.util.Set.of("image/jpeg","image/png","image/webp").contains(mediaType==null?"":mediaType))throw new IllegalArgumentException("Attach a JPEG, PNG or WebP screenshot.");
                if(java.util.Base64.getDecoder().decode(contentBase64).length>5*1024*1024)throw new IllegalArgumentException("Attach at most 5 MiB.");
            }
        }
    }
    public record ScenarioCoupon(UUID id,String description,java.math.BigDecimal amount,int requiredQuantity,List<UUID> lineIds,UUID savedCouponId,String kind,boolean wholeReceipt,boolean pendingVerification) implements java.io.Serializable {
        public ScenarioCoupon(UUID id,String description,java.math.BigDecimal amount,int requiredQuantity,List<UUID> lines,UUID savedId,String kind,boolean wholeReceipt){this(id,description,amount,requiredQuantity,lines,savedId,kind,wholeReceipt,false);}
        public ScenarioCoupon(UUID id,String description,java.math.BigDecimal amount,int requiredQuantity,List<UUID> lines,UUID savedId,String kind){this(id,description,amount,requiredQuantity,lines,savedId,kind,false);}
        public ScenarioCoupon(UUID id,String description,java.math.BigDecimal amount,int requiredQuantity,List<UUID> lines,UUID savedId){this(id,description,amount,requiredQuantity,lines,savedId,"MANUFACTURER");}
        public ScenarioCoupon {kind=kind==null?"MANUFACTURER":kind;if(!java.util.Set.of("MANUFACTURER","STORE_COUPON","SALE_PROMOTION").contains(kind)&&!(pendingVerification&&kind.equals("UNKNOWN")))throw new IllegalArgumentException("Choose manufacturer or store coupon.");Objects.requireNonNull(id);requireText(description,"Coupon description");if(amount==null||amount.signum()<0||!pendingVerification&&amount.signum()==0||requiredQuantity<1||!wholeReceipt&&lineIds.isEmpty())throw new IllegalArgumentException("Coupon needs a positive amount, required quantity and products.");lineIds=List.copyOf(lineIds);}
    }
    public record ScenarioPromotion(UUID id,String description,String rewardType,java.math.BigDecimal rewardAmount,int requiredQuantity,java.math.BigDecimal requiredSpend,String spendBasis,boolean allowsWalgreensCash,List<UUID> lineIds,boolean wholeReceipt,Boolean allowsWalmartCash, boolean restrictSameRegisterReward, String prohibitedRegisterRewardProgram, String originProduct, String originBrand, String originProgram, String originPromotion,Boolean paymentRulesReviewed,String giftCardRule,Boolean rollingAllowed,String earnedRegisterRewardType,Boolean allowsRegisterRewards,Boolean allowsEcb) implements java.io.Serializable {
        public ScenarioPromotion(UUID id,String description,String rewardType,java.math.BigDecimal rewardAmount,int requiredQuantity,java.math.BigDecimal requiredSpend,String spendBasis,boolean allowsWalgreensCash,List<UUID> lineIds,boolean wholeReceipt,Boolean allowsWalmartCash, boolean restrictSameRegisterReward, String prohibitedRegisterRewardProgram, String originProduct, String originBrand, String originProgram, String originPromotion,Boolean paymentRulesReviewed){this(id,description,rewardType,rewardAmount,requiredQuantity,requiredSpend,spendBasis,allowsWalgreensCash,lineIds,wholeReceipt,allowsWalmartCash,restrictSameRegisterReward,prohibitedRegisterRewardProgram,originProduct,originBrand,originProgram,originPromotion,paymentRulesReviewed,null,null,null,null,null);}
        public ScenarioPromotion(UUID id,String description,String type,java.math.BigDecimal amount,int quantity,java.math.BigDecimal spend,String basis,boolean allows,List<UUID> lines,boolean whole,Boolean walmart,boolean same,String prohibited,String product,String brand,String program,String promotion){this(id,description,type,amount,quantity,spend,basis,allows,lines,whole,walmart,same,prohibited,product,brand,program,promotion,true);}
        public ScenarioPromotion(UUID id,String description,String type,java.math.BigDecimal amount,int quantity,java.math.BigDecimal spend,String basis,boolean allows,List<UUID> lines,boolean whole,Boolean walmart){this(id,description,type,amount,quantity,spend,basis,allows,lines,whole,walmart,false,null,null,null,null,null,null);}
        public ScenarioPromotion(UUID id,String description,String type,java.math.BigDecimal amount,int quantity,java.math.BigDecimal spend,String basis,boolean allows,List<UUID> lines,boolean whole){this(id,description,type,amount,quantity,spend,basis,allows,lines,whole,true);}
        public ScenarioPromotion(UUID id,String description,String rewardType,java.math.BigDecimal amount,int quantity,java.math.BigDecimal spend,String basis,boolean allows,List<UUID> lines){this(id,description,rewardType,amount,quantity,spend,basis,allows,lines,false);}
        public ScenarioPromotion {if(giftCardRule!=null&&!java.util.Set.of("PROHIBITED","COUNTS","REDUCES","UNKNOWN").contains(giftCardRule))throw new IllegalArgumentException("Record the gift-card payment rule.");if(earnedRegisterRewardType!=null&&!java.util.Set.of("MANUFACTURER","STORE","UNKNOWN").contains(earnedRegisterRewardType))throw new IllegalArgumentException("Record the earned RR type.");allowsWalmartCash=allowsWalmartCash==null?true:allowsWalmartCash;Objects.requireNonNull(id);requireText(description,"Promotion description");if(!java.util.Set.of("REGISTER_REWARD","WALGREENS_CASH","ECB","WALMART_CASH","CASH","FETCH","PG_BRAND","IBOTTA","PAYPAL","VENMO","SWAGBUCKS").contains(rewardType)||rewardAmount==null||rewardAmount.signum()<0||requiredQuantity<0||requiredSpend==null||requiredSpend.signum()<0||!wholeReceipt&&lineIds.isEmpty()||!java.util.Set.of("BEFORE_COUPONS","AFTER_COUPONS","AFTER_REWARDS_USED","BEFORE_COUPONS_AFTER_STORE_RR","AFTER_COUPONS_AFTER_STORE_RR").contains(spendBasis))throw new IllegalArgumentException("Complete the promotion amount, requirements and qualifying products.");lineIds=List.copyOf(lineIds);}
    }
    public record ScenarioPayment(UUID id,String type,java.math.BigDecimal amount,String notes,String registerRewardType,UUID rewardSourceId, String originProduct, String originBrand, String originProgram, String originPromotion,PaymentEligibility.Rule eligibility) implements java.io.Serializable {
        public ScenarioPayment(UUID id,String type,java.math.BigDecimal amount,String notes,String rrType,UUID source,String product,String brand,String program,String promotion){this(id,type,amount,notes,rrType,source,product,brand,program,promotion,null);}
        public ScenarioPayment(UUID id,String type,java.math.BigDecimal amount,String notes,String rrType,UUID source){this(id,type,amount,notes,rrType,source,null,null,null,null);}
        public ScenarioPayment(UUID id,String type,java.math.BigDecimal amount,String notes){this(id,type,amount,notes,null,null);}
        public ScenarioPayment { if(registerRewardType!=null&&!java.util.Set.of("MANUFACTURER","STORE","UNKNOWN").contains(registerRewardType))throw new IllegalArgumentException("Unknown Register Reward type."); Objects.requireNonNull(id);if(!java.util.Set.of("WALGREENS_CASH","WALMART_CASH","REGISTER_REWARD","ECB","GIFT_CARD","CASH","CARD").contains(type)||amount==null||amount.signum()<0)throw new IllegalArgumentException("Choose a payment type and nonnegative amount.");notes=notes==null?"":notes; }
    }
    public record Scenario(List<Line> items,List<ScenarioCoupon> coupons,List<ScenarioPromotion> promotions,java.math.BigDecimal tax,java.math.BigDecimal walgreensCashUsed,java.math.BigDecimal registerRewardsUsed,java.math.BigDecimal giftCardUsed,java.math.BigDecimal manualOop,String paymentMethod,String fundingNotes,java.math.BigDecimal taxRate,List<ScenarioPayment> payments,PlanningSource source,java.math.BigDecimal walgreensCashIncrement,java.math.BigDecimal walgreensCashLimit) implements java.io.Serializable {
        public Scenario(List<Line> items,List<ScenarioCoupon> coupons,List<ScenarioPromotion> promotions,java.math.BigDecimal tax,java.math.BigDecimal wc,java.math.BigDecimal rr,java.math.BigDecimal gift,java.math.BigDecimal manual,String method,String notes,java.math.BigDecimal rate,List<ScenarioPayment> payments,PlanningSource source){this(items,coupons,promotions,tax,wc,rr,gift,manual,method,notes,rate,payments,source,null,null);}
        public Scenario(List<Line> items,List<ScenarioCoupon> coupons,List<ScenarioPromotion> promotions,java.math.BigDecimal tax,java.math.BigDecimal wc,java.math.BigDecimal rr,java.math.BigDecimal gift,java.math.BigDecimal manual,String method,String notes,java.math.BigDecimal rate,List<ScenarioPayment> payments){this(items,coupons,promotions,tax,wc,rr,gift,manual,method,notes,rate,payments,null);}
        public Scenario(List<Line> items,List<ScenarioCoupon> coupons,List<ScenarioPromotion> promotions,java.math.BigDecimal tax,java.math.BigDecimal wc,java.math.BigDecimal rr,java.math.BigDecimal gift,java.math.BigDecimal manual,String method,String notes,java.math.BigDecimal rate){this(items,coupons,promotions,tax,wc,rr,gift,manual,method,notes,rate,null);}
        public Scenario(List<Line> items,List<ScenarioCoupon> coupons,List<ScenarioPromotion> promotions,java.math.BigDecimal tax,java.math.BigDecimal wc,java.math.BigDecimal rr,java.math.BigDecimal gift,java.math.BigDecimal manual,String method,String notes){this(items,coupons,promotions,tax,wc,rr,gift,manual,method,notes,null);}
        public Scenario {
            if(walgreensCashIncrement!=null&&walgreensCashIncrement.signum()<=0||walgreensCashLimit!=null&&walgreensCashLimit.signum()<0)throw new IllegalArgumentException("Enter a positive redemption increment and nonnegative limit.");
            if(payments!=null){payments=List.copyOf(payments);if(payments.size()>100||payments.stream().map(ScenarioPayment::id).distinct().count()!=payments.size())throw new IllegalArgumentException("Enter at most 100 distinct payment rows.");}
            if(taxRate!=null&&(taxRate.signum()<0||taxRate.compareTo(new java.math.BigDecimal("100"))>0))throw new IllegalArgumentException("Tax rate must be between 0 and 100 percent.");
            fundingNotes=fundingNotes==null?"":fundingNotes;if(fundingNotes.length()>2000)throw new IllegalArgumentException("Funding notes too long.");
            items=List.copyOf(items);coupons=List.copyOf(coupons);promotions=List.copyOf(promotions);
            if(items.isEmpty()||items.size()>100||coupons.size()>100||promotions.size()>100)throw new IllegalArgumentException("A scenario needs 1–100 products and at most 100 coupons/promotions.");
            for(var n:List.of(tax,walgreensCashUsed,registerRewardsUsed,giftCardUsed))if(n.signum()<0)throw new IllegalArgumentException("Tax and rewards used cannot be negative.");
            if(manualOop!=null&&manualOop.signum()<0||!java.util.Set.of("CASH","CARD").contains(paymentMethod))throw new IllegalArgumentException("Choose cash/card and a nonnegative manual OOP.");
            var sources=new HashSet<UUID>();if(payments!=null)for(var payment:payments)if(payment.rewardSourceId()!=null&&!sources.add(payment.rewardSourceId()))throw new IllegalArgumentException("Select each recorded reward once.");
            var ids=new HashSet<UUID>();for(var line:items)if(!ids.add(line.id()))throw new IllegalArgumentException("Duplicate product ID.");
            var saleGroups=new HashSet<UUID>();
            for(var owner:items) {
                var rule=owner.priceRule();if(rule==null||rule.lineIds().isEmpty())continue;
                if(!rule.lineIds().contains(owner.id())||!ids.containsAll(rule.lineIds())||new HashSet<>(rule.lineIds()).size()!=rule.lineIds().size())throw new IllegalArgumentException("Sale groups must contain distinct products from this plan, including their owner.");
                if(rule.lineIds().size()<2)continue;
                if(rule.type().equals("TWO_FOR"))throw new IllegalArgumentException("Use 2-for pricing on one product line.");
                for(var line:items)if(rule.lineIds().contains(line.id())&&(!saleGroups.add(line.id())||!line.id().equals(owner.id())&&line.priceRule()!=null))throw new IllegalArgumentException("A product cannot belong to overlapping sale rules.");
            }
            var events=new HashSet<UUID>();var saved=new HashSet<UUID>();for(var c:coupons){if(!events.add(c.id())||c.savedCouponId()!=null&&!saved.add(c.savedCouponId()))throw new IllegalArgumentException("Enter each coupon once.");if(!ids.containsAll(c.lineIds())||new HashSet<>(c.lineIds()).size()!=c.lineIds().size())throw new IllegalArgumentException("Coupon products must belong to this scenario.");}
            for(var promotion:promotions){if(!events.add(promotion.id())||!ids.containsAll(promotion.lineIds())||new HashSet<>(promotion.lineIds()).size()!=promotion.lineIds().size())throw new IllegalArgumentException("Promotion IDs must be distinct and products must belong to this scenario.");}
        }
        private String paymentRuleStatus(ScenarioPromotion promotion,java.math.BigDecimal wc,java.math.BigDecimal walmart,java.math.BigDecimal rr,java.math.BigDecimal gift,java.math.BigDecimal ecb,boolean walgreens) {
            if(Boolean.FALSE.equals(promotion.allowsRegisterRewards())&&rr.signum()>0||Boolean.FALSE.equals(promotion.allowsEcb())&&ecb.signum()>0||"PROHIBITED".equals(promotion.giftCardRule())&&gift.signum()>0)return "Not Met";
            if(gift.signum()>0&&promotion.requiredSpend().signum()>0&&(promotion.giftCardRule()==null||promotion.giftCardRule().equals("UNKNOWN")))return "Needs Review";
            if(!promotion.allowsWalgreensCash()&&wc.signum()>0||!promotion.allowsWalmartCash()&&walmart.signum()>0
                    ||walgreensCashLimit!=null&&wc.compareTo(walgreensCashLimit)>0
                    ||walgreensCashIncrement!=null&&wc.remainder(walgreensCashIncrement).signum()!=0)return "Not Met";
            String status=registerRewardPaymentStatus(promotion,rr,walgreens);
            if(status.equals("Not Met"))return status;
            if(walgreens&&!Boolean.TRUE.equals(promotion.paymentRulesReviewed())
                    &&(rr.signum()>0&&promotion.rewardType().equals("REGISTER_REWARD")&&!promotion.restrictSameRegisterReward()&&!known(promotion.prohibitedRegisterRewardProgram())))return "Needs Review";
            if(status.equals("Met")&&promotion.spendBasis().endsWith("AFTER_STORE_RR")&&promotion.requiredSpend().signum()>0&&rr.signum()>0
                    &&(payments==null||payments.stream().anyMatch(v->v.type().equals("REGISTER_REWARD")&&v.amount().signum()>0
                        &&(v.registerRewardType()==null||v.registerRewardType().equals("UNKNOWN")))))return "Needs Review";
            return status;
        }
        private String registerRewardPaymentStatus(ScenarioPromotion promotion,java.math.BigDecimal used,boolean walgreens) {
            boolean sameBrandRule=walgreens&&promotion.rewardType().equals("REGISTER_REWARD");
            if(used.signum()==0||!sameBrandRule&&!promotion.restrictSameRegisterReward()&&!known(promotion.prohibitedRegisterRewardProgram()))return "Met";
            if(payments==null)return "Needs Review";
            boolean review=false;
            for(var payment:payments) {
                if(!payment.type().equals("REGISTER_REWARD")||payment.amount().signum()==0)continue;
                if(sameBrandRule){
                    String earnedBrand=known(promotion.originBrand())?promotion.originBrand():promotion.description().trim().split("\\s+")[0];
                    String paidBrand=known(payment.originBrand())?payment.originBrand():known(payment.originProduct())?payment.originProduct().trim().split("\\s+")[0]:null;
                    if(known(earnedBrand)&&known(paidBrand)&&brandKey(earnedBrand).equals(brandKey(paidBrand)))return "Not Met";
                    if(!known(earnedBrand)||!known(paidBrand))review=true;
                }
                if(known(promotion.prohibitedRegisterRewardProgram())) {
                    if(!known(payment.originProgram()))review=true;
                    else if(same(promotion.prohibitedRegisterRewardProgram(),payment.originProgram()))return "Not Met";
                }
                if(promotion.restrictSameRegisterReward()) {
                    if(same(promotion.originProduct(),payment.originProduct())||same(promotion.originPromotion(),payment.originPromotion()))return "Not Met";
                    // Both comparisons must be known to establish an unrelated origin.
                    if(!known(promotion.originProduct())||!known(payment.originProduct())||!known(promotion.originPromotion())||!known(payment.originPromotion()))review=true;
                }
            }
            return review?"Needs Review":"Met";
        }
        private boolean assignManufacturerCoupon(ScenarioCoupon coupon,java.util.Map<UUID,List<ScenarioCoupon>> assigned,java.util.Map<UUID,Integer> capacities,java.util.Set<UUID> visited){
            var eligible=coupon.wholeReceipt()?items.stream().map(Line::id).toList():coupon.lineIds();
            for(var id:eligible)if(visited.add(id)){
                var occupants=assigned.computeIfAbsent(id,key->new java.util.ArrayList<>());
                if(occupants.size()<capacities.get(id)){occupants.add(coupon);return true;}
                for(var previous:new java.util.ArrayList<>(occupants))if(assignManufacturerCoupon(previous,assigned,capacities,visited)){
                    occupants.remove(previous);occupants.add(coupon);return true;
                }
            }
            return false;
        }
        /** Supplied redemption denominations; additional entered increment/limit may narrow these. */
        public static List<java.math.BigDecimal> walgreensCashDenominations(){return java.util.stream.Stream.of("1","2","3","4","5","10","20","50").map(java.math.BigDecimal::new).toList();}
        public boolean allowsWalgreensCashAmount(java.math.BigDecimal amount){return amount.signum()==0||walgreensCashDenominations().stream().anyMatch(v->v.compareTo(amount)==0)
                &&(walgreensCashLimit==null||amount.compareTo(walgreensCashLimit)<=0)&&(walgreensCashIncrement==null||amount.remainder(walgreensCashIncrement).signum()==0);}
        private static String brandKey(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}","").replaceAll("[^\\p{L}\\p{N}]","").toLowerCase(java.util.Locale.ROOT);}
        private static boolean known(String value){return value!=null&&!value.isBlank();}
        private static boolean same(String a,String b){return known(a)&&known(b)&&a.trim().equalsIgnoreCase(b.trim());}
        public java.util.Map<String,Object> estimate(){return estimate(null);}
        private static boolean nativeRewardAllowed(String retailer,String type){
            if(retailer==null)return true;String store=retailer.trim().toLowerCase(java.util.Locale.ROOT);
            if(store.matches("^walgreens(?:\\s.*|#.*|$)"))return !java.util.Set.of("ECB","WALMART_CASH").contains(type);
            if(store.matches("^cvs(?:\\s.*|#.*|$)"))return !java.util.Set.of("REGISTER_REWARD","WALGREENS_CASH","WALMART_CASH").contains(type);
            if(store.matches("^walmart(?:\\s.*|#.*|$)"))return !java.util.Set.of("REGISTER_REWARD","WALGREENS_CASH","ECB").contains(type);
            return true;
        }
        public java.util.Map<String,Object> estimate(String retailer){
            var zero=java.math.BigDecimal.ZERO;
            var walgreensCashUsed=this.walgreensCashUsed;var registerRewardsUsed=this.registerRewardsUsed;var giftCardUsed=this.giftCardUsed;var manualOop=this.manualOop;var ecbUsed=zero;var walmartCashUsed=zero;
            if(payments!=null){walgreensCashUsed=zero;registerRewardsUsed=zero;giftCardUsed=zero;var paid=zero;boolean entered=false;for(var p:payments){switch(p.type()){case "WALMART_CASH"->walmartCashUsed=walmartCashUsed.add(p.amount());case "WALGREENS_CASH"->walgreensCashUsed=walgreensCashUsed.add(p.amount());case "REGISTER_REWARD"->registerRewardsUsed=registerRewardsUsed.add(p.amount());case "ECB"->ecbUsed=ecbUsed.add(p.amount());case "GIFT_CARD"->giftCardUsed=giftCardUsed.add(p.amount());case "CASH","CARD"->{paid=paid.add(p.amount());entered=true;}default->throw new IllegalArgumentException("Unknown payment type.");}}manualOop=entered?paid:null;}
            boolean walgreens=retailer!=null&&retailer.trim().matches("(?i)^walgreens(?:\\s.*|#.*|$)");
            var sale=new java.util.LinkedHashMap<UUID,java.math.BigDecimal>();var discounts=new java.util.LinkedHashMap<UUID,java.math.BigDecimal>();var regular=zero;
            for(var l:items){var unit=l.sale()==null?l.regular():l.sale();if(l.regular()!=null)regular=regular.add(l.regular().multiply(java.math.BigDecimal.valueOf(l.quantity())));sale.put(l.id(),l.priceRule()==null||l.priceRule().lineIds().size()>1?unit.multiply(java.math.BigDecimal.valueOf(l.quantity())):l.priceRule().total(unit,l.quantity()));discounts.put(l.id(),zero);}
            var grouped=new HashSet<UUID>();
            for(var owner:items) {
                var rule=owner.priceRule();if(rule==null||rule.lineIds().size()<2)continue;
                if(rule.type().equals("TWO_FOR"))throw new IllegalArgumentException("Use 2-for pricing on one product line; mixed groups support buy/get discounts.");
                if(!rule.lineIds().contains(owner.id()))throw new IllegalArgumentException("Include the product that owns the sale group.");
                var eligible=items.stream().filter(l->rule.lineIds().contains(l.id())).sorted(java.util.Comparator.comparing(l->l.sale()==null?l.regular():l.sale())).toList();
                if(eligible.size()!=rule.lineIds().size())throw new IllegalArgumentException("Every sale-group product must exist in this plan.");
                long count=eligible.stream().mapToLong(Line::quantity).sum(),discounted=rule.type().equals("BUY_GET")?(long)rule.freeQuantity()*(count/(rule.buyQuantity()+rule.freeQuantity())):rule.type().equals("BUY_ONE_GET_TWO")?2*(count/3):rule.type().equals("BUY_TWO_GET_ONE")?count/3:count/2;
                for(var l:eligible) {
                    if(!grouped.add(l.id())||!l.id().equals(owner.id())&&l.priceRule()!=null)throw new IllegalArgumentException("A product cannot belong to overlapping sale rules.");
                    long take=Math.min(discounted,l.quantity());discounted-=take;
                    var unit=l.sale()==null?l.regular():l.sale();var discount=unit.multiply(java.math.BigDecimal.valueOf(take));
                    if(rule.type().equals("BOGO_HALF"))discount=discount.multiply(new java.math.BigDecimal("0.5"));
                    sale.put(l.id(),sale.get(l.id()).subtract(discount));
                }
            }
            var couponRows=new java.util.ArrayList<java.util.Map<String,Object>>();var couponTotal=zero;
            for(var c:coupons){var couponLines=c.wholeReceipt()?items.stream().map(Line::id).toList():c.lineIds();long qty=items.stream().filter(l->couponLines.contains(l.id())).mapToLong(Line::quantity).sum();boolean met=qty>=c.requiredQuantity();var eligible=couponLines.stream().map(sale::get).reduce(zero,java.math.BigDecimal::add);var available=couponLines.stream().map(id->sale.get(id).subtract(discounts.get(id))).reduce(zero,java.math.BigDecimal::add);boolean fits=c.amount().compareTo(available)<=0;boolean applied=(!c.pendingVerification()||c.savedCouponId()!=null)&&c.amount().signum()>0&&met&&fits;
                couponRows.add(java.util.Map.of("id",c.id(),"description",c.description(),"qualifyingQuantity",qty,"requiredQuantity",c.requiredQuantity(),"quantityMet",met,"applied",applied,"amount",applied?c.amount():zero,"reason",!met?"Required quantity not met":!fits?"Coupon exceeds remaining merchandise":c.amount().signum()==0?"Enter the planned discount amount":c.pendingVerification()?"Included in planning estimate; actual coupon use is not confirmed":"Entered coupon applied"));
                if(applied){couponTotal=couponTotal.add(c.amount());var remaining=c.amount();for(int i=0;i<couponLines.size();i++){var id=couponLines.get(i);var part=i==couponLines.size()-1?remaining:c.amount().multiply(sale.get(id).subtract(discounts.get(id))).divide(available,2,java.math.RoundingMode.DOWN);discounts.put(id,discounts.get(id).add(part));remaining=remaining.subtract(part);}}
            }
            var saleTotal=sale.values().stream().reduce(zero,java.math.BigDecimal::add);var merchandise=saleTotal.subtract(couponTotal);
            // Keep explicitly entered but unverified coupon arithmetic visible as a proposal.
            // It never feeds verified eligibility, reward funding, or optimizer decisions.
            var proposedCouponTotal=coupons.stream().filter(ScenarioCoupon::pendingVerification).map(ScenarioCoupon::amount).reduce(zero,java.math.BigDecimal::add).min(merchandise);
            var proposedMerchandise=merchandise.subtract(proposedCouponTotal).max(zero);
            var calculatedTax=taxRate==null?tax:merchandise.multiply(taxRate).movePointLeft(2).setScale(2,java.math.RoundingMode.HALF_UP);var rawOop=merchandise.subtract(registerRewardsUsed).subtract(ecbUsed).add(calculatedTax).subtract(walgreensCashUsed).subtract(walmartCashUsed).subtract(giftCardUsed);var oop=rawOop.max(zero);var selected=manualOop==null?oop:manualOop;var potential=zero;var receiptLevelRewards=zero;var cashRebates=zero;var promoRows=new java.util.ArrayList<java.util.Map<String,Object>>();var perItemRewards=new java.util.HashMap<UUID,java.math.BigDecimal>();
            var fundingIssues=new java.util.ArrayList<String>();
            String fundingStatus="Met";
            if(walgreens){
                if(ecbUsed.signum()>0||walmartCashUsed.signum()>0)fundingIssues.add("Walmart Cash and CVS ExtraBucks cannot be used at Walgreens.");
                if(!allowsWalgreensCashAmount(walgreensCashUsed))fundingIssues.add("Walgreens Cash must use an allowed denomination within the entered increment and limit.");
                if(registerRewardsUsed.add(walgreensCashUsed).compareTo(merchandise)>0)fundingIssues.add("Reward funding exceeds eligible merchandise; tax cannot increase redemption eligibility.");
                // Each applied manufacturer coupon and each manufacturer RR consumes one eligible item slot.
                // Allocate coupons to actual linked quantities, avoiding double use of the same item slot.
                var slots=new java.util.HashMap<UUID,Integer>();for(var item:items)slots.put(item.id(),item.quantity());
                var applied=new HashSet<UUID>();for(var row:couponRows)if(Boolean.TRUE.equals(row.get("applied")))applied.add((UUID)row.get("id"));
                var manufacturers=coupons.stream().filter(c->c.kind().equals("MANUFACTURER")&&applied.contains(c.id())).toList();
                var assigned=new java.util.HashMap<UUID,List<ScenarioCoupon>>();boolean couponFits=true;
                for(var coupon:manufacturers)if(!assignManufacturerCoupon(coupon,assigned,slots,new HashSet<>()))couponFits=false;
                long rrCount=payments==null?0:payments.stream().filter(v->v.type().equals("REGISTER_REWARD")&&v.amount().signum()>0&&"MANUFACTURER".equals(v.registerRewardType())).count();
                boolean unknownType=registerRewardsUsed.signum()>0&&(payments==null||payments.stream().anyMatch(v->v.type().equals("REGISTER_REWARD")&&v.amount().signum()>0&&(v.registerRewardType()==null||v.registerRewardType().equals("UNKNOWN"))));
                if(!couponFits||rrCount>slots.values().stream().mapToLong(Integer::longValue).sum()-manufacturers.size())fundingIssues.add("Manufacturer coupons and Register Rewards exceed eligible item quantities.");
                if(!fundingIssues.isEmpty())fundingStatus="Not Met";
                if(unknownType){if(fundingStatus.equals("Met"))fundingStatus="Needs Review";fundingIssues.add("Register Reward type is unknown; coupon-to-item eligibility needs review.");}
            }
            if(retailer!=null&&retailer.trim().matches("(?i)^(cvs|walmart)(?:\\s.*|#.*|$)")){
                boolean cvs=retailer.trim().toLowerCase(java.util.Locale.ROOT).startsWith("cvs");
                if(registerRewardsUsed.signum()>0||walgreensCashUsed.signum()>0||cvs&&walmartCashUsed.signum()>0||!cvs&&ecbUsed.signum()>0){fundingStatus="Not Met";fundingIssues.add("Funding belongs to another retailer.");}
            }
            if(retailer!=null&&giftCardUsed.signum()>0){
                var identified=payments==null?zero:payments.stream().filter(v->v.type().equals("GIFT_CARD")&&v.eligibility()!=null).map(ScenarioPayment::amount).reduce(zero,java.math.BigDecimal::add);
                if(identified.compareTo(giftCardUsed)<0){if(fundingStatus.equals("Met"))fundingStatus="Needs Review";fundingIssues.add("Select the recorded gift card to verify its acceptance and balance.");}
                if(payments!=null){var uses=new java.util.HashMap<UUID,java.math.BigDecimal>();for(var p:payments)if(p.type().equals("GIFT_CARD")&&p.eligibility()!=null){var rule=p.eligibility();uses.merge(rule.instrumentId(),p.amount(),java.math.BigDecimal::add);if(rule.available()!=null&&uses.get(rule.instrumentId()).compareTo(rule.available())>0){fundingStatus="Not Met";fundingIssues.add("Combined uses exceed this gift card's available balance.");}}}
            }
            if(retailer!=null&&payments!=null)for(var payment:payments)if(payment.amount().signum()>0){
                var rule=payment.eligibility();String status=PaymentEligibility.check(retailer,payment.type(),rule,payment.amount(),rule==null?null:rule.evaluatedOn());
                if(!status.equals("Met")){if(status.equals("Not Met")||fundingStatus.equals("Met"))fundingStatus=status;fundingIssues.add(payment.type()+": "+status+" — check the recorded instrument, retailer acceptance, expiration and available balance.");}
            }
            if(rawOop.signum()<0){fundingStatus="Not Met";fundingIssues.add("Funding exceeds checkout amount.");}
            for(var p:promotions){var promotionLines=p.wholeReceipt()?items.stream().map(Line::id).toList():p.lineIds();long qty=items.stream().filter(l->promotionLines.contains(l.id())).mapToLong(Line::quantity).sum();var spend=promotionLines.stream().map(sale::get).reduce(zero,java.math.BigDecimal::add);if(!p.spendBasis().startsWith("BEFORE_COUPONS"))spend=spend.subtract(promotionLines.stream().map(discounts::get).reduce(zero,java.math.BigDecimal::add));if(p.spendBasis().endsWith("AFTER_STORE_RR")&&payments!=null)spend=spend.subtract(payments.stream().filter(v->v.type().equals("REGISTER_REWARD")&&"STORE".equals(v.registerRewardType())).map(ScenarioPayment::amount).reduce(zero,java.math.BigDecimal::add)).max(zero);if(p.spendBasis().equals("AFTER_REWARDS_USED"))spend=spend.subtract(registerRewardsUsed).subtract(ecbUsed).subtract(walgreensCashUsed).subtract(walmartCashUsed).max(zero);
                if("REDUCES".equals(p.giftCardRule()))spend=spend.subtract(giftCardUsed).max(zero);
                boolean q=qty>=p.requiredQuantity(),s=spend.compareTo(p.requiredSpend())>=0,w=(p.allowsWalgreensCash()||walgreensCashUsed.signum()==0)&&(p.allowsWalmartCash()||walmartCashUsed.signum()==0),met=q&&s&&w;String paymentStatus=!nativeRewardAllowed(retailer,p.rewardType())?"Not Met":paymentRuleStatus(p,walgreensCashUsed,walmartCashUsed,registerRewardsUsed,giftCardUsed,ecbUsed,walgreens);if(!fundingStatus.equals("Met")&&!paymentStatus.equals("Not Met"))paymentStatus=fundingStatus;met=met&&paymentStatus.equals("Met");var usd=p.rewardType().equals("FETCH")?p.rewardAmount().movePointLeft(3):p.rewardType().equals("PG_BRAND")?p.rewardAmount().divide(new java.math.BigDecimal("150"), java.math.MathContext.DECIMAL128):p.rewardAmount();var amount=met?usd:zero;potential=potential.add(amount);if(java.util.Set.of("CASH","IBOTTA","PAYPAL","VENMO","SWAGBUCKS").contains(p.rewardType()))cashRebates=cashRebates.add(amount);
                promoRows.add(java.util.Map.ofEntries(java.util.Map.entry("id",p.id()),java.util.Map.entry("description",p.description()),java.util.Map.entry("rewardType",p.rewardType()),java.util.Map.entry("quantityMet",q),java.util.Map.entry("spendMet",s),java.util.Map.entry("walgreensCashAllowed",w),java.util.Map.entry("paymentRuleStatus",paymentStatus),java.util.Map.entry("qualifyingQuantity",qty),java.util.Map.entry("qualifyingSpend",spend),java.util.Map.entry("eligible",met),java.util.Map.entry("potentialReward",amount),java.util.Map.entry("usdValue",usd),java.util.Map.entry("points",java.util.Set.of("FETCH","PG_BRAND").contains(p.rewardType())?p.rewardAmount():zero)));
                // Receipt-wide rewards belong to the receipt, not its first product.
                if(p.wholeReceipt())receiptLevelRewards=receiptLevelRewards.add(amount);else perItemRewards.merge(promotionLines.getFirst(),amount,java.math.BigDecimal::add);
            }
            var memberTotal=zero;var memberRows=new java.util.ArrayList<java.util.Map<String,Object>>();
            if(fundingStatus.equals("Met")&&promotions.stream().noneMatch(p->p.rewardType().equals("WALGREENS_CASH")&&p.description().toLowerCase(java.util.Locale.ROOT).matches("(?:mywalgreens )?(?:members? rewards?|base rewards?)(?: for purchase)?"))&&retailer!=null&&retailer.trim().matches("(?i)^walgreens(?:\\s.*|#.*|$)")) {
                var eligibleFunding=merchandise.subtract(registerRewardsUsed).subtract(walgreensCashUsed).subtract(ecbUsed).subtract(giftCardUsed).max(zero);
                for(var line:items) {
                    int rate=line.memberRewardRate()!=null?line.memberRewardRate():line.name().matches("(?i)^(?:walg|walgreens)\\b.*")?5:1;
                    var basis=sale.get(line.id()).subtract(discounts.get(line.id())).max(zero);
                    if(merchandise.signum()>0)basis=basis.multiply(eligibleFunding).divide(merchandise,java.math.MathContext.DECIMAL128);else basis=zero;
                    var earned=basis.multiply(java.math.BigDecimal.valueOf(rate)).movePointLeft(2).setScale(2,java.math.RoundingMode.DOWN);
                    memberTotal=memberTotal.add(earned);
                    memberRows.add(java.util.Map.of("id",line.id(),"rate",rate,"basis",basis,"amount",earned));
                }
                memberTotal=memberTotal.setScale(2,java.math.RoundingMode.DOWN);potential=potential.add(memberTotal);receiptLevelRewards=receiptLevelRewards.add(memberTotal);
            }
            var itemRows=new java.util.ArrayList<java.util.Map<String,Object>>();for(var l:items)itemRows.add(java.util.Map.of("id",l.id(),"name",l.name(),"totalPrice",sale.get(l.id()),"saleReduction",(l.regular()==null?l.sale():l.regular()).multiply(java.math.BigDecimal.valueOf(l.quantity())).subtract(sale.get(l.id())),"couponsApplied",discounts.get(l.id()),"itemOopBeforeTaxAndFunding",sale.get(l.id()).subtract(discounts.get(l.id())),"possibleRewards",perItemRewards.getOrDefault(l.id(),zero)));
            // Sale reductions are already included in merchandise, so these rows report savings only.
            var storePromotions=new java.util.ArrayList<java.util.Map<String,Object>>();var storePromotionTotal=zero;
            for(var item:itemRows) {
                var reduction=(java.math.BigDecimal)item.get("saleReduction");
                if(reduction.signum()>0){storePromotionTotal=storePromotionTotal.add(reduction);storePromotions.add(java.util.Map.of("id",item.get("id"),"description",item.get("name")+" Store Promotion","kind","SALE_PROMOTION","amount",reduction));}
            }
            var out=new java.util.LinkedHashMap<String,Object>();
            out.put("fundingRuleStatus",fundingStatus);
            out.put("proposedCouponTotal",proposedCouponTotal);
            out.put("proposedMerchandiseAfterCoupons",proposedMerchandise);
            out.put("proposedCalculatedOop",proposedMerchandise.add(calculatedTax).subtract(registerRewardsUsed).subtract(ecbUsed).subtract(walgreensCashUsed).subtract(walmartCashUsed).subtract(giftCardUsed).max(zero));
            out.put("expectedExtraBucks",promoRows.stream().filter(p->p.get("rewardType").equals("ECB")).map(p->(java.math.BigDecimal)p.get("potentialReward")).reduce(zero,java.math.BigDecimal::add));
            out.put("expectedWalmartCash",promoRows.stream().filter(p->p.get("rewardType").equals("WALMART_CASH")).map(p->(java.math.BigDecimal)p.get("potentialReward")).reduce(zero,java.math.BigDecimal::add));
            out.put("expectedRegisterRewards",promoRows.stream().filter(p->p.get("rewardType").equals("REGISTER_REWARD")).map(p->(java.math.BigDecimal)p.get("potentialReward")).reduce(zero,java.math.BigDecimal::add));
            out.put("expectedWalgreensCash",promoRows.stream().filter(p->p.get("rewardType").equals("WALGREENS_CASH")).map(p->(java.math.BigDecimal)p.get("potentialReward")).reduce(memberTotal,java.math.BigDecimal::add));
            out.put("fundingIssues",fundingIssues);
            out.put("storePromotions",storePromotions);
            out.put("storePromotionTotal",storePromotionTotal);
            out.put("totalDiscounts",storePromotionTotal.add(couponTotal));
            out.put("items",itemRows);
            out.put("receiptLevelRewards",receiptLevelRewards);
            out.put("memberRewardEstimate",memberTotal);
            out.put("memberRewardLines",memberRows);
            out.put("coupons",couponRows);
            out.put("promotions",promoRows);
            out.put("regularTotal",items.stream().allMatch(l->l.regular()!=null)?regular:null);
            out.put("saleTotal",saleTotal);
            out.put("saleReductionTotal",items.stream().map(l->(l.regular()==null?l.sale():l.regular()).multiply(java.math.BigDecimal.valueOf(l.quantity()))).reduce(zero,java.math.BigDecimal::add).subtract(saleTotal));
            out.put("couponTotal",couponTotal);
            out.put("merchandiseAfterCoupons",merchandise);
            out.put("tax",calculatedTax);
            out.put("totalWithTax",merchandise.add(calculatedTax));
            out.put("cashRebates",cashRebates);
            out.put("otherRebates",potential.subtract(cashRebates));
            out.put("afterCashRebates",selected.subtract(cashRebates));
            out.put("beforeTaxAfterCashRebates",selected.subtract(calculatedTax).subtract(cashRebates));
            out.put("netBeforeTax",selected.subtract(calculatedTax).subtract(potential));
            out.put("registerRewardsUsed",registerRewardsUsed);
            out.put("ecbUsed",ecbUsed);
            out.put("walmartCashUsed",walmartCashUsed);
            out.put("walgreensCashUsed",walgreensCashUsed);
            out.put("giftCardUsed",giftCardUsed);
            out.put("receiptTotalBeforeFunding",merchandise.subtract(registerRewardsUsed).subtract(ecbUsed).add(calculatedTax).max(zero));
            out.put("calculatedOop",oop);
            out.put("selectedOop",selected);
            out.put("manualDifference",selected.subtract(oop));
            out.put("overFunding",rawOop.negate().max(zero));
            out.put("potentialRewards",potential);
            out.put("afterPotentialRewards",selected.subtract(potential));return out;
        }
    }

    public ShoppingPlan {
        Objects.requireNonNull(planId, "Plan ID cannot be null.");
        requireText(name, "Plan name");
        if (retailer != null && retailer.isBlank()) throw new IllegalArgumentException("Retailer must be null or nonblank.");
        Objects.requireNonNull(plannedPurchaseIds, "Planned purchase IDs cannot be null.");
        plannedPurchaseIds = List.copyOf(plannedPurchaseIds);
        if (plannedPurchaseIds.stream().anyMatch(Objects::isNull)) throw new NullPointerException("Planned purchase ID cannot be null.");
        if (new HashSet<>(plannedPurchaseIds).size() != plannedPurchaseIds.size()) throw new IllegalArgumentException("Planned purchase IDs cannot repeat.");
        if (revision < 1) throw new IllegalArgumentException("Plan revision must be at least one.");
    }
    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null.");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank.");
    }
}
