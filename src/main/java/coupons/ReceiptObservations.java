package coupons;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable receipt facts, earning observations, and confirmed savings links.
 *
 * <p>Observations are not a live account ledger. Printed aggregates reconcile
 * financial events; they never create extra deductions or earnings. Unidentified
 * RR deductions do not reserve a fictitious instrument. Associating one with an
 * issued instrument later requires an explicit audited reconciliation operation.
 *
 * @param purchaseDate recorded date, null when unknown
 * @param printedTime exact time text, null when unknown
 * @param earnings underlying item, multi-item, receipt-wide, or unassociated earnings
 * @param unidentifiedRR deductions whose instruments are unknown
 * @param walgreensCash printed account activity, null when unavailable
 * @param savings printed savings categories and user-confirmed links
 * @param discountKinds explicit classification of actual deductions
 */
public record ReceiptObservations(LocalDate purchaseDate, String printedTime,
        List<Earning> earnings, List<UnidentifiedRR> unidentifiedRR,
        WalgreensCashAccount walgreensCash, List<SavingsCategory> savings,
        Map<UUID, DiscountKind> discountKinds, BigDecimal receiptSavingValuePercent,
        List<ReceiptPromotionLink> promotionLinks, ReceiptWorksheetData worksheet, FooterStatus footerStatus) implements java.io.Serializable {
    public enum FooterStatus { UNKNOWN, PRINTED, NOT_PRINTED }
    /** Existing snapshots do not assert whether a footer was printed. */
    public ReceiptObservations(LocalDate purchaseDate, String printedTime, List<Earning> earnings,
            List<UnidentifiedRR> unidentifiedRR, WalgreensCashAccount walgreensCash, List<SavingsCategory> savings,
            Map<UUID, DiscountKind> discountKinds, BigDecimal receiptSavingValuePercent,
            List<ReceiptPromotionLink> promotionLinks, ReceiptWorksheetData worksheet) {
        this(purchaseDate,printedTime,earnings,unidentifiedRR,walgreensCash,savings,discountKinds,
                receiptSavingValuePercent,promotionLinks,worksheet,FooterStatus.UNKNOWN);
    }
    public ReceiptObservations(LocalDate purchaseDate, String printedTime, List<Earning> earnings,
            List<UnidentifiedRR> unidentifiedRR, WalgreensCashAccount walgreensCash, List<SavingsCategory> savings,
            Map<UUID, DiscountKind> discountKinds, BigDecimal receiptSavingValuePercent, List<ReceiptPromotionLink> promotionLinks) {
        this(purchaseDate,printedTime,earnings,unidentifiedRR,walgreensCash,savings,discountKinds,receiptSavingValuePercent,promotionLinks,ReceiptWorksheetData.empty());
    }
    public ReceiptObservations(LocalDate purchaseDate, String printedTime, List<Earning> earnings,
            List<UnidentifiedRR> unidentifiedRR, WalgreensCashAccount walgreensCash, List<SavingsCategory> savings,
            Map<UUID, DiscountKind> discountKinds, BigDecimal receiptSavingValuePercent) {
        this(purchaseDate, printedTime, earnings, unidentifiedRR, walgreensCash, savings,
                discountKinds, receiptSavingValuePercent, List.of());
    }
    /** Preserves existing receipts with no printed savings percentage. */
    public ReceiptObservations(LocalDate purchaseDate, String printedTime, List<Earning> earnings,
            List<UnidentifiedRR> unidentifiedRR, WalgreensCashAccount walgreensCash, List<SavingsCategory> savings,
            Map<UUID, DiscountKind> discountKinds) {
        this(purchaseDate, printedTime, earnings, unidentifiedRR, walgreensCash, savings, discountKinds, null);
    }
    /** Copies input and validates local structure without forcing reconciliation. */
    public ReceiptObservations {
        worksheet=worksheet==null?ReceiptWorksheetData.empty():worksheet;
        footerStatus=footerStatus==null?FooterStatus.UNKNOWN:footerStatus;
        optionalText(printedTime, "Printed time");
        nonnegative(receiptSavingValuePercent);
        earnings = List.copyOf(earnings);
        unidentifiedRR = List.copyOf(unidentifiedRR);
        savings = List.copyOf(savings);
        discountKinds = Map.copyOf(discountKinds);
        promotionLinks = promotionLinks == null ? List.of() : List.copyOf(promotionLinks);
        unique(promotionLinks.stream().map(ReceiptPromotionLink::promotionId).toList());
        unique(earnings.stream().map(Earning::earningId).toList());
        unique(unidentifiedRR.stream().map(UnidentifiedRR::eventId).toList());
        unique(savings.stream().map(SavingsCategory::categoryId).toList());
    }

    /** Returns observations with no invented values or default benefits. */
    public static ReceiptObservations empty() {
        return new ReceiptObservations(null, null, List.of(), List.of(), null, List.of(), Map.of());
    }

    /** Keeps reward value types distinct even when both are dollar-denominated. */
    public enum EarningKind { REGISTER_REWARD, WALGREENS_CASH, THIRD_PARTY_REBATE }
    /** Distinguishes receipt-wide scope from genuinely unknown item associations. */
    public enum Scope { ITEM, MULTI_ITEM, RECEIPT, UNKNOWN }
    /** Refers to existing events instead of creating another financial entry. */
    public enum EventKind { COUPON_APPLIED, PAYMENT, REGISTER_REWARD_REDEMPTION, UNIDENTIFIED_RR, EARNING }
    /** Stores classification explicitly, rather than guessing from a printed label. */
    public enum DiscountKind { STORE, MANUFACTURER, OTHER, SALE_PROMOTION, STORE_COUPON }

    /**
     * One earning observation with optional expected and confirmed entitlement.
     *
     * <p>Crediting or delivering the same value never creates another Earning.
     * Instrument/claim references connect to existing models when available;
     * claim existence requires future rebate repository integration.
     */
    public record Earning(UUID earningId, EarningKind kind, String provider,
            String offerReference, Scope scope, List<UUID> lineItemIds,
            RebateBenefit expected, RebateBenefit confirmed, RebateStatus status,
            LocalDate expirationDate, UUID instrumentId, UUID claimId, List<UUID> evidenceIds,
            String recordedLifecycle, OfferRequirement requirement, RecordedValuation valuation, EarningDetails details) implements java.io.Serializable {
        public Earning(UUID id,EarningKind kind,String provider,String offer,Scope scope,List<UUID> lines,
                RebateBenefit expected,RebateBenefit confirmed,RebateStatus status,LocalDate expiry,UUID instrument,
                UUID claim,List<UUID> evidence,String lifecycle,OfferRequirement requirement,RecordedValuation valuation) {
            this(id,kind,provider,offer,scope,lines,expected,confirmed,status,expiry,instrument,claim,evidence,lifecycle,requirement,valuation,null);
        }
        /** Preserves the existing constructor; new observations remain unknown. */
        public Earning(UUID earningId, EarningKind kind, String provider,
                String offerReference, Scope scope, List<UUID> lineItemIds,
                RebateBenefit expected, RebateBenefit confirmed, RebateStatus status,
                LocalDate expirationDate, UUID instrumentId, UUID claimId, List<UUID> evidenceIds,
                String recordedLifecycle) {
            this(earningId, kind, provider, offerReference, scope, lineItemIds, expected, confirmed,
                    status, expirationDate, instrumentId, claimId, evidenceIds, recordedLifecycle, null, null);
        }

        /** Preserves callers with no separately recorded availability/lifecycle text. */
        public Earning(UUID earningId, EarningKind kind, String provider,
                String offerReference, Scope scope, List<UUID> lineItemIds,
                RebateBenefit expected, RebateBenefit confirmed, RebateStatus status,
                LocalDate expirationDate, UUID instrumentId, UUID claimId, List<UUID> evidenceIds) {
            this(earningId, kind, provider, offerReference, scope, lineItemIds, expected, confirmed,
                    status, expirationDate, instrumentId, claimId, evidenceIds, null);
        }

        /** Preserves values without assuming eligibility, availability, or conversion. */
        public Earning {
            Objects.requireNonNull(earningId);
            Objects.requireNonNull(kind);
            text(provider, "Provider");
            optionalText(offerReference, "Offer reference");
            optionalText(recordedLifecycle, "Recorded lifecycle");
            Objects.requireNonNull(scope);
            Objects.requireNonNull(status);
            lineItemIds = List.copyOf(lineItemIds);
            evidenceIds = List.copyOf(evidenceIds);
            unique(lineItemIds);
            unique(evidenceIds);
            if(details!=null && !new HashSet<>(lineItemIds).equals(details.qualifyingQuantities().keySet()) && !details.qualifyingQuantities().isEmpty())throw new IllegalArgumentException("Quantity selection must match selected earning lines.");
            if (expected == null && confirmed == null) {
                throw new IllegalArgumentException("An expected or confirmed benefit is required.");
            }
            if (scope == Scope.ITEM && lineItemIds.size() != 1
                    || scope == Scope.MULTI_ITEM && lineItemIds.size() < 2
                    || (scope == Scope.RECEIPT || scope == Scope.UNKNOWN) && !lineItemIds.isEmpty()) {
                throw new IllegalArgumentException("Earning scope does not match its item associations.");
            }
            if (kind != EarningKind.THIRD_PARTY_REBATE) {
                for (RebateBenefit benefit : new RebateBenefit[]{expected, confirmed}) {
                    if (benefit != null && (benefit.kind() != BenefitKind.CASH
                            && benefit.kind() != (kind == EarningKind.REGISTER_REWARD
                                    ? BenefitKind.STORE_COUPON : BenefitKind.STORE_REWARD)
                            || !Currency.getInstance("USD").equals(benefit.currency()))) {
                        throw new IllegalArgumentException("Retailer reward face values must be recorded in USD.");
                    }
                }
            }
        }
    }

    public enum SpendBasis { SALE_BEFORE_COUPONS, SALE_AFTER_COUPONS, UNKNOWN }

    /** Recorded requirement and explicit arithmetic basis; eligibility remains user-confirmed. */
    public record OfferRequirement(PromotionType type, BigDecimal amount, String category,
            SpendBasis spendBasis) implements java.io.Serializable {
        /** Old receipts and callers have an unknown basis; do not infer it from notes. */
        public OfferRequirement(PromotionType type, BigDecimal amount, String category) {
            this(type, amount, category, SpendBasis.UNKNOWN);
        }
        /** Preserves existing buy/spend qualification callers. */
        public OfferRequirement(PromotionType type, BigDecimal amount) {
            this(Objects.requireNonNull(type), amount, null);
        }

        public OfferRequirement {
            spendBasis = spendBasis == null ? SpendBasis.UNKNOWN : spendBasis;
            if (type != PromotionType.SPEND && spendBasis != SpendBasis.UNKNOWN)
                throw new IllegalArgumentException("A spend basis applies only to spend requirements.");
            if (type == null) {
                if (amount != null || !("Receipt".equals(category) || "Other".equals(category) || "Purchase".equals(category) || "ReceiptScan".equals(category))) {
                    throw new IllegalArgumentException("Receipt/Other requirements have a category and no quantity or spend amount.");
                }
            } else {
                Objects.requireNonNull(amount);
                if (category != null || amount.signum() <= 0
                        || type == PromotionType.BUY && amount.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException("Buy quantity must be a positive whole number; spend amount must be positive.");
                }
            }
        }
    }

    /** User-recorded value of one benefit, not another earning or a conversion transaction. */
    public record RecordedValuation(BigDecimal amount, Currency currency, String basis) implements java.io.Serializable {
        public RecordedValuation {
            Objects.requireNonNull(amount);
            Objects.requireNonNull(currency);
            nonnegative(amount);
            text(basis, "Valuation basis");
        }
    }

    /** A recorded RR deduction, without pretending to know its issued instrument. */
    public record UnidentifiedRR(UUID eventId, BigDecimal amount, String recordedDescription, String notes) implements java.io.Serializable {
        public UnidentifiedRR(UUID eventId, BigDecimal amount, String recordedDescription) {
            this(eventId, amount, recordedDescription, null);
        }
        /** Requires a positive amount and preserves its original scale. */
        public UnidentifiedRR {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(amount);
            text(recordedDescription, "RR description");
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("RR deduction must be positive.");
            }
        }
    }

    /** Four independently printed observations; null means unknown, not zero. */
    public record WalgreensCashAccount(BigDecimal startingBalance, BigDecimal used,
            BigDecimal earned, BigDecimal endingBalance) implements java.io.Serializable {
        /** Retains printed balances and validates nonnegative activity. */
        public WalgreensCashAccount {
            nonnegative(used);
            nonnegative(earned);
        }

        /** Calculates only when all contributing printed values are known. */
        public Optional<BigDecimal> calculatedEnding() {
            return startingBalance == null || used == null || earned == null ? Optional.empty()
                    : Optional.of(startingBalance.subtract(used).add(earned));
        }

        /** Returns printed ending minus calculated ending without erasing differences. */
        public Optional<BigDecimal> difference() {
            return endingBalance == null ? Optional.empty()
                    : calculatedEnding().map(value -> endingBalance.subtract(value));
        }
    }

    /** Typed stable reference to one underlying event. */
    public record EventReference(EventKind kind, UUID eventId) implements java.io.Serializable {
        /** Requires both reference components. */
        public EventReference {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(eventId);
        }
    }

    /** An amount the entrant confirms is included; not another deduction or earning. */
    public record Inclusion(EventReference event, BigDecimal includedAmount,
            String confirmedBy, Instant confirmedAt) implements java.io.Serializable {
        /** Preserves confirmation and amount, including zero. */
        public Inclusion {
            Objects.requireNonNull(event);
            Objects.requireNonNull(includedAmount);
            nonnegative(includedAmount);
            text(confirmedBy, "Confirmed by");
            Objects.requireNonNull(confirmedAt);
        }
    }

    /**
     * A printed category with a potentially partial confirmed explanation.
     * Currency is explicit so points and different denominations cannot silently
     * become dollar savings. A printed total may be stored without event links.
     */
    public record SavingsCategory(UUID categoryId, String printedLabel,
            Currency currency, BigDecimal reportedAmount, List<Inclusion> inclusions,
            boolean explanationComplete) implements java.io.Serializable {
        /** Rejects duplicate event links within one category, not arithmetic differences. */
        public SavingsCategory {
            Objects.requireNonNull(categoryId);
            text(printedLabel, "Savings label");
            Objects.requireNonNull(currency);
            Objects.requireNonNull(reportedAmount);
            nonnegative(reportedAmount);
            inclusions = List.copyOf(inclusions);
            var refs = inclusions.stream().map(Inclusion::event).toList();
            if (new HashSet<>(refs).size() != refs.size()) {
                throw new IllegalArgumentException("Duplicate event within one savings category.");
            }
        }

        /** Returns explained value, not an additional financial benefit. */
        public BigDecimal explainedAmount() {
            return inclusions.stream().map(Inclusion::includedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Preserves negative overexplained amounts for review. */
        public BigDecimal difference() {
            return reportedAmount.subtract(explainedAmount());
        }
    }

    /** Validates identifiers while preserving collection order. */
    private static void unique(List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate observation identifier.");
        }
    }

    /** Requires nonblank text without normalization. */
    private static void text(String value, String label) {
        Objects.requireNonNull(value, label + " cannot be null.");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank.");
        }
    }

    /** Retains the distinction between optional and blank recorded text. */
    private static void optionalText(String value, String label) {
        if (value != null) {
            text(value, label);
        }
    }

    /** Validates optional nonnegative observations without rounding. */
    private static void nonnegative(BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException("Activity and savings amounts cannot be negative.");
        }
    }
}
