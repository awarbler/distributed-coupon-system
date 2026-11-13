package coupons.promotions;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Recorded offer arithmetic.
 * Text qualifications still require an explicit item association.
 */
public record DealTerms(
        Type type,
        String productDescription,
        BigDecimal regularUnitPrice,
        BigDecimal promotionalPrice,
        int minimumQuantity,
        BigDecimal minimumSpend,
        int freeQuantity,
        BigDecimal rewardAmount,
        boolean repeatable,
        Integer limit,
        List<String> qualifyingSkus,
        String qualifications,
        String loyaltyRequirement,
        String exclusions,
        boolean cheapestItemsDiscounted,
        boolean strictSkuMatching,
        WeeklyAdvertisement advertisement
) implements Serializable {

    /** One source offer, containing all of its price, coupon and reward terms. */
    public record WeeklyAdvertisement(String draftJson, java.util.Map<String,String> fields,
            List<String> warnings) implements Serializable {
        public WeeklyAdvertisement {
            if (draftJson == null || draftJson.length() > 150000) throw new IllegalArgumentException("Invalid weekly deal draft.");
            fields = java.util.Map.copyOf(fields);
            warnings = List.copyOf(warnings);
        }
    }
    public DealTerms(Type type,String product,BigDecimal regular,BigDecimal promotional,int quantity,
            BigDecimal spend,int free,BigDecimal reward,boolean repeat,Integer limit,List<String> skus,
            String qualifications,String loyalty,String exclusions,boolean cheapest,boolean strict) {
        this(type,product,regular,promotional,quantity,spend,free,reward,repeat,limit,skus,qualifications,loyalty,exclusions,cheapest,strict,null);
    }
    /**
     * Backward-compatible 15-argument constructor.
     * Existing calls default to non-strict UPC matching.
     */
    public DealTerms(
            Type type,
            String productDescription,
            BigDecimal regularUnitPrice,
            BigDecimal promotionalPrice,
            int minimumQuantity,
            BigDecimal minimumSpend,
            int freeQuantity,
            BigDecimal rewardAmount,
            boolean repeatable,
            Integer limit,
            List<String> qualifyingSkus,
            String qualifications,
            String loyaltyRequirement,
            String exclusions,
            boolean cheapestItemsDiscounted) {

        this(
                type,
                productDescription,
                regularUnitPrice,
                promotionalPrice,
                minimumQuantity,
                minimumSpend,
                freeQuantity,
                rewardAmount,
                repeatable,
                limit,
                qualifyingSkus,
                qualifications,
                loyaltyRequirement,
                exclusions,
                cheapestItemsDiscounted,
                false
        );
    }

    /**
     * Backward-compatible 14-argument constructor.
     */
    public DealTerms(
            Type type,
            String product,
            BigDecimal regular,
            BigDecimal promotional,
            int quantity,
            BigDecimal spend,
            int free,
            BigDecimal reward,
            boolean repeat,
            Integer limit,
            List<String> skus,
            String qualifications,
            String loyalty,
            String exclusions) {

        this(
                type,
                product,
                regular,
                promotional,
                quantity,
                spend,
                free,
                reward,
                repeat,
                limit,
                skus,
                qualifications,
                loyalty,
                exclusions,
                false,
                false
        );
    }

    public enum Type {
        ADVERTISEMENT,
        SALE,
        MULTIBUY,
        BUY_GET,
        SPEND_REWARD,
        QUANTITY_REWARD
    }

    public DealTerms {
        if (type == null
                || minimumQuantity < 1
                || freeQuantity < 0
                || (limit != null && limit < 1)) {
            throw new IllegalArgumentException("Invalid offer quantities.");
        }

        for (var amount : new BigDecimal[]{
                regularUnitPrice,
                promotionalPrice,
                minimumSpend,
                rewardAmount
        }) {
            if (amount != null && amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "Offer amounts cannot be negative.");
            }
        }

        if ((type == Type.ADVERTISEMENT) != (advertisement != null)) throw new IllegalArgumentException("Weekly advertisements require their combined offer draft.");

        qualifyingSkus = qualifyingSkus == null
                ? List.of()
                : List.copyOf(qualifyingSkus);

        if ((type == Type.SALE
                || type == Type.MULTIBUY
                || type == Type.BUY_GET)
                && promotionalPrice == null) {
            throw new IllegalArgumentException(
                    "Record a promotional price (zero for free items).");
        }

        if (type == Type.SPEND_REWARD
                && (minimumSpend == null
                || minimumSpend.signum() <= 0)) {
            throw new IllegalArgumentException(
                    "Record a positive qualifying spend.");
        }

        if ((type == Type.SPEND_REWARD
                || type == Type.QUANTITY_REWARD)
                && (rewardAmount == null
                || rewardAmount.signum() <= 0)) {
            throw new IllegalArgumentException(
                    "Record a positive expected reward.");
        }

        if (type == Type.BUY_GET && freeQuantity < 1) {
            throw new IllegalArgumentException(
                    "Record the discounted item quantity.");
        }

        if (strictSkuMatching && qualifyingSkus.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strict SKU matching requires at least one qualifying SKU.");
        }
    }
}