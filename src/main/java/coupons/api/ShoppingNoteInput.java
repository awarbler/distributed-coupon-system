package coupons.api;

import coupons.shopping.ShoppingPlan;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative local note extraction. Unrecognized text remains in the saved source. */
final class ShoppingNoteInput {
    private ShoppingNoteInput() {}
    private static final String AMOUNT="([0-9]+(?:[.][0-9]+)?)";
    private static final Pattern TRAILING_QUANTITY=pattern("^(.+?)\\s+\\$?"+AMOUNT+"\\s*[*x×]\\s*([1-9][0-9]*)\\s*$");
    private static final Pattern PREFIX_QUANTITY=pattern("^(?:buy\\s+)?\\(?([1-9][0-9]*)\\)?\\s*[x×]\\s*([\\p{L}].*?)\\s+\\$?"+AMOUNT+"(?:\\s*(?:each|ea[.]?))?\\s*[›>»]?\\s*$");
    private static final Pattern LEADING_QUANTITY=pattern("^(?:buy\\s+)?\\(?([1-9][0-9]*)\\)?\\s+([\\p{L}].*?)\\s+\\$"+AMOUNT+"(?:\\s*(?:each|ea[.]?))?\\s*[›>»]?\\s*$");
    private static final Pattern BUY_GET=pattern("^buy\\s*1\\s*,?\\s*get\\s*1\\s+(50\\s*%\\s*off|free)\\s*[.!]?$");
    private static final Pattern IBOTTA=pattern("^(?:redeem|use)\\s+\\$?"+AMOUNT+"\\s*/\\s*([1-9][0-9]*)\\s+(.+?\\bibotta\\b.*?\\brebate)\\s+([1-9][0-9]*)\\s+times?\\s*[.]?$");
    private static final Pattern SPEND=pattern("^spend\\s*\\$?"+AMOUNT+"\\s*,?\\s*(?:get|earn|receive)\\s*\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*[.!]?$");
    private static final Pattern MFC=pattern("\\$\\s*"+AMOUNT+"\\s*(?:MFC|manufacturer\\s+coupons?)\\b");
    private static final Pattern MFC_LIST=pattern("^use\\s+(?:\\$\\s*"+AMOUNT+"\\s*(?:MFC|manufacturer\\s+coupons?)\\s*[,;+&]?\\s*)+[.]?$");
    private static final Pattern COUPON=pattern("^(?:use\\s+(?:the\\s+)?)?\\$?"+AMOUNT+"\\s*(?:off|/)\\s*([1-9][0-9]*)(.*)$");
    private static final Pattern REWARD=pattern("^(?:earn|get|receive)\\s+\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*$");
    private static final Pattern REWARD_VALUE=pattern("^\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*$");
    private static Pattern pattern(String regex){return Pattern.compile(regex,Pattern.CASE_INSENSITIVE);}
    private static String rewardType(String unit){return switch(unit.toUpperCase(Locale.ROOT)){case "RR"->"REGISTER_REWARD";case "WC","WCASH"->"WALGREENS_CASH";default->"ECB";};}

    /** Clipboard presentation is normalized only for parsing; source bytes/text stay untouched. */
    private static String plainText(String raw) {
        var out=new StringBuilder();
        for(int i=0;i<raw.length();) {
            if(raw.charAt(i)=='[') {
                int labelEnd=raw.indexOf("](",i+1);
                if(labelEnd>=0) {
                    int depth=1,end=labelEnd+2;
                    for(;end<raw.length();end++) {
                        char c=raw.charAt(end);
                        if(c=='\\'){end++;continue;}
                        if(c=='(')depth++;
                        if(c==')'){depth--;if(depth==0)break;}
                    }
                    if(depth==0){out.append(raw,i+1,labelEnd);i=end+1;continue;}
                }
            }
            out.append(raw.charAt(i++));
        }
        String line=out.toString().replace("&nbsp;"," ").replace("&#160;"," ").replace("&#xA0;"," ")
                .replace("&#x20;"," ").replace("&amp;","&").replace('\u00a0',' ').replace('\u202f',' ').replace('\u2009',' ')
                .replace("\u200b","").replace("\ufeff","").replace("**","").replace("__","").trim();
        while(line.length()>1&&((line.startsWith("*")&&line.endsWith("*"))||(line.startsWith("_")&&line.endsWith("_"))))line=line.substring(1,line.length()-1).trim();
        line=line.replaceFirst("(?i)\\s*svg\\s*$","").trim();
        return line.replaceAll("(?<=[0-9])[.] +(?=[0-9])", ".").replaceAll("[ \\t]+"," ");
    }

    /** Poster OCR can interleave packaging words between a Grab item and its price. */
    private static String joinPosterProducts(String text) {
        var lines=text.split("\\R",-1);boolean grab=false;int remaining=0;
        for(int i=0;i<lines.length;i++) {
            String line=lines[i].trim();
            if(line.equalsIgnoreCase("Grab")){grab=true;remaining=6;continue;}
            if(!grab||line.isEmpty())continue;
            if(pattern("^(?:clip|pay|get|earn|total)\\b.*").matcher(line).matches()){grab=false;continue;}
            var product=pattern("^([1-9][0-9]*)\\s*([\\p{L}].*?)$").matcher(line);
            if(product.matches()&&!line.contains("$")) {
                for(int j=i+1;j<lines.length&&j<=i+6;j++) {
                    String following=lines[j].trim();
                    if(pattern("^(?:clip|pay|get|earn|total)\\b.*").matcher(following).matches())break;
                    var price=pattern("^\\$"+AMOUNT+"$").matcher(following);
                    if(price.matches()){
                        lines[i]=product.group(1)+" "+product.group(2)+" $"+price.group(1);lines[j]="";break;
                    }
                    // Do not borrow a price across another quantified product.
                    if(pattern("^[1-9][0-9]*\\s*[\\p{L}].*").matcher(following).matches())break;
                }
                grab=false;
            }else if(--remaining==0)grab=false;
        }
        return String.join("\n",lines);
    }

    static Map<String,Object> parse(String text, ShoppingPlan.PlanningSource source) {
        if(text==null||text.isBlank())throw new IllegalArgumentException("Paste a deal or enter readable item text.");
        var items=new ArrayList<ShoppingPlan.Line>();var coupons=new ArrayList<ShoppingPlan.ScenarioCoupon>();
        var promotions=new ArrayList<ShoppingPlan.ScenarioPromotion>();var unresolved=new ArrayList<String>();
        var manufacturerLines=new HashSet<String>();
        String pendingBuyReward=null;
        String pendingSale=null;
        BigDecimal walgreensCashUsed=null;
        boolean advertisedBundle=false;
        var screenshotCoupons=new ArrayList<String>();
        var digitalSummaries=new ArrayList<BigDecimal>();
        var screenshotCouponQuantities=new ArrayList<Integer>();
        var clipCoupons=new LinkedHashMap<BigDecimal,String>();
        record PosterReward(String description,BigDecimal amount,int quantity,UUID line){}
        var posterRewards=new ArrayList<PosterReward>();
        UUID current=null;boolean rewardHeading=false;ShoppingPlan.ScenarioPromotion lastSpend=null;
        String screenshotProduct=null;
        String normalizedText=Arrays.stream(text.split("\\R")).map(ShoppingNoteInput::plainText).collect(java.util.stream.Collectors.joining("\n"));
        normalizedText=joinPosterProducts(normalizedText);
        normalizedText=pattern("register\\s+rewards?").matcher(normalizedText).replaceAll("RR");
        normalizedText=pattern("(?im)^pay (.*?)\\s*&\\s*get ").matcher(normalizedText).replaceAll("Pay $1 get ");
        normalizedText=pattern("(?im)(register|in[ -]?store)\\s*\\n\\s*(rewards?)\\b").matcher(normalizedText).replaceAll("$1 $2");

        normalizedText=pattern("(?im)(\\$"+AMOUNT+")\\s*\\n\\s*(RR|register rewards?|in[ -]?store rewards?)\\b").matcher(normalizedText).replaceAll("$1 $3");
        normalizedText=pattern("(?im)(\\bRR|register rewards?)\\s*\\n\\s*(WYB\\s*[1-9][0-9]*)\\b").matcher(normalizedText).replaceAll("$1 $2");
        normalizedText=pattern("(?im)(\\$"+AMOUNT+"\\s+off)\\s*\\n\\s*([1-9][0-9]*)\\s*$").matcher(normalizedText).replaceAll("$1 $3");
        // Poster summaries put a combined price below a quantity, followed by a DQ deduction.
        // Keep this separate from ordinary "2 products $5 each" notes.
        var posterTotal=pattern("(?m)^([1-9][0-9]*)\\s+([\\p{L}‘’“”'\\\"][^\\n$]*)\\n\\s*\\$"+AMOUNT+"\\s*\\n(?=\\s*[-−–—]\\s*\\$?"+AMOUNT+"\\s*DQ\\b)").matcher(normalizedText);
        normalizedText=posterTotal.replaceAll(m->{
            unresolved.add("Poster price $"+m.group(3)+" is treated as the combined price for "+m.group(1)+" products. Verify this against the original image.");
            return Matcher.quoteReplacement("Product: "+m.group(2).replaceAll("[‘’“”\\\"]","").trim()+"\nbuy "+m.group(1)+" for $"+m.group(3)+"\n");
        });
        // Rejoin explicitly quantified product/price and Clip/amount pairs in poster layouts.
        normalizedText=pattern("(?m)^((?:buy\\s+)?[1-9][0-9]*\\s+[\\p{L}][^\\n$]*)\\n\\s*\\$"+AMOUNT+"\\s*$").matcher(normalizedText).replaceAll(m->Matcher.quoteReplacement(m.group(1)+" $"+m.group(2)));
        normalizedText=pattern("(?m)^clip\\s*\\n\\s*(\\$"+AMOUNT+"\\s+off(?:\\s+1)?)\\s*$").matcher(normalizedText).replaceAll("Clip $1");
        // An explicit price equation supports a bundle estimate, not guessed individual prices.
        var equation=pattern("(?m)^\\s*\\$"+AMOUNT+"\\s*\\+\\s*\\$?"+AMOUNT+"\\s*=\\s*\\$?"+AMOUNT+"\\s*$").matcher(normalizedText);
        if(equation.find()&&new BigDecimal(equation.group(1)).add(new BigDecimal(equation.group(2))).compareTo(new BigDecimal(equation.group(3)))==0) {
            current=UUID.randomUUID();advertisedBundle=true;
            items.add(new ShoppingPlan.Line(current,"Advertised bundle — review individual products",null,null,new BigDecimal(equation.group(3)),1));
            unresolved.add("Bundle estimate only: enter individual product names, quantities and prices before shopping. The image's price equation does not identify which product has each price.");
        }
        for(String line:normalizedText.split("\\R")) {
            if(line.isEmpty())continue;
            if(advertisedBundle) {
                if(pattern("^\\s*\\$"+AMOUNT+"\\s*\\+\\s*\\$?"+AMOUNT+"\\s*=\\s*\\$?"+AMOUNT+"\\s*$").matcher(line).matches())continue;
                var deduction=pattern("^[-−–—]\\s*\\$?"+AMOUNT+"\\s*$").matcher(line);
                if(deduction.matches()) {
                    coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Advertised deduction — verify type and eligibility",new BigDecimal(deduction.group(1)),1,List.of(),null,"SALE_PROMOTION",true));
                    unresolved.add(line+" imported as an advertised whole-receipt reduction; confirm whether it is a coupon or store promotion and which products qualify.");continue;
                }
                var namedRR=pattern("^(?:(?:get|earn|receive)\\s+)?\\$"+AMOUNT+"\\s*RR\\s+(.+)$").matcher(line);
                if(namedRR.matches()) {
                    promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"Advertised "+namedRR.group(2)+" RR — verify requirements", "REGISTER_REWARD",new BigDecimal(namedRR.group(1)),0,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(),true));
                    unresolved.add(line+" — verify your offer's qualifying products, spend threshold and payment rules. This is expected, not earned.");continue;
                }
                // Checkout and net amounts are claims to check, never additional products.
                if(pattern("^(?:pay|net\\s+total)\\b").matcher(line).find()){unresolved.add(line+" — advertised comparison only; enter tax separately.");continue;}
            }
            boolean followsRewardHeading=rewardHeading;rewardHeading=false;
            if(line.startsWith("Product: ")){screenshotProduct=line.substring(9).trim();continue;}
            var clipped=pattern("^clip\\s+\\$"+AMOUNT+"\\s+off(?:\\s+1)?\\s*$").matcher(line);
            if(clipped.matches()){clipCoupons.put(new BigDecimal(clipped.group(1)).stripTrailingZeros(),clipped.group(1));continue;}
            var posterReward=pattern("^(?:(?:earn|get(?: back)?|receive)\\s+)?[-−–—]?\\$?"+AMOUNT+"\\s*(RR|register\\s+rewards?|in[ -]?store\\s+rewards?)(?:\\s*(?:WYB|buy)\\s*([1-9][0-9]*))?\\s*[.!]?$").matcher(line);
            if(posterReward.matches()){
                posterRewards.add(new PosterReward(line,new BigDecimal(posterReward.group(1)),posterReward.group(3)==null?0:Integer.parseInt(posterReward.group(3)),current));continue;
            }
            var bundle=pattern("^buy\\s+([1-9][0-9]*)\\s+for\\s+\\$"+AMOUNT+"\\s*(each|ea[.]?)?\\s*$").matcher(line);
            if(bundle.matches()){
                int count=Integer.parseInt(bundle.group(1));BigDecimal total=new BigDecimal(bundle.group(2));
                try {BigDecimal unit=bundle.group(3)==null?total.divide(BigDecimal.valueOf(count)):total;current=UUID.randomUUID();
                    items.add(new ShoppingPlan.Line(current,screenshotProduct==null?"Product from screenshot — enter name":screenshotProduct,null,null,unit,count));
                    if(screenshotProduct==null)unresolved.add("Product name was not readable. Replace Product from screenshot with the pictured product name.");
                }catch(ArithmeticException failure){unresolved.add(line+" — enter unit prices; bundle cannot be divided exactly.");}
                continue;
            }

            var buyReward=pattern("^buy\\s+([1-9][0-9]*)\\s+get\\s+\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*[.!]?$").matcher(line);
            if(buyReward.matches()){pendingBuyReward=line;continue;}
            var pending=pendingBuyReward==null?null:pattern("^buy\\s+([1-9][0-9]*)\\s+get\\s+\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*[.!]?$").matcher(pendingBuyReward);
            var listedPrices=pattern("^buy\\s+\\(?([1-9][0-9]*)\\)?\\s+(.+?)\\s*\\((\\$[0-9., $]+)\\)\\s*$").matcher(line);
            if(listedPrices.matches()) {
                int count=Integer.parseInt(listedPrices.group(1));var prices=pattern("\\$"+AMOUNT).matcher(listedPrices.group(3));
                var values=new ArrayList<BigDecimal>();while(prices.find())values.add(new BigDecimal(prices.group(1)));
                if(values.size()!=count){unresolved.add(line+" — quantity does not match listed prices.");continue;}
                var ids=new ArrayList<UUID>();
                for(var value:values){current=UUID.randomUUID();ids.add(current);items.add(new ShoppingPlan.Line(current,listedPrices.group(2),null,null,value,1));}
                if(pending!=null&&pending.matches()) {
                    promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),pendingBuyReward,rewardType(pending.group(3)),new BigDecimal(pending.group(2)),Integer.parseInt(pending.group(1)),BigDecimal.ZERO,"BEFORE_COUPONS",true,ids));pendingBuyReward=null;
                }
                if(pendingSale!=null){unresolved.add(pendingSale+" — explicit listed prices preserved; sale discount is not applied twice.");pendingSale=null;}
                continue;
            }
            var standalone=pattern("^([\\p{L}].*?)\\s+\\$"+AMOUNT+"\\s+(?:(each|ea[.]?)|(bogo\\s+50\\s*%\\s+off))\\s*$").matcher(line);
            if(pending!=null&&pending.matches()&&standalone.matches()){
                current=UUID.randomUUID();int count=Integer.parseInt(pending.group(1));
                items.add(new ShoppingPlan.Line(current,standalone.group(1),null,null,new BigDecimal(standalone.group(2)),count,null,standalone.group(4)==null?null:new ShoppingPlan.PriceRule("BOGO_HALF",null)));
                promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),pendingBuyReward,rewardType(pending.group(3)),new BigDecimal(pending.group(2)),count,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(current)));
                pendingBuyReward=null;continue;
            }

            if(pendingBuyReward!=null){unresolved.add(pendingBuyReward);pendingBuyReward=null;}
            var payReward=pattern("^pay\\s+\\$?"+AMOUNT+"\\s+get\\s+\\$?"+AMOUNT+"\\s*(RR|WCash|WC|ECB)\\s*[.!]?$").matcher(line);
            if(current!=null&&payReward.matches()){
                BigDecimal amount=new BigDecimal(payReward.group(2));String type=rewardType(payReward.group(3));UUID item=current;
                var linked=items.stream().filter(i->i.name().equals(items.stream().filter(v->v.id().equals(item)).findFirst().orElseThrow().name())).map(ShoppingPlan.Line::id).toList();
                if(promotions.stream().noneMatch(p->p.rewardType().equals(type)&&p.rewardAmount().compareTo(amount)==0&&new HashSet<>(p.lineIds()).equals(new HashSet<>(linked))))
                    promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,type,amount,0,BigDecimal.ZERO,"BEFORE_COUPONS",true,linked));
                unresolved.add(line+" (advertised checkout amount; compare with the calculated total)");continue;
            }

            var digitalSummary=pattern("^[-−–—]\\s*\\$?"+AMOUNT+"\\s*DQ[.]?\\s*$").matcher(line);
            if(digitalSummary.matches()) {
                digitalSummaries.add(new BigDecimal(digitalSummary.group(1)));continue;
            }
            var simpleCoupon=pattern("^\\$?"+AMOUNT+"\\s+off\\s+coupon\\s*$").matcher(line);
            if(current!=null&&simpleCoupon.matches()) {
                coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),line,new BigDecimal(simpleCoupon.group(1)),1,List.of(current),null));continue;
            }
            var saleProduct=pattern("^([\\p{L}].*?)\\s+\\$?"+AMOUNT+"\\s+on\\s+sale\\s+for\\s+\\$?"+AMOUNT+"$").matcher(line);
            if(saleProduct.matches()) {
                current=UUID.randomUUID();items.add(new ShoppingPlan.Line(current,saleProduct.group(1),null,new BigDecimal(saleProduct.group(2)),new BigDecimal(saleProduct.group(3)),1));continue;
            }
            var cashUsed=pattern("[-−]\\s*\\$?"+AMOUNT+"\\s+walgreens\\s+cash\\b").matcher(line);
            if(cashUsed.find())walgreensCashUsed=new BigDecimal(cashUsed.group(1));

            var atPrice=pattern("^([1-9][0-9]*)\\s+(.+?)\\s*@\\s*\\$?"+AMOUNT+"\\s*(?:ea[.]?|each)\\s+BOGO\\s+50\\s*%(?:\\s+off)?\\s*$").matcher(line);
            if(atPrice.matches()) {
                current=UUID.randomUUID();items.add(new ShoppingPlan.Line(current,atPrice.group(2).trim(),null,null,new BigDecimal(atPrice.group(3)),Integer.parseInt(atPrice.group(1)),null,new ShoppingPlan.PriceRule("BOGO_HALF",null)));
                unresolved.add("BOGO half-price arithmetic may include a half cent. Compare the calculated total with the advertised checkout total and your store's rounding.");continue;
            }
            String productText=line;String inlineOffer=null;
            var inline=pattern("^(.*)\\s+\\((buy\\s+1.*)\\)$").matcher(line);
            if(inline.matches()){productText=inline.group(1);inlineOffer=inline.group(2);}
            Matcher trailing=TRAILING_QUANTITY.matcher(productText),prefix=PREFIX_QUANTITY.matcher(productText),leading=LEADING_QUANTITY.matcher(productText);
            String name=null,price=null,quantity=null;
            if(trailing.matches()){name=trailing.group(1);price=trailing.group(2);quantity=trailing.group(3);}
            else if(prefix.matches()){quantity=prefix.group(1);name=prefix.group(2);price=prefix.group(3);}
            else if(leading.matches()){quantity=leading.group(1);name=leading.group(2);price=leading.group(3);}
            if(name!=null){current=UUID.randomUUID();items.add(new ShoppingPlan.Line(current,name.replaceAll("[,;:]+$","").trim(),null,null,new BigDecimal(price),Integer.parseInt(quantity)));
                if(inlineOffer!=null){var offer=BUY_GET.matcher(inlineOffer);if(offer.matches()){
                    var old=items.removeLast();items.add(new ShoppingPlan.Line(old.id(),old.name(),old.sku(),old.regular(),old.sale(),old.quantity(),old.memberRewardRate(),new ShoppingPlan.PriceRule(offer.group(1).equalsIgnoreCase("free")?"BOGO_FREE":"BOGO_HALF",null)));
                    if(old.quantity()==1)unresolved.add("Review mixed-product sale eligibility for "+old.name()+"; a single item does not receive a BOGO discount by itself.");
                }else unresolved.add("Review sale terms for "+name+": "+inlineOffer);}
                continue;}

            var buyGet=BUY_GET.matcher(line);
            if(current==null&&buyGet.matches()){pendingSale=line;continue;}
            if(current!=null&&buyGet.matches()) {
                int index=items.size()-1;var old=items.get(index);
                items.set(index,new ShoppingPlan.Line(old.id(),old.name(),old.sku(),old.regular(),old.sale(),old.quantity(),old.memberRewardRate(),new ShoppingPlan.PriceRule(buyGet.group(1).equalsIgnoreCase("free")?"BOGO_FREE":"BOGO_HALF",null)));
                continue;
            }
            // Rebates are benefits after purchase, never checkout coupon deductions.
            var rebate=IBOTTA.matcher(line);
            if(current!=null&&rebate.matches()) {
                int times=Integer.parseInt(rebate.group(4)),required=Math.multiplyExact(Integer.parseInt(rebate.group(2)),times);
                promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,"IBOTTA",new BigDecimal(rebate.group(1)).multiply(BigDecimal.valueOf(times)),required,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(current)));
                unresolved.add("Ibotta amount imported from the advertisement; your account offer and redemption limit are not confirmed.");continue;
            }
            var screenshotRebate=pattern("^[-–—]\\$"+AMOUNT+"\\s+Ibotta(?:\\s+.*)?$").matcher(line);
            if(current!=null&&screenshotRebate.matches()){
                promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,"IBOTTA",new BigDecimal(screenshotRebate.group(1)),0,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(current)));
                unresolved.add("Advertised Ibotta amount: verify your own offer and expiration.");continue;
            }
            if(pattern("\\b(?:rebate|ibotta)\\b").matcher(line).find()){unresolved.add(line);continue;}

            var spend=SPEND.matcher(line);
            if(current!=null&&spend.matches()) {
                lastSpend=new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,rewardType(spend.group(3)),new BigDecimal(spend.group(2)),0,new BigDecimal(spend.group(1)),"AFTER_COUPONS",true,items.stream().map(ShoppingPlan.Line::id).toList());
                promotions.add(lastSpend);
                unresolved.add("Spend basis is not shown: this imported spend offer uses after-coupon spending until you enter the offer's actual basis.");continue;
            }
            if(current!=null&&line.matches("(?i)^use\\b.*")&&MFC.matcher(line).find()) {
                if(!MFC_LIST.matcher(line).matches()) {
                    unresolved.add(line);unresolved.add("Manufacturer coupon text is incomplete; no partial coupon total was applied.");continue;
                }
                String identity=items.stream().map(l->l.id().toString()).reduce("",String::concat)+line.toLowerCase(Locale.ROOT).replaceAll("\\s+","");
                if(manufacturerLines.add(identity)) {
                    var amounts=MFC.matcher(line);BigDecimal total=BigDecimal.ZERO;
                    while(amounts.find())total=total.add(new BigDecimal(amounts.group(1)));
                    if(total.signum()>0)coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Advertised manufacturer coupons (unallocated): "+line,total,1,List.of(),null,"MANUFACTURER",true));
                    unresolved.add("Advertised manufacturer coupon amounts have no product links. Replace or allocate them using your own recorded offers before shopping.");
                }
                continue;
            }
            var namedCoupon=pattern("^use\\s+(?:the\\s+)?\\$"+AMOUNT+"\\s+off\\s+(?:([1-9][0-9]*)\\s+)?(.+?)\\s+coupon(?:\\s+.*)?$").matcher(line);
            if(namedCoupon.matches()) {
                String brand=namedCoupon.group(3).toLowerCase(Locale.ROOT);
                var linked=items.stream().filter(i->i.name().toLowerCase(Locale.ROOT).contains(brand)).map(ShoppingPlan.Line::id).toList();
                if(linked.isEmpty()){unresolved.add(line);continue;}
                coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),line,new BigDecimal(namedCoupon.group(1)),namedCoupon.group(2)==null?1:Integer.parseInt(namedCoupon.group(2)),linked,null));continue;
            }
            var code=pattern("^use code (.+?) at checkout\\s*[-–—]\\s*will deduct \\$"+AMOUNT+"[.]?$").matcher(line);
            if(!items.isEmpty()&&code.matches()){
                coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),line,new BigDecimal(code.group(2)),1,List.of(),null,"STORE_COUPON",true));continue;
            }
            var cashReward=pattern("^get back (?:\\$"+AMOUNT+"|([0-9]+)¢) (?:booster )?cash rewards(?: (.*))?$").matcher(line);
            if(!items.isEmpty()&&cashReward.matches()) {
                BigDecimal amount=cashReward.group(1)==null?new BigDecimal(cashReward.group(2)).movePointLeft(2):new BigDecimal(cashReward.group(1));
                String terms=cashReward.group(3)==null?"":cashReward.group(3);
                var requirement=pattern("^WYB ([1-9][0-9]*) (.+)$").matcher(terms);
                if(requirement.matches()) {
                    String words=requirement.group(2).toLowerCase(Locale.ROOT);
                    var linked=items.stream().filter(i->Arrays.stream(words.split("[^a-z]+" )).filter(w->w.length()>3).anyMatch(w->i.name().toLowerCase(Locale.ROOT).contains(w))).map(ShoppingPlan.Line::id).toList();
                    if(linked.isEmpty()){unresolved.add(line);continue;}
                    promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,"WALGREENS_CASH",amount,Integer.parseInt(requirement.group(1)),BigDecimal.ZERO,"BEFORE_COUPONS",true,linked));continue;
                }
                // Unspecified boosters must retain their spend/payment eligibility for review.
                if(line.toLowerCase(Locale.ROOT).contains("booster")){unresolved.add(line);continue;}
                if(terms.equalsIgnoreCase("for purchase")){
                    promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),"myWalgreens member reward","WALGREENS_CASH",amount,0,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(),true));continue;
                }
                unresolved.add(line);continue;
            }

            var screenshotCoupon=pattern("^[=—–\\-\\s]*\\$"+AMOUNT+"\\s+off\\s+([1-9][0-9]*)\\s*[;:]?$").matcher(line);
            if(screenshotCoupon.matches()){screenshotCoupons.add(screenshotCoupon.group(1));screenshotCouponQuantities.add(Integer.parseInt(screenshotCoupon.group(2)));continue;}
            var namedSlash=pattern("^use\\s+(?:the\\s+)?\\$"+AMOUNT+"\\s*/\\s*([1-9][0-9]*)\\s+(.+?)(?:\\s+digital)?\\s+coupon[.!]?$").matcher(line);
            if(namedSlash.matches()) {
                String brand=namedSlash.group(3).toLowerCase(Locale.ROOT);
                var ids=items.stream().filter(i->i.name().toLowerCase(Locale.ROOT).contains(brand)).map(ShoppingPlan.Line::id).toList();
                if(!ids.isEmpty()){coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),line,new BigDecimal(namedSlash.group(1)),Integer.parseInt(namedSlash.group(2)),ids,null));continue;}
            }
            var coupon=COUPON.matcher(line);
            if(current!=null&&coupon.matches()) {
                coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),line,new BigDecimal(coupon.group(1)),Integer.parseInt(coupon.group(2)),List.of(current),null));continue;
            }
            if(line.matches("(?i)^(?:get|earn|receive)\\s*:?$")){rewardHeading=true;continue;}
            Matcher reward=REWARD.matcher(line),isolated=REWARD_VALUE.matcher(line);
            if(current!=null&&(reward.matches()||followsRewardHeading&&isolated.matches())) {
                boolean full=reward.matches();String unit=full?reward.group(2):isolated.group(2);BigDecimal amount=new BigDecimal(full?reward.group(1):isolated.group(1));
                if(!full&&lastSpend!=null&&lastSpend.rewardType().equals(rewardType(unit))&&lastSpend.rewardAmount().compareTo(amount)==0&&lastSpend.lineIds().equals(items.stream().map(ShoppingPlan.Line::id).toList()))continue;
                promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),line,rewardType(unit),amount,0,BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(current)));continue;
            }
            var simpleProduct=pattern("^([\\p{L}].*?)\\s+\\$?"+AMOUNT+"$").matcher(line);
            if(simpleProduct.matches()&&(simpleProduct.group(2).contains(".")||line.contains("$"))&&!pattern("^(?:pay|total|tax|save|earn|get|receive|spend|coupon|use)\\b").matcher(line).find()) {
                current=UUID.randomUUID();items.add(new ShoppingPlan.Line(current,simpleProduct.group(1),null,null,new BigDecimal(simpleProduct.group(2)),1));continue;
            }
            unresolved.add(line);
        }
        if(items.size()==1){
            var product=items.getFirst();
            for(int i=0;i<screenshotCoupons.size();i++){
                String amount=screenshotCoupons.get(i);int required=screenshotCouponQuantities.get(i);
                if(required!=1||!clipCoupons.containsKey(new BigDecimal(amount).stripTrailingZeros()))
                    coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Advertised $"+amount+" off "+required+" — verify offer",new BigDecimal(amount),required,List.of(product.id()),null));
            }
            for(String amount:clipCoupons.values())coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Advertised $"+amount+" off 1 — verify offer",new BigDecimal(amount),1,List.of(product.id()),null));
        }else {
            for(String amount:screenshotCoupons)unresolved.add("$"+amount+" off 1 — select qualifying product");
            for(String amount:clipCoupons.values())unresolved.add("Clip $"+amount+" off — select qualifying product");
        }
        for(var deduction:digitalSummaries) {
            if(coupons.isEmpty()&&!items.isEmpty()) {
                coupons.add(new ShoppingPlan.ScenarioCoupon(UUID.randomUUID(),"Advertised digital coupon total — verify individual offers",deduction,1,List.of(),null,"MANUFACTURER",true));
                unresolved.add("Imported the DQ summary once because no individual coupon amounts were read. Confirm the individual digital coupons and qualifying products.");
            } else {
                var couponTotal=coupons.stream().map(ShoppingPlan.ScenarioCoupon::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
                unresolved.add("DQ summary $"+deduction+" not added again. Individual imported coupons total $"+couponTotal+(couponTotal.compareTo(deduction)==0?"; verify eligibility.":"; resolve these conflicting amounts before shopping."));
            }
        }
        for(var reward:posterRewards){
            UUID line=reward.line()!=null?reward.line():items.size()==1?items.getFirst().id():null;
            if(line==null){unresolved.add(reward.description()+" — select qualifying product");continue;}
            boolean duplicate=promotions.stream().anyMatch(p->p.rewardType().equals("REGISTER_REWARD")&&p.rewardAmount().compareTo(reward.amount())==0&&p.lineIds().equals(List.of(line)));
            if(!duplicate)promotions.add(new ShoppingPlan.ScenarioPromotion(UUID.randomUUID(),reward.description(),"REGISTER_REWARD",reward.amount(),reward.quantity(),BigDecimal.ZERO,"BEFORE_COUPONS",true,List.of(line)));
        }
        if(pendingBuyReward!=null)unresolved.add(pendingBuyReward);
        if(items.size()>100||coupons.size()>100||promotions.size()>100)throw new IllegalArgumentException("Import at most 100 items, coupons and rewards per plan.");
        var result=new LinkedHashMap<String,Object>();result.put("advertisedBundle",advertisedBundle);result.put("walgreensCashUsed",walgreensCashUsed);result.put("items",items);result.put("coupons",coupons);result.put("promotions",promotions);result.put("unresolved",unresolved);result.put("source",source);
        var store=pattern("(?m)^\\s*(?:in[- ]store\\s+deal\\s+at\\s+)(Walgreens|CVS|Walmart|Target)\\b|^\\s*(Walgreens|CVS|Walmart|Target)\\s+(?:weekly\\s+deals|deals)\\b").matcher(normalizedText);
        result.put("retailer",store.find()?(store.group(1)==null?store.group(2):store.group(1)):null);
        result.put("notice","Imported amounts are advertised assumptions. Your account offers are unknown until recorded; source notes are preserved.");return result;
    }
}
