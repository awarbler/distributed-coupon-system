package coupons;

import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/** Presents recorded facts and independent reconciliations without mutating either. */
public final class ReceiptReport {
    static String earningStatusText(Earning e) {
        var benefit=e.confirmed()==null?e.expected():e.confirmed();
        return switch(e.status()) {
            case EXPECTED -> "Expected, not confirmed";
            case SUBMITTED -> "Submitted, awaiting result";
            case PENDING -> "Pending";
            case APPROVED -> "Approved / earned";
            case CREDITED -> benefit!=null && benefit.kind()==BenefitKind.POINTS?"Points added to account":"Added to program account";
            case PAID -> e.kind()==EarningKind.REGISTER_REWARD?"Register Reward Printed":"Received / delivered";
            case REJECTED -> e.kind()==EarningKind.REGISTER_REWARD?"Rejected / not issued":"Rejected";
        };
    }

    private static final Currency USD = Currency.getInstance("USD");

    /** Utility class. */
    private ReceiptReport() { }

    /** Human-readable selection referencing one existing event. */
    public record Source(EventReference reference,
            ReceiptObservationValidator.EventValue value, String description) { }

    /** Lists actual monetary events in entry order, never points or unconfirmed estimates. */
    public static List<Source> sources(ReceiptSnapshot snapshot) {
        var events = ReceiptObservationValidator.monetaryEvents(snapshot);
        List<Source> result = new ArrayList<>();
        for (var coupon : snapshot.appliedCoupons()) {
            var ref = new EventReference(EventKind.COUPON_APPLIED, coupon.getApplicationId());
            String items = snapshot.purchaseItems().stream()
                    .filter(line -> coupon.getLineItemIds().contains(line.lineItemId()))
                    .map(line -> title(line.productName())).reduce((a, b) -> a + ", " + b).orElse("association unknown");
            result.add(new Source(ref, events.get(ref), title(coupon.getPrintedLabel()) + " | " + (coupon.isWholeReceipt()?"Whole receipt":items)));
        }
        for (var payment : snapshot.payments()) {
            var ref = new EventReference(EventKind.PAYMENT, payment.paymentId());
            result.add(new Source(ref, events.get(ref), "Payment/redemption " + payment.paymentType()));
        }
        for (var rr : snapshot.registerRewardRedemptions()) {
            var ref = new EventReference(EventKind.REGISTER_REWARD_REDEMPTION, rr.getRedemptionId());
            result.add(new Source(ref, events.get(ref), "Register Reward redeemed (known instrument)"));
        }
        for (var rr : snapshot.observations().unidentifiedRR()) {
            var ref = new EventReference(EventKind.UNIDENTIFIED_RR, rr.eventId());
            result.add(new Source(ref, events.get(ref), "Register Reward redeemed (instrument unknown)"));
        }
        for (var earning : snapshot.observations().earnings()) {
            var ref = new EventReference(EventKind.EARNING, earning.earningId());
            if (events.containsKey(ref)) {
                result.add(new Source(ref, events.get(ref), "Earned " + earning.kind() + " | "
                        + earning.provider() + " | " + earning.offerReference()));
            }
        }
        return List.copyOf(result);
    }

    /** Receipt-like presentation; formatting never changes stored precision or recorded facts. */
    public static void print(ReceiptRecord receipt, PrintStream output) {
        CouponTransaction transaction = receipt.transaction();
        ReceiptObservations observations = transaction.getObservations();
        TransactionCalculator calculator = new TransactionCalculator();
        ReceiptIssues.summary(receipt,output);
        rule(output);
        output.println(receipt.metadata() == null ? "RECEIPT" : title(receipt.metadata().retailer()));
        output.println("Receipt: " + (receipt.metadata() == null ? "unknown" : receipt.metadata().receiptNumber()));
        output.println("Date: " + (observations.purchaseDate() == null ? "unknown" : observations.purchaseDate())
                + " | Time: " + ReceiptTimeInput.display(observations.printedTime()));
        rule(output);
        int receiptLine = 0;
        for (PurchaseItem item : transaction.getPurchaseItems()) {
            output.println("Line " + (++receiptLine) + ": " + title(item.getProductName()));
            output.println("  SKU / UPC: " + (item.getProductCode() == null ? "Unknown" : item.getProductCode().toUpperCase(java.util.Locale.ROOT)));
            row(output, "  " + item.getQuantity() + " @ " + money(item.getSaleUnitPrice()), item.calculateLineTotal());
            if (item.getRegularUnitPrice() != null) row(output, "  Regular unit price", item.getRegularUnitPrice());
            BigDecimal store = transaction.getAppliedCoupons().stream()
                    .filter(c -> c.getLineItemIds().equals(List.of(item.getLineItemId()))
                            && observations.discountKinds().get(c.getApplicationId()) == DiscountKind.STORE)
                    .map(CouponApplied::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (store.signum() != 0) row(output, "  Store discount", store.negate());
            if (store.signum() != 0) row(output, "  After store discounts", item.calculateLineTotal().subtract(store));
            BigDecimal sale = transaction.getAppliedCoupons().stream()
                    .filter(c -> c.getLineItemIds().equals(List.of(item.getLineItemId()))
                            && observations.discountKinds().get(c.getApplicationId()) == DiscountKind.SALE_PROMOTION)
                    .map(CouponApplied::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sale.signum() != 0) {
                row(output, "  Sale/promotion reduction", sale.negate());
                row(output, "  After sale/promotion reductions", item.calculateLineTotal().subtract(sale));
            }
            output.println();
        }
        output.println("COUPONS APPLIED");
        for (CouponApplied coupon : transaction.getAppliedCoupons()) {
            if (observations.discountKinds().get(coupon.getApplicationId()) == DiscountKind.STORE
                    || observations.discountKinds().get(coupon.getApplicationId()) == DiscountKind.SALE_PROMOTION
                    && coupon.getLineItemIds().size() == 1) continue;
            row(output, coupon.getPrintedLabel(), coupon.getAppliedAmount().negate());
            output.println("  " + (coupon.isWholeReceipt()?"Whole receipt":RegisterRewardLabel.products(transaction,coupon.getLineItemIds())));
            if (coupon.getRequiredQuantity() != null) output.println("  Requires " + coupon.getRequiredQuantity() + " products per coupon");
        }
        BigDecimal rr = calculator.calculateRegisterRewardsRedeemed(transaction);
        if (rr.signum() != 0) {
            row(output, "Register Rewards redeemed", rr.negate());
            for (UnidentifiedRR used : observations.unidentifiedRR()) {
                output.println("  Source: " + title(used.recordedDescription())
                        + (observations.unidentifiedRR().size() > 1 ? " (" + money(used.amount()) + ")" : ""));
            }
        }
        rule(output);
        BigDecimal subtotal = calculator.calculateAfterCouponTotal(transaction).subtract(rr);
        row(output, "SUBTOTAL (calculated)", subtotal);
        row(output, "SALES TAX", transaction.getReceiptTotals().getTaxAmount());
        BigDecimal wc = calculator.calculateWalgreensCashRedeemed(transaction);
        boolean walgreensReceipt = receipt.metadata() != null && receipt.metadata().retailer().trim().equalsIgnoreCase("Walgreens");
        row(output, walgreensReceipt || wc.signum() != 0 ? "TOTAL before Walgreens Cash" : "TOTAL",
                subtotal.add(transaction.getReceiptTotals().getTaxAmount()));
        if (wc.signum() != 0) row(output, "Walgreens Cash redeemed", wc.negate());
        for (Payment used : transaction.getPayments()) {
            if (used.getPaymentType() == PaymentType.WALGREENS_CASH && used.getNotes() != null) {
                output.println("  Used For: " + title(used.getNotes()));
            }
        }
        row(output, "Retailer-reported total:", transaction.getReceiptTotals().getFinalTotal());
        BigDecimal difference = calculator.calculateReceiptDifference(transaction);
        if (difference.signum() != 0) {
            row(output, "Calculated checkout:", calculator.calculateExpectedCheckoutAmount(transaction));
            row(output, "Difference (reported minus calculated):", difference);
        }
        var tenders = transaction.getPayments().stream().filter(p -> p.getPaymentType() != PaymentType.WALGREENS_CASH
                && p.getPaymentType() != PaymentType.REGISTER_REWARD).toList();
        output.println();
        output.println("PAYMENT");
        if (tenders.isEmpty()) output.println("Cash / card / gift-card payment not recorded.");
        else tenders.forEach(p -> {
            row(output, p.getPaymentType().name().replace('_', ' '), p.getAmount());
            if (p.getAccountName() != null) output.println("  Redeemed Account: " + p.getAccountName());
        });
        if (observations.footerStatus() == FooterStatus.NOT_PRINTED)
            output.println("Walgreens savings/rewards footer: Not printed.");
        if (!observations.savings().isEmpty()) {
            rule(output);
            output.println("PRINTED SAVINGS");
            for (SavingsCategory category : observations.savings()) {
                String printed = switch (category.printedLabel()) {
                    case "myWalgreens Savings" -> "Receipt myWalgreens Savings";
                    case "Mfg coupon savings" -> "Receipt MFG Coupon savings";
                    case "Your total Savings" -> "Receipt Total Savings";
                    default -> category.printedLabel();
                };
                String label = printed + (USD.equals(category.currency()) ? "" : " (" + category.currency() + ")");
                row(output, label, category.reportedAmount());

            }
        }
        if (observations.receiptSavingValuePercent() != null) output.printf("%-39s %10s%n", "Receipt Saving Value",
                observations.receiptSavingValuePercent().toPlainString() + "%");
        WalgreensCashAccount account = observations.walgreensCash();
        if (account != null) {
            rule(output);
            output.println("WALGREENS CASH ACCOUNT — printed balances");
            row(output, "Receipt Opening Balance", account.startingBalance());
            row(output, "Receipt Earned This Visit", account.earned());
            row(output, "Receipt Redeemed This Visit", account.used());
            row(output, "Receipt Closing Balance", account.endingBalance());
            if (account.difference().isPresent() && account.difference().orElseThrow().signum() != 0) {
                row(output, "Calculated closing balance", account.calculatedEnding().orElse(null));
                row(output, "Balance difference", account.difference().orElse(null));
            }
            if (account.used() != null && account.used().compareTo(wc) != 0) {
                row(output, "Account used minus checkout used", account.used().subtract(wc));
            }
            BigDecimal recorded = observations.earnings().stream()
                    .filter(e -> e.kind() == EarningKind.WALGREENS_CASH && e.confirmed() != null && e.status() != RebateStatus.REJECTED)
                    .map(e -> e.confirmed().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (account.earned() != null && account.earned().compareTo(recorded) != 0) {
                row(output, "Earned minus recorded components", account.earned().subtract(recorded));
                output.println("  Earning breakdown may be incomplete.");
            }
        }
        if (!observations.earnings().isEmpty()) {
            rule(output);
            output.println("REWARDS & REBATES EARNED");
            output.println("Separate from the amount due above.");
            for (var kind : List.of(EarningKind.REGISTER_REWARD, EarningKind.WALGREENS_CASH, EarningKind.THIRD_PARTY_REBATE)) {
                var group = observations.earnings().stream().filter(e -> e.kind() == kind).toList();
                if (group.isEmpty()) continue;
                output.println("\n" + switch (kind) { case REGISTER_REWARD -> "REGISTER REWARDS"; case WALGREENS_CASH -> "WALGREENS CASH"; default -> "OTHER REBATES"; });
            for (Earning earning : group) {
                boolean alreadyInAccount = earning.kind() == EarningKind.WALGREENS_CASH && earning.scope() == Scope.RECEIPT
                        && earning.confirmed() != null && earning.status() != RebateStatus.REJECTED
                        && earning.offerReference() == null && earning.recordedLifecycle() == null
                        && (earning.requirement() == null || earning.requirement().type() != PromotionType.SPEND)
                        && account != null && account.earned() != null
                        && observations.earnings().stream().filter(e -> e.kind() == EarningKind.WALGREENS_CASH).count() == 1
                        && earning.confirmed().amount().compareTo(account.earned()) == 0;
                if (!alreadyInAccount) printEarning(earning, transaction, output);
            }
            }
            for (PurchaseItem item : transaction.getPurchaseItems()) {
                List<Earning> cash = observations.earnings().stream()
                        .filter(e -> e.scope() == Scope.ITEM && e.lineItemIds().equals(List.of(item.getLineItemId())) && projectedCash(e) != null).toList();
                if (cash.isEmpty()) continue;
                BigDecimal rebates = cash.stream().map(ReceiptReport::projectedCash).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal discounts = transaction.getAppliedCoupons().stream()
                        .filter(c -> c.getLineItemIds().equals(List.of(item.getLineItemId())))
                        .map(CouponApplied::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                output.println(title(item.getProductName()) + " — Cash Rebate Estimate");
                row(output, "  Cash rebates", rebates);
                row(output, "  After cash rebates, before tax", item.calculateLineTotal().subtract(discounts).subtract(rebates));
                output.println("  Conditional on receiving these rebates.");
            }
        }
        boolean walgreens = receipt.metadata() != null && receipt.metadata().retailer().trim().equalsIgnoreCase("Walgreens");
        boolean rrEarned = observations.earnings().stream().anyMatch(e -> e.kind() == EarningKind.REGISTER_REWARD);
        boolean wcEarned = observations.earnings().stream().anyMatch(e -> e.kind() == EarningKind.WALGREENS_CASH);
        if (walgreens || rrEarned || wcEarned || account != null) {
            rule(output);
            output.println("REWARDS EARNED TOTALS");
            output.println("+--------------------------+--------------------------------------------------------------------+");
            if (walgreens || rrEarned) detail(output, "Register Rewards earned", money(confirmedTotal(observations, EarningKind.REGISTER_REWARD)) + " RR");
            if (walgreens || wcEarned || account != null) {
                BigDecimal earned = account != null && account.earned() != null ? account.earned()
                        : confirmedTotal(observations, EarningKind.WALGREENS_CASH);
                detail(output, "Walgreens Cash earned", money(earned) + " WC");
            }
            output.println("+--------------------------+--------------------------------------------------------------------+");
        }
        SavingsReport.printReceiptTotals(receipt, output);
        rule(output);
        if (!observations.promotionLinks().isEmpty()) {
            output.println("ONLINE REBATE ELIGIBILITY — progress only; no gift card received or used");
            for (var link : observations.promotionLinks()) {
                output.println("  Eligibility note: " + link.eligibilityNote());
                output.println("  Eligible products: " + RegisterRewardLabel.products(transaction,link.eligibleLineIds()));
                if (link.reviewedEligibleAmount() != null) output.println("  Reviewed eligible USD: " + link.reviewedEligibleAmount());
            }
        }
        if (!receipt.evidenceIds().isEmpty()) output.println("Attached Receipt Files: " + receipt.evidenceIds().size());
    }

    /** One block per benefit; expected and received values are never added together. */
    private static void printEarning(Earning e, CouponTransaction transaction, PrintStream output) {
        String name = e.kind() == EarningKind.REGISTER_REWARD ? "Register Reward" : e.kind() == EarningKind.WALGREENS_CASH ? "Walgreens Cash" : e.provider();
        output.println("+--------------------------+--------------------------------------------------------------------+");
        detail(output, "Offer", e.kind() == EarningKind.REGISTER_REWARD ? RegisterRewardLabel.offer(transaction,e)
                : name + (e.offerReference() == null ? "" : " — " + e.offerReference()));
        String products = RegisterRewardLabel.products(transaction,e);
        if (e.kind() == EarningKind.REGISTER_REWARD) detail(output,"Receipt lines",RegisterRewardLabel.lines(transaction,e));
        else detail(output, "Products", products);
        if (e.expected() != null && (e.confirmed() == null || !sameBenefit(e.expected(), e.confirmed()))) detail(output, "Expected", earningValue(e, e.expected()));
        if (e.confirmed() != null) detail(output, "Recorded Earned", earningValue(e, e.confirmed()));
        detail(output, "Status", earningStatusText(e));
        if (e.kind() == EarningKind.REGISTER_REWARD || e.expirationDate() != null) detail(output, "Expires", e.expirationDate() == null ? "unknown" : e.expirationDate().toString());
        if (e.requirement() != null && e.requirement().type() == PromotionType.SPEND) {
            var check = RewardSpendCheck.evaluate(e, transaction);
            detail(output, "Required spend", "$" + money(check.requiredSpend()));
            detail(output, "Basis", e.requirement().spendBasis() == SpendBasis.SALE_BEFORE_COUPONS ? "After sale/promotion reductions, before coupons" : "Other / unknown");
            if (check.status() == RewardSpendCheck.Status.CANNOT_VERIFY) detail(output, "Cannot verify", check.reason());
            else {
                detail(output, "Qualifying spend", "$" + money(check.qualifyingSpend()));
                detail(output, "Spend requirement", check.status() == RewardSpendCheck.Status.MET ? "Met — $" + money(check.qualifyingSpend().subtract(check.requiredSpend())) + " above threshold" : "Not met — $" + money(check.requiredSpend().subtract(check.qualifyingSpend())) + " short");
            }
        }
        if (e.recordedLifecycle() != null) detail(output, "Note", e.recordedLifecycle());
        if (e.valuation() != null) detail(output, "Recorded Value", e.valuation().amount().toPlainString() + " " + e.valuation().currency() + " (" + e.valuation().basis() + ")");
        output.println("+--------------------------+--------------------------------------------------------------------+");
    }
    private static void detail(PrintStream out, String label, String text) {
        text = text == null || text.isEmpty() ? "unknown" : text;
        boolean first = true;
        while (!text.isEmpty()) {
            int end = Math.min(66, text.length());
            if (end < text.length()) { int space = text.lastIndexOf(' ', end); if (space > 0) end = space; }
            out.printf("| %-24s | %-66s |%n", first ? label : "", text.substring(0,end));
            text = text.substring(end).stripLeading(); first = false;
        }
    }

    private static boolean earned(Earning e) { return SavingsReport.earned(e); }
    private static BigDecimal earnedDollars(Earning e) { return SavingsReport.earnedDollars(e); }
    private static boolean sameBenefit(RebateBenefit a, RebateBenefit b) {
        return a.kind() == b.kind() && a.amount().compareTo(b.amount()) == 0
                && java.util.Objects.equals(a.currency(), b.currency()) && java.util.Objects.equals(a.program(), b.program());
    }

    private static BigDecimal projectedCash(Earning earning) {
        if (earning.kind() != EarningKind.THIRD_PARTY_REBATE || earning.status() == RebateStatus.REJECTED) return null;
        RebateBenefit benefit = earning.confirmed() == null ? earning.expected() : earning.confirmed();
        return benefit != null && benefit.kind() == BenefitKind.CASH && USD.equals(benefit.currency()) ? benefit.amount() : null;
    }

    /** Count each confirmed earning once; expected and rejected amounts are not earned totals. */
    private static BigDecimal confirmedTotal(ReceiptObservations observations, EarningKind kind) {
        return observations.earnings().stream().filter(e -> e.kind() == kind && earned(e))
                .map(e -> e.confirmed().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Capitalizes displayed words without lowercasing acronyms or modifying stored text. */
    static String title(String text) {
        if (text == null) return "Unknown";
        StringBuilder result = new StringBuilder();
        boolean wordStart = true;
        for (int offset = 0; offset < text.length();) {
            int letter = text.codePointAt(offset);
            result.appendCodePoint(wordStart && Character.isLetter(letter) ? Character.toUpperCase(letter) : letter);
            wordStart = !Character.isLetterOrDigit(letter) && letter != '\'';
            offset += Character.charCount(letter);
        }
        return result.toString();
    }

    /** Receipt money is displayed to cents; original stored amounts are never rounded. */
    private static String money(BigDecimal value) {
        return value == null ? "unknown" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static void row(PrintStream output, String label, BigDecimal amount) {
        output.printf("%-39s %10s%n", title(label), money(amount));
    }

    private static void rule(PrintStream output) { output.println("--------------------------------------------------"); }

    private static String earningValue(Earning earning, RebateBenefit benefit) {
        if (earning.kind() == EarningKind.REGISTER_REWARD) return money(benefit.amount()) + " RR";
        if (earning.kind() == EarningKind.WALGREENS_CASH) return money(benefit.amount()) + " Walgreens Cash";
        if (benefit.kind() == BenefitKind.POINTS) return benefit.amount().toPlainString() + " points";
        return money(benefit.amount()) + " " + benefit.currency();
    }
}
