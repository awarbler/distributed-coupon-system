package coupons;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

import coupons.ReceiptObservations.DiscountKind;
import coupons.ReceiptObservations.Earning;
import coupons.ReceiptObservations.EarningKind;
import coupons.ReceiptObservations.EventReference;
import coupons.ReceiptObservations.Inclusion;
import coupons.ReceiptObservations.SavingsCategory;
import coupons.ReceiptObservations.Scope;
import coupons.ReceiptObservations.UnidentifiedRR;
import coupons.ReceiptObservations.WalgreensCashAccount;
import coupons.repository.InMemoryTransactionRepository;
import coupons.repository.ReceiptCreateCtx;
import coupons.repository.ReceiptEvidence;
import coupons.repository.ReceiptMeta;
import coupons.repository.ReceiptRecord;
import coupons.repository.ReceiptSnapshot;
import coupons.repository.ReceiptStatus;
import coupons.repository.ReceiptWriteRequest;
import coupons.repository.ReceiptWriteResult;
import coupons.repository.TransactionRepository;

/**
 * Receipt-first manual entry with review/edit and immutable captured retries.
 * The main entry point saves receipts locally; embedded constructors may use memory-only stores. Provider benefits are earning
 * observations, not account credits, payouts, or automatic available balances.
 */
public final class ReceiptEntryApp {
    private final Scanner input;
    private final PrintStream output;
    private final InMemoryTransactionRepository repository;
    private final List<UUID> savedIds = new ArrayList<>();
    private PendingSave last;
    private final ProviderBalanceSummary balances = new ProviderBalanceSummary();

    /** Returns from the console without terminating the JVM used by callers or tests. */
    private static final class ExitCli extends RuntimeException { }

    /** Enables navigation commands only while collecting the current item. */
    private boolean readingItem;
    private boolean readingBalances;
    private static final class ReturnToMenu extends RuntimeException { }
    private static final class PreviousItemQuestion extends RuntimeException { }

    /** Enables previous-question navigation during an unsaved earning. */
    private boolean readingEarning;

    /** Signals navigation, without discarding the receipt or prior earning answers. */
    private static final class PreviousEarningQuestion extends RuntimeException { }

    /** Cancels one unsaved item without discarding previously entered receipt facts. */
    private static final class ItemEntryCancelled extends RuntimeException { }

    /** Creates a single-user console session with an isolated in-memory store. */
    public ReceiptEntryApp(Scanner input, PrintStream output) {
        this(input, output, new InMemoryTransactionRepository());
    }

    /** Allows integration tests to inspect the same repository used by the console. */
    ReceiptEntryApp(Scanner input, PrintStream output, InMemoryTransactionRepository repository) {
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.repository = Objects.requireNonNull(repository);
        var history = repository.creationHistory();
        history.forEach(entry -> savedIds.add(entry.after().transactionId()));
        if (!history.isEmpty()) {
            var entry = history.get(history.size() - 1);
            var original = entry.after();
            last = new PendingSave(original.transactionId(), original.contents(), original.metadata(),
                    original.evidenceIds().stream().map(id -> repository.evidence().findById(id).orElseThrow()).toList(),
                    entry.request(), entry.createCtx());
        }
    }

    /** Launches entry without changing the original demonstration. */
    public static void main(String[] args) {
        Path file = args.length == 0 ? Path.of(".coupon-data", "receipts.bin") : Path.of(args[0]);
        try (var repository = InMemoryTransactionRepository.openLocal(file)) {
            new ReceiptEntryApp(new Scanner(System.in), System.out, repository).run();
        } catch (IOException | IllegalStateException exception) {
            System.err.println("Could not open receipt storage. Existing data was not replaced: " + exception.getMessage());
        }
    }

    /** Accepts a receipt number directly as well as menu choices. */
    public void run() {
        if (repository.storagePath().isPresent()) {
            output.println("Receipt entry — saved receipts are kept in " + repository.storagePath().orElseThrow());
            output.println("Loaded saved receipts: " + savedIds.size());
        } else output.println("Receipt entry — records last only for this session.");
        output.println("Type a receipt number to begin, or choose an action below.");
        while (true) {
            output.println();
            output.println("NEW / 1 Enter | 2 Retrieve | 3 Retry | 4 History | 5 RR Coupons (View/Add/Delete) | 6 Exit | 7 Balances");
            try {
                String choice = text("Receipt number or action");
                switch (choice.trim().toUpperCase(Locale.ROOT)) {
                    case "NEW", "1" -> enterReceipt(null);
                    case "2" -> selectReceipt().ifPresent(this::display);
                    case "3" -> {
                        if (last == null) output.println("No captured save request.");
                        else {
                            boolean alreadySaved = repository.history().findByRequestId(last.request().requestId()).isPresent();
                            ReceiptWriteResult result = last.submit(repository);
                            output.println(alreadySaved ? "Already Saved — No Duplicate Receipt Or History Entry Created." : "Save Completed.");
                            output.println("Original result: " + writeSummary(result));
                            output.println("Saved At: " + readableTimestamp(result.committedAt()));
                        }
                    }
                    case "4" -> selectReceipt().ifPresent(this::displayHistory);
                    case "5" -> enterReward();
                    case "6", "EXIT" -> { return; }
                    case "7" -> balanceSettings();
                    default -> {
                        if (choice.isBlank()) output.println("Enter a receipt number or an action.");
                        else enterReceipt(choice);
                    }
                }
            } catch (ExitCli exit) {
                output.println("CLI closed.");
                return;
            } catch (NoSuchElementException exception) {
                output.println("Input ended; incomplete entry was not saved.");
                return;
            } catch (IllegalArgumentException | IllegalStateException | IOException exception) {
                output.println("Operation not saved: " + exception.getMessage());
            }
        }
    }

    
    /** Collects a draft before asking for a single final save confirmation. */
    private void enterReceipt(String suppliedNumber) throws IOException {
        Draft draft = new Draft();
        output.println("NEW RECEIPT");
        draft.number = suppliedNumber == null ? optional("Receipt number (blank unknown)") : suppliedNumber;
        details(draft);
        output.println(
                "Enter purchased products only. Tax comes after the items."
        );
        

        while (true) {
            Item item = readItem(null);

            if (item == null) {
                if (draft.items.isEmpty()) {
                    output.println(
                            "Enter at least one purchased item before checkout."
                    );
                    continue;
                }

                break;
            }

            draft.items.add(item);
            itemEarnings(draft, item);

            if (!yes(
                    "Add another purchased item? No continues to tax and checkout",
                    false)) {
                break;
            }
        }
        
        checkout(draft);
        earnings(draft);
        if (isWalgreens(draft)) {
            savings(draft);
            account(draft);
        } else {
            account(draft);
            savings(draft);
        }
        if (yes("Attach a receipt photo or PDF?", false)) evidence(draft);
        while (true) {
            output.println("REVIEW — all changes are still unsaved");
            display(draft.receipt());
            String action = text("SAVE / EDIT / CANCEL / EXIT").trim().toUpperCase(Locale.ROOT);
            if (action.equals("EXIT")) {
                if (yes(repository.storagePath().isPresent() ? "Exit CLI? Discard this unsaved receipt; saved receipts stay on disk"
                        : "Exit CLI? This unsaved receipt and session-only records will be lost", false)) throw new ExitCli();
                continue;
            }
            if (action.equals("CANCEL")) {
                output.println("Cancelled; nothing saved.");
                return;
            }
            if (action.equals("SAVE")) {
                last = new PendingSave(draft.id, ReceiptSnapshot.from(draft.transaction()),
                        draft.metadata(), List.copyOf(draft.evidence),
                        new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx(draft.actor));
                ReceiptWriteResult result = last.submit(repository);
                savedIds.add(draft.id);
                output.println("Save result: " + writeSummary(result));
                display(repository.findById(draft.id).orElseThrow());
                return;
            }
            if (!action.equals("EDIT")) continue;
            String section = text("Edit: DETAILS / ITEM / CHECKOUT / EARNINGS / ACCOUNT / SAVINGS / SAVINGS LINKS / EVIDENCE")
                    .trim().toUpperCase(Locale.ROOT);
            switch (section) {
                case "DETAILS" -> {
                    draft.number = optional("Receipt number (blank unknown)");
                    details(draft);
                }
                case "ITEM" -> {
                    showItems(draft);
                    int index = integer("Item number to edit (0 adds an item)", 0, draft.items.size()) - 1;
                    Item edited = readItem(index < 0 ? null : draft.items.get(index));
                    if (edited != null) {
                        if (index < 0) draft.items.add(edited);
                        else draft.items.set(index, edited);
                        clearSavings(draft);
                    }
                }
                case "CHECKOUT" -> { checkout(draft); clearSavings(draft); }
                case "EARNINGS" -> { editEarnings(draft); clearSavings(draft); }
                case "ACCOUNT" -> account(draft);
                case "SAVINGS" -> savings(draft);
                case "SAVINGS LINKS" -> detailedSavings(draft);
                case "EVIDENCE" -> evidence(draft);
                default -> output.println("Choose one of the named sections.");
            }
        }
    }

    /** Keeps partial date/time observations without inventing missing components. */
    private void details(Draft draft) {
        draft.time = receiptTime();
        draft.date = date("Receipt date (for example 10/4/26, 26/10/4, or October 4, 2026; blank unknown)");
        draft.retailer = required("Retailer");
        draft.actor = required("Entered/confirmed by");
    }

    /** DONE cancels the item; BACK moves within its unanswered draft. */
    private Item readItem(Item previous) {
        output.println("Type BACK for the previous item question; DONE discards this unsaved item and continues to checkout.");
        readingItem = true;
        try {
            return readItemFields(previous);
        } catch (ItemEntryCancelled exception) {
            output.println("Current item discarded.");
            return null;
        } finally {
            readingItem = false;
        }
    }

    /** Records each item's discounts immediately after price and quantity. */
    private Item readItemFields(Item previous) {
        output.println("ITEM");
        String code = null, name = null;
        BigDecimal price = null, store = null, value = null;
        int quantity = 1, coupons = 0, step = 0;
        Integer requiredQuantity = null;
        var visited = new java.util.ArrayDeque<Integer>();
        while (step < 8) {
            boolean asked = true;
            try {
                switch (step) {
                    case 0 -> code = optional("SKU / UPC (blank unknown; preserve leading zeros)");
                    case 1 -> name = required("Item description");
                    case 2 -> price = amount("Unit price before the separately entered discounts", false, false);
                    case 3 -> {
                        quantity = integer("Quantity", 1, Integer.MAX_VALUE);
                        output.println("Calculated line total: " + price.multiply(BigDecimal.valueOf(quantity)).toPlainString());
                    }
                    case 4 -> store = amount("Total Store Coupons and Promos", false, false);
                    case 5 -> coupons = integer("Quantity of MQs (0 if none)", 0, 1000);
                    case 6 -> {
                        if (coupons > 0) value = amount("Value of MQs", false, true);
                        else { value = null; asked = false; }
                    }
                    case 7 -> {
                        if (coupons > 0) requiredQuantity = optionalQuantity("Items required per MQ (e.g. 2; blank unknown)");
                        else { requiredQuantity = null; asked = false; }
                    }
                    default -> throw new IllegalStateException("Unknown item question.");
                }
                if (asked) visited.push(step);
                step++;
            } catch (PreviousItemQuestion navigation) {
                if (visited.isEmpty()) throw new ItemEntryCancelled();
                step = visited.pop();
                output.println("Back to previous item question; earlier answers retained.");
            }
        }
        UUID id = previous == null ? UUID.randomUUID() : previous.item().getLineItemId();
        PurchaseItem item = new PurchaseItem(id, name, code, null, price, quantity);
        List<CouponApplied> deductions = new ArrayList<>();
        Map<UUID, DiscountKind> kinds = new LinkedHashMap<>();
        if (store.signum() > 0) {
            CouponApplied applied = new CouponApplied("Store discount", store, List.of(id));
            deductions.add(applied);
            kinds.put(applied.getApplicationId(), DiscountKind.STORE);
        }
        output.println("After store discounts: " + item.calculateLineTotal().subtract(store).toPlainString());
        BigDecimal manufacturer = BigDecimal.ZERO;
        if (coupons > 0) {
            manufacturer = value.multiply(BigDecimal.valueOf(coupons));
            for (int i = 0; i < coupons; i++) {
                CouponApplied applied = new CouponApplied(UUID.randomUUID(), "Manufacturer coupon", value, List.of(id), requiredQuantity);
                deductions.add(applied);
                kinds.put(applied.getApplicationId(), DiscountKind.MANUFACTURER);
            }
        }
        output.println("Manufacturer coupons applied: " + coupons);
        if (requiredQuantity != null) output.println("Each manufacturer coupon requires: " + requiredQuantity + " products");
        output.println("Manufacturer coupon total: " + manufacturer.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        output.println("Combined line discounts: " + store.add(manufacturer).toPlainString());
        output.println("After discounts: " + item.calculateLineTotal().subtract(store).subtract(manufacturer).toPlainString());
        return new Item(item, List.copyOf(deductions), Map.copyOf(kinds));
    }

    /** Separates checkout deductions from rewards earned and printed savings. */
    private void checkout(Draft draft) {
        draft.tax = amount("Tax printed on receipt", false, false);
        draft.unknownRR.clear();
        draft.knownRR.clear();
        int rewardCount = integer("How many separate Register Reward coupons did you use? (0 if none)", 0, 1000);
        BigDecimal rrTotal = BigDecimal.ZERO;
        for (int index = 0; index < rewardCount; index++) {
            output.println("REGISTER REWARD USED " + (index + 1) + " OF " + rewardCount);
            BigDecimal value = amount("Amount", false, true);
            draft.unknownRR.add(new UnidentifiedRR(UUID.randomUUID(), value, rrDescription()));
            rrTotal = rrTotal.add(value);
        }
        output.println("Total Register Rewards used: " + rrTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        draft.payments.clear();
        BigDecimal wc = amount("Walgreens Cash used (0 if none)", false, false);
        if (wc.signum() > 0) draft.payments.add(new Payment(UUID.randomUUID(), PaymentType.WALGREENS_CASH, wc,
                optional("Walgreens Cash used for (blank unknown)")));
        while (true) {
            output.println("PAYMENT METHOD");
            output.println("1. Cash | 2. Card | 3. Gift card payment | 4. Other with notes | 5. Not recorded");
            String selection = text("Payment method").trim();
            // Accept the old skip response without adding an extra yes/no question.
            if (selection.equalsIgnoreCase("n") || selection.isEmpty() || selection.equals("5")) break;
            if (selection.equalsIgnoreCase("y")) continue;
            int selected;
            try { selected = Integer.parseInt(selection); }
            catch (NumberFormatException exception) { output.println("Choose 1 through 5."); continue; }
            if (selected < 1 || selected > 4) { output.println("Choose 1 through 5."); continue; }
            PaymentType type = switch (selected) {
                case 1 -> PaymentType.CASH;
                case 2 -> PaymentType.CARD;
                case 3 -> PaymentType.GIFT_CARD;
                default -> PaymentType.OTHER;
            };
            BigDecimal paid = amount("Payment amount", false, true);
            String notes = selected == 4 ? optional("Other payment notes (blank unknown)") : null;
            draft.payments.add(new Payment(UUID.randomUUID(), type, paid, notes));
            if (!yes("Another payment method?", false)) break;
        }
        draft.total = amount("Final total PRINTED on receipt (kept separate from calculation)", false, false);
    }

    /** A description preserves the entered source without claiming a verified issued instrument. */
    private String rrDescription() {
        String description = optional("Source (e.g. Palmolive; blank unknown)");
        return description == null ? "Receipt-reported RR used; source unknown" : description;
    }

    /** VIEW inspects existing draft earnings without adding or changing any event. */
    private boolean addEarning(Draft draft, String question) {
        while (true) {
            String answer = text(question + " [y/N; VIEW lists rewards already entered]").trim();
            if (answer.equalsIgnoreCase("y") || answer.equals("1")) return true;
            if (answer.isEmpty() || answer.equalsIgnoreCase("n") || answer.equals("3")) return false;
            if (answer.equalsIgnoreCase("VIEW") || answer.equalsIgnoreCase("v") || answer.equals("2")) {
                output.println("REWARDS ALREADY ENTERED — " + draft.earnings.size());
                if (draft.earnings.isEmpty()) output.println("None entered yet.");
                for (int i = 0; i < draft.earnings.size(); i++) {
                    Earning e = draft.earnings.get(i);
                    RebateBenefit benefit = e.confirmed() == null ? e.expected() : e.confirmed();
                    String name = e.kind() == EarningKind.REGISTER_REWARD ? "Register Reward"
                            : e.kind() == EarningKind.WALGREENS_CASH ? "Walgreens Cash" : e.provider();
                    String products = e.scope() == Scope.RECEIPT ? "Whole receipt" : e.scope() == Scope.UNKNOWN ? "Products unknown"
                            : draft.items.stream().filter(item -> e.lineItemIds().contains(item.item().getLineItemId()))
                                    .map(item -> item.item().getProductName()).reduce((a, b) -> a + ", " + b).orElse("Products unknown");
                    output.println((i + 1) + ". " + name + " — " + benefit.amount().toPlainString()
                            + (benefit.kind() == BenefitKind.POINTS ? " points" : " USD") + " — " + products
                            + (e.confirmed() == null ? " — expected" : " — recorded earned"));
                    if (e.offerReference() != null) output.println("   Offer: " + e.offerReference());
                }
                continue;
            }
            output.println("Enter Y to add, VIEW (or 2) to review, or N to continue.");
        }
    }

    /** Corrects one unsaved earning without deleting unrelated item or receipt benefits. */
    private void editEarnings(Draft draft) {
        for (int i = 0; i < draft.earnings.size(); i++) {
            Earning e = draft.earnings.get(i);
            output.println((i + 1) + ". " + e.provider() + " | " + e.offerReference());
        }
        int selected = integer("Earning number to change (0 adds one)", 0, draft.earnings.size()) - 1;
        if (selected >= 0 && yes("Remove this earning?", false)) {
            draft.earnings.remove(selected);
            return;
        }
        Earning replacement = readEarning(draft, null);
        if (replacement == null) return;
        if (selected < 0) draft.earnings.add(replacement);
        else {
            Earning old = draft.earnings.get(selected);
            draft.earnings.set(selected, new Earning(old.earningId(), replacement.kind(), replacement.provider(),
                    replacement.offerReference(), replacement.scope(), replacement.lineItemIds(), replacement.expected(),
                    replacement.confirmed(), replacement.status(), replacement.expirationDate(), old.instrumentId(),
                    old.claimId(), old.evidenceIds(), replacement.recordedLifecycle(), replacement.requirement(), replacement.valuation()));
        }
    }

    /** Captures benefits immediately after the item, with its association already known. */
    private void itemEarnings(Draft draft, Item item) {
        while (addEarning(draft, "Add rebate for current item")) {
            Earning earning = readEarning(draft, item.item().getLineItemId());
            if (earning != null) draft.earnings.add(earning);
        }
    }

    /** Appends receipt-wide or additional benefits; never silently clears item earnings. */
    private void earnings(Draft draft) {
        output.println("Earnings do not reduce checkout. Transfers and point conversions are not new earnings.");
        while (addEarning(draft, "Add a receipt-wide or another reward / rebate?")) {
            Earning earning = readEarning(draft, null);
            if (earning != null) draft.earnings.add(earning);
        }
    }

    /** Collects ordinary receipt terms, keeping the previous answers on BACK. */
    private Earning readEarning(Draft draft, UUID itemId) {
        output.println("BACK returns to the previous question; BACK at program cancels this earning only.");
        EarningAnswers a = new EarningAnswers();
        var previous = new java.util.ArrayDeque<Integer>();
        int step = 0;
        readingEarning = true;
        try {
            while (step < 14) {
                boolean asked = true;
                try {
                    switch (step) {
                        case 0 -> {
                            a.selection = earningKind(draft.retailer);
                            a.provider = a.selection.kind() == EarningKind.THIRD_PARTY_REBATE ? a.selection.provider() : "Walgreens";
                            a.currency = a.selection.form() == BenefitKind.POINTS ? null : Currency.getInstance("USD");
                        }
                        case 1 -> {
                            if (a.selection.other()) {
                                BenefitKind form = choice("What is awarded?", BenefitKind.class);
                                a.selection = new EarningSelection(EarningKind.THIRD_PARTY_REBATE, form, true, null);
                                a.currency = form == BenefitKind.POINTS ? null : Currency.getInstance("USD");
                            } else asked = false;
                        }
                        case 2 -> {
                            if (a.selection.other()) a.provider = required("Reward / rebate program name");
                            else asked = false;
                        }
                        case 3 -> a.offer = optional("Offer (for example: Buy 2 Neutrogena, earn 7 RR; blank unknown)");
                        case 4 -> {
                            output.println("1. Buy a quantity | 2. Spend an amount | 3. Receipt | 4. Other");
                            int selected = integer("Requirement", 1, 4);
                            a.requirementType = selected == 1 ? PromotionType.BUY : selected == 2 ? PromotionType.SPEND : null;
                            a.requirementCategory = selected == 3 ? "Receipt" : selected == 4 ? "Other" : null;
                        }
                        case 5 -> {
                            if (a.requirementType == PromotionType.BUY) {
                                a.required = BigDecimal.valueOf(integer("Required amount (number of products)", 1, Integer.MAX_VALUE));
                            } else if (a.requirementType == PromotionType.SPEND) {
                                a.required = amount("Required amount (USD spend)", false, true);
                            } else { a.required = null; asked = false; }
                        }
                        case 6 -> {
                            if (itemId != null) {
                                a.lines = List.of(itemId); a.scope = Scope.ITEM; asked = false;
                            } else {
                                a.lines = earningItems(draft);
                                a.scope = a.lines == null ? Scope.UNKNOWN : a.lines.isEmpty() ? Scope.RECEIPT
                                        : a.lines.size() == 1 ? Scope.ITEM : Scope.MULTI_ITEM;
                                if (a.lines == null) a.lines = List.of();
                            }
                        }
                        case 7 -> a.entered = amount(a.selection.provider() == null ? "Reward / rebate amount"
                                : a.selection.provider() + (a.selection.form() == BenefitKind.POINTS
                                    ? " points earned / expected" : " amount earned / expected (USD)"), false, true);
                        case 8 -> {
                            output.println("1. Expected, not confirmed | 2. Submitted, awaiting result");
                            output.println("3. Approved / earned | 4. Added to program account");
                            output.println(a.selection.kind() == EarningKind.REGISTER_REWARD
                                    ? "5. Register Reward Printed | 6. Rejected / not issued"
                                    : "5. Received / delivered | 6. Rejected");
                            a.status = switch (integer("What has happened to this benefit?", 1, 6)) {
                                case 1 -> RebateStatus.EXPECTED;
                                case 2 -> RebateStatus.SUBMITTED;
                                case 3 -> RebateStatus.APPROVED;
                                case 4 -> RebateStatus.CREDITED;
                                case 5 -> RebateStatus.PAID;
                                default -> RebateStatus.REJECTED;
                            };
                        }
                        case 9 -> {
                            if (a.status == RebateStatus.APPROVED || a.status == RebateStatus.CREDITED || a.status == RebateStatus.PAID) {
                                a.confirmed = a.entered;
                                a.expected = amount("Earlier expected amount (blank if not separately recorded)", true, true);
                            } else { a.expected = a.entered; a.confirmed = null; asked = false; }
                        }
                        case 10 -> {
                            if (a.selection.form() == BenefitKind.POINTS || a.selection.form() == BenefitKind.GIFT_CARD) {
                                a.valuation = amount("Recorded USD value of THIS benefit (blank unknown; not another earning)", true, false);
                            } else { a.valuation = null; asked = false; }
                        }
                        case 11 -> {
                            if (a.valuation != null) a.basis = required("Basis for that value (for example: my spreadsheet)");
                            else { a.basis = null; asked = false; }
                        }
                        case 12 -> a.expiration = date("Expiration date (M/D/YYYY or YYYY-MM-DD, blank unknown)");
                        case 13 -> a.lifecycle = optional("Optional note (for example: expired; blank none)");
                        default -> throw new IllegalStateException("Unknown earning question.");
                    }
                    if (asked) previous.push(step);
                    step++;
                } catch (PreviousEarningQuestion navigation) {
                    if (previous.isEmpty()) return null;
                    step = previous.pop();
                    output.println("Back to the previous earning question; earlier answers retained.");
                }
            }
            return a.build();
        } finally { readingEarning = false; }
    }

    /** Chooses actual program names; these convenience choices do not verify offer eligibility. */
    private EarningSelection earningKind(String retailer) {
        List<EarningSelection> choices = new ArrayList<>();
        boolean walgreens = retailer.trim().equalsIgnoreCase("Walgreens");
        if (walgreens) {
            choices.add(new EarningSelection(EarningKind.REGISTER_REWARD, BenefitKind.STORE_COUPON, false, "Register Reward"));
            choices.add(new EarningSelection(EarningKind.WALGREENS_CASH, BenefitKind.STORE_REWARD, false, "Walgreens Cash"));
        }
        if (retailer.trim().equalsIgnoreCase("CVS")) choices.add(new EarningSelection(EarningKind.THIRD_PARTY_REBATE, BenefitKind.STORE_COUPON, false, "CVS ExtraBucks"));
        if (retailer.trim().equalsIgnoreCase("Walmart")) choices.add(new EarningSelection(EarningKind.THIRD_PARTY_REBATE, BenefitKind.STORE_REWARD, false, "Walmart Cash"));
        for (String program : List.of("Checkout 51", "Ibotta", "Venmo", "Fetch", "P&G Brand", "Swagbucks", "MyPoints", "Coupons DotCom", "PayPal", "Gift Cards", "Merryfield", "Makenna", "Shopmium", "Rakuten")) {
            BenefitKind form = List.of("Fetch", "P&G Brand", "Swagbucks", "MyPoints", "Merryfield").contains(program)
                    ? BenefitKind.POINTS : program.equals("Gift Cards") ? BenefitKind.GIFT_CARD : BenefitKind.CASH;
            choices.add(new EarningSelection(EarningKind.THIRD_PARTY_REBATE, form, false, program));
        }
        choices.add(new EarningSelection(EarningKind.THIRD_PARTY_REBATE, BenefitKind.CASH, true, "Other"));
        while (true) {
            for (int i = 0; i < choices.size(); i++) output.println((i + 1) + ". " + choices.get(i).provider());
            String answer = text("Reward / rebate program (number or full name; Other supports a different benefit form)").trim();
            try {
                int index = Integer.parseInt(answer) - 1;
                if (index >= 0 && index < choices.size()) return choices.get(index);
            } catch (NumberFormatException ignored) { }
            for (var option : choices) if (option.provider().equalsIgnoreCase(answer)) return option;
            output.println("Choose a displayed number or program name.");
        }
    }

    /** Association follows selected products; a buy-two rule does not require two receipt lines. */
    private List<UUID> earningItems(Draft draft) {
        showItems(draft);
        output.println("0. Whole receipt | U. Unknown products");
        while (true) {
            String answer = text("Applies to (item numbers separated by commas, 0, or U)").trim();
            if (answer.equals("0")) return List.of();
            if (answer.equalsIgnoreCase("U")) return null;
            try {
                List<UUID> ids = Arrays.stream(answer.split(",", -1)).map(String::trim).map(Integer::parseInt)
                        .map(i -> draft.items.get(i - 1).item().getLineItemId()).toList();
                if (new HashSet<>(ids).size() == ids.size()) return ids;
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) { }
            output.println("Choose listed products once each; two units on one line need only that one line number.");
        }
    }

    /** Records four printed account facts separately from underlying earnings. */
    private void account(Draft draft) {
        if (isWalgreens(draft)) {
            output.println("WALGREENS CASH ACCOUNT — printed balances");
            BigDecimal start = amount("Receipt Opening Balance", false, false, true);
            BigDecimal earned = amount("Receipt Earned This Visit", false, false);
            BigDecimal used = amount("Receipt Redeemed This Visit", false, false);
            BigDecimal end = amount("Receipt Closing Balance", false, false, true);
            draft.account = new WalgreensCashAccount(start, used, earned, end);
            return;
        }
        if (!yes("Enter printed Walgreens Cash account balances?", false)) {
            draft.account = null;
            return;
        }
        BigDecimal start = amount("Printed starting balance (blank unknown)", true, false, true);
        BigDecimal checkout = draft.payments.stream().filter(p -> p.getPaymentType() == PaymentType.WALGREENS_CASH)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        output.println("Redeemed this visit: " + checkout.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        BigDecimal used = yes("Does that match the printed account used amount?", true) ? checkout
                : amount("Printed account used (blank unknown)", true, false);
        BigDecimal earned = amount("Printed account earned TOTAL (blank unknown)", true, false);
        BigDecimal end = amount("Printed ending balance (blank unknown)", true, false, true);
        draft.account = new WalgreensCashAccount(start, used, earned, end);
    }

    private boolean isWalgreens(Draft draft) {
        return draft.retailer != null && draft.retailer.trim().equalsIgnoreCase("Walgreens");
    }

    /** Direct printed-value entry; no categories or arithmetic are inferred from checkout. */
    private void savings(Draft draft) {
        if (!isWalgreens(draft)) { detailedSavings(draft); return; }
        output.println("RECEIPT SAVINGS — enter the amounts printed on your receipt");
        printedSavings(draft, "myWalgreens Savings", "Receipt myWalgreens Savings");
        printedSavings(draft, "Mfg coupon savings", "Receipt MFG Coupon savings");
        printedSavings(draft, "Your total Savings", "Receipt Total Savings");
        while (true) {
            String entered = required("Receipt Saving Value (percentage, e.g. 43%)").trim();
            try {
                BigDecimal value = new BigDecimal(entered.endsWith("%") ? entered.substring(0, entered.length() - 1).trim() : entered);
                if (value.signum() < 0) throw new NumberFormatException();
                draft.savingPercent = value;
                return;
            } catch (NumberFormatException exception) {
                output.println("Enter the printed nonnegative percentage, such as 43 or 43%.");
            }
        }
    }

    private void printedSavings(Draft draft, String label, String prompt) {
        BigDecimal value = amount(prompt, false, false);
        for (int index = 0; index < draft.savings.size(); index++) {
            SavingsCategory previous = draft.savings.get(index);
            if (previous.printedLabel().equals(label)) {
                draft.savings.set(index, new SavingsCategory(previous.categoryId(), label, Currency.getInstance("USD"), value,
                        previous.inclusions(), previous.explanationComplete()));
                return;
            }
        }
        draft.savings.add(new SavingsCategory(UUID.randomUUID(), label, Currency.getInstance("USD"), value, List.of(), false));
    }

    /** Lets the entrant link existing actual events to printed savings without guessing. */
    private void detailedSavings(Draft draft) {
        draft.savings.clear();
        while (yes("Add a printed savings category or total?", false)) {
            output.println("PRINTED RECEIPT SAVINGS — all amounts are USD");
            output.println("1. myWalgreens Savings | 2. Manufacturer coupon savings");
            output.println("3. Your total Savings | 4. Other printed savings | 5. Continue to review");
            int category = integer("Which printed savings amount?", 1, 5);
            if (category == 5) return;
            String label = switch (category) {
                case 1 -> "myWalgreens Savings";
                case 2 -> "Mfg coupon savings";
                case 3 -> "Your total Savings";
                default -> required("Printed savings heading (words, not the amount)");
            };
            Currency currency = Currency.getInstance("USD");
            BigDecimal reported = amount("Printed savings amount", false, false);
            List<Inclusion> inclusions = new ArrayList<>();
            var events = ReceiptReport.sources(ReceiptSnapshot.from(draft.transaction()));
            List<EventReference> used = new ArrayList<>();
            while (yes("Identify a coupon, redemption or earning included in this amount?", false)) {
                List<ReceiptReport.Source> options = events.stream()
                        .filter(source -> currency.equals(source.value().currency()) && !used.contains(source.reference())).toList();
                if (options.isEmpty()) {
                    output.println("No unused confirmed monetary events in this currency.");
                    break;
                }
                for (int i = 0; i < options.size(); i++) output.println((i + 1) + ". " + options.get(i).description()
                        + " — " + options.get(i).value().amount().toPlainString());
                var selected = options.get(integer("Event number", 1, options.size()) - 1);
                BigDecimal included = yes("Include its displayed amount?", true) ? selected.value().amount()
                        : amount("Amount you confirm is included", false, false);
                inclusions.add(new Inclusion(selected.reference(), included, draft.actor, Instant.now()));
                used.add(selected.reference());
            }
            boolean complete = !inclusions.isEmpty() && yes("Have you identified everything included in this savings amount?", false);
            draft.savings.add(new SavingsCategory(UUID.randomUUID(), label, currency, reported, inclusions, complete));
        }
    }

    /** Reads unchanged file bytes; manual confirmation is not authentication of an image. */
    private void evidence(Draft draft) throws IOException {
        while (true) {
            String entered = optional("Receipt photo or PDF file path (Enter or BACK skips attachments)");
            if (entered == null || entered.trim().equalsIgnoreCase("BACK")) return;
            byte[] bytes;
            String mediaType;
            try {
                Path path = Path.of(entered.trim());
                bytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
                mediaType = filename.endsWith(".png") ? "image/png"
                        : filename.endsWith(".jpg") || filename.endsWith(".jpeg") ? "image/jpeg"
                        : filename.endsWith(".pdf") ? "application/pdf" : Files.probeContentType(path);
            } catch (IOException | IllegalArgumentException | SecurityException exception) {
                output.println("Cannot read that file. Receipt entries retained; try another path or press Enter to skip.");
                continue;
            }
            if (mediaType == null) {
                mediaType = optional("File type, e.g. image/png (Enter skips this attachment)");
                if (mediaType == null) return;
            }
            draft.evidence.add(new ReceiptEvidence(UUID.randomUUID(), draft.id, mediaType, bytes));
            if (!draft.earnings.isEmpty() && yes("Associate this evidence file with recorded earnings?", false)) {
                UUID evidenceId = draft.evidence.get(draft.evidence.size() - 1).getEvidenceId();
                for (int i = 0; i < draft.earnings.size(); i++) {
                    Earning earning = draft.earnings.get(i);
                    if (yes("Supports " + earning.provider() + " / " + earning.offerReference() + "?", false)) {
                        List<UUID> evidenceIds = new ArrayList<>(earning.evidenceIds());
                        evidenceIds.add(evidenceId);
                        draft.earnings.set(i, new Earning(earning.earningId(), earning.kind(), earning.provider(),
                                earning.offerReference(), earning.scope(), earning.lineItemIds(), earning.expected(),
                                earning.confirmed(), earning.status(), earning.expirationDate(), earning.instrumentId(),
                                earning.claimId(), evidenceIds, earning.recordedLifecycle(), earning.requirement(), earning.valuation()));
                    }
                }
            }
            if (!yes("Attach another file?", false)) return;
        }
    }

    /** Invalidated event selections require explicit reconfirmation after editing. */
    private void clearSavings(Draft draft) {
        if (!draft.savings.isEmpty()) {
            output.println("Savings links must be reconfirmed after editing financial entries.");
            for (int i = 0; i < draft.savings.size(); i++) {
                SavingsCategory previous = draft.savings.get(i);
                draft.savings.set(i, new SavingsCategory(previous.categoryId(), previous.printedLabel(), previous.currency(),
                        previous.reportedAmount(), List.of(), false));
            }
        }
    }

    /** Lists human-readable item choices. */
    private void showItems(Draft draft) {
        for (int i = 0; i < draft.items.size(); i++) output.println((i + 1) + ". "
                + ReceiptReport.title(draft.items.get(i).item().getProductName()) + " (Quantity " + draft.items.get(i).item().getQuantity() + ")");
    }

    /** Selects a receipt by its printed number or session list, without UUID entry. */
    private Optional<ReceiptRecord> selectReceipt() {
        if (savedIds.isEmpty()) {
            output.println("No receipts saved this session.");
            return Optional.empty();
        }
        for (int i = 0; i < savedIds.size(); i++) {
            var receipt = repository.findById(savedIds.get(i)).orElseThrow();
            output.println("#" + (i + 1) + " " + ReceiptReport.title(receipt.metadata().retailer()) + " | " + receipt.metadata().receiptNumber());
        }
        String selection = required("Printed receipt number, or #session number");
        if (selection.startsWith("#")) {
            try { return repository.findById(savedIds.get(Integer.parseInt(selection.substring(1)) - 1)); }
            catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                output.println("No such session receipt.");
                return Optional.empty();
            }
        }
        var matches = savedIds.stream().map(id -> repository.findById(id).orElseThrow())
                .filter(receipt -> selection.equals(receipt.metadata().receiptNumber())).toList();
        if (matches.size() != 1) {
            output.println("Found " + matches.size() + " matches; use a #session number to select uniquely.");
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    /** Clearly separates viewing receipt earnings from recording a separate physical coupon. */
    private void enterReward() {
        while (true) {
            output.println("REGISTER REWARD COUPONS");
            output.println("1. View Recorded RR Coupons | 2. Add A Separate Existing RR Coupon | 3. Return | 4. Delete Misentered Separate RR");
            int action = integer("RR Coupon Action", 1, 4);
            if (action == 3) return;
            if (action == 1) { viewRecordedRewards(); continue; }
            if (action == 4) { deleteRecordedReward(); continue; }
            output.println("Adds a separate physical RR coupon you already have; it does not change receipt earnings or spend a reward.");
            output.println("If you entered this reward while recording a receipt, view it with option 1 instead.");
            String source = optional("Coupon Source (e.g. Neutrogena; Enter or BACK cancels)");
            if (source == null || source.trim().equalsIgnoreCase("BACK")) { output.println("Cancelled — No RR Coupon Added."); continue; }
            RegisterReward reward = new RegisterReward(source, amount("Register Reward Amount", false, true),
                    date("Earned Date (blank unknown)"), date("Expiration Date (blank unknown)"), null);
            output.println("Coupon: " + ReceiptReport.title(source) + " — " + reward.getFaceValue().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " RR");
            if (yes("Save This Separate RR Coupon?", false)) {
                repository.issuedRewards().save(reward);
                output.println("RR Coupon Recorded. Saved Receipts Were Not Changed.");
            } else output.println("Cancelled — No RR Coupon Added.");
        }
    }

    private void deleteRecordedReward() {
        var rewards = repository.recordedRewards();
        if (rewards.isEmpty()) { output.println("No Separate RR Coupons To Delete."); return; }
        for (int i = 0; i < rewards.size(); i++) {
            RegisterReward r = rewards.get(i);
            output.println((i + 1) + ". " + ReceiptReport.title(r.getSourceDescription()) + " — "
                    + r.getFaceValue().setScale(2, java.math.RoundingMode.HALF_UP) + " RR");
        }
        int selected = integer("Coupon Number To Delete (0 cancels)", 0, rewards.size());
        if (selected == 0) return;
        RegisterReward reward = rewards.get(selected - 1);
        if (!yes("Delete " + ReceiptReport.title(reward.getSourceDescription()) + " — " + reward.getFaceValue()
                + " RR From The Active List?", false)) return;
        String reason = required("Reason (e.g. Entered By Mistake)");
        String actor = required("Confirmed By");
        repository.voidRecordedReward(reward.getRewardId(), reason, actor);
        output.println("RR Removed From Active List. Original Entry And Removal Audit Retained; Receipt Earnings Unchanged.");
    }

    private void viewRecordedRewards() {
        output.println("RR RECORDED WITH RECEIPTS");
        int count = 0;
        for (var history : repository.creationHistory()) {
            for (Earning earning : history.after().contents().observations().earnings()) {
                if (earning.kind() != EarningKind.REGISTER_REWARD) continue;
                RebateBenefit benefit = earning.confirmed() == null ? earning.expected() : earning.confirmed();
                String receipt = history.after().metadata() == null ? "Unknown" : history.after().metadata().receiptNumber();
                output.println(++count + ". Receipt " + receipt + " — " + benefit.amount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " RR — " + (earning.confirmed() == null ? "Expected" : earning.status() == RebateStatus.PAID
                                ? "Register Reward Printed" : ReceiptReport.title(earning.status().name().replace('_', ' '))));
                String products = earning.scope() == Scope.RECEIPT ? "Whole Receipt" : history.after().contents().purchaseItems().stream()
                        .filter(line -> earning.lineItemIds().contains(line.lineItemId())).map(line -> ReceiptReport.title(line.productName()))
                        .reduce((a, b) -> a + ", " + b).orElse("Products Not Recorded");
                output.println("   For: " + products);
            }
        }
        if (count == 0) output.println("No RR Recorded With Receipts.");
        output.println("SEPARATELY RECORDED PHYSICAL COUPONS");
        var separate = repository.recordedRewards();
        if (separate.isEmpty()) output.println("None Recorded.");
        for (RegisterReward reward : separate) {
            output.println(ReceiptReport.title(reward.getSourceDescription()) + " — "
                    + reward.getFaceValue().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " RR | Expires: "
                    + (reward.getExpirationDate() == null ? "Unknown" : reward.getExpirationDate()));
        }
        output.println("Receipt Earnings And Separate Coupons Are Listed Separately; No Balances Were Changed.");
    }

    private String writeSummary(ReceiptWriteResult result) {
        var history = repository.history().findByRequestId(result.requestId()).orElseThrow();
        var metadata = history.after().metadata();
        String number = metadata == null || metadata.receiptNumber() == null ? "Unknown" : metadata.receiptNumber();
        return "Receipt " + number + " | Version " + result.revision() + " | " + ReceiptReport.title(result.status().name().toLowerCase(Locale.ROOT));
    }

    private String readableTimestamp(Instant timestamp) {
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu h:mm:ss a z", Locale.US)
                .withZone(java.time.ZoneId.systemDefault()).format(timestamp);
    }

    /** Reads historical snapshot components directly, without reconstructing a working transaction. */
    private void displayHistory(ReceiptRecord receipt) {
        var history = repository.history().findByReceiptId(receipt.transactionId());
        output.println("RECEIPT HISTORY");
        output.println("Receipt: " + (receipt.metadata() == null ? "Unknown" : receipt.metadata().receiptNumber()));
        output.println("History entries: " + history.size());
        for (var entry : history) {
            output.println("--------------------------------------------------");
            String action = switch (entry.result().action()) {
                case CREATED -> "Receipt Saved";
                case UPDATED -> "Receipt Corrected";
                case DELETED -> "Receipt Deleted";
                case RESTORED -> "Receipt Restored";
            };
            output.println("Version " + entry.after().revision() + " — " + action);
            output.println("Saved At: " + readableTimestamp(entry.result().committedAt()));
            output.println("Entered By: " + ReceiptReport.title(entry.createCtx() == null ? entry.changeCtx().requestedBy() : entry.createCtx().requestedBy()));
            var contents = entry.after().contents();
            output.println("Receipt Lines: " + contents.purchaseItems().size() + " | Products Purchased: "
                    + contents.purchaseItems().stream().mapToInt(line -> line.quantity()).sum());
            output.println("Receipt Total: " + contents.receiptTotals().finalTotal().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
            output.println("Status: " + ReceiptReport.title(entry.after().status().name().toLowerCase(Locale.ROOT)));
            if (entry.changeCtx() != null) output.println("Reason: " + entry.changeCtx().reason());
        }
    }

    /** Delegates presentation so review and retrieval use the same calculations. */
    private void display(ReceiptRecord receipt) {
        ReceiptReport.print(receipt, output);

    }

    /** Records explicit starting USD values; settings remain local to this console session. */
    private void balanceSettings() {
        readingBalances = true;
        try { balanceSettingsFields(); }
        catch (ReturnToMenu back) { output.println("Returned To Main Menu."); }
        finally { readingBalances = false; }
    }

    private void balanceSettingsFields() {
        LocalDate day = date("Balance date (Enter or BACK returns; EXIT closes CLI)");
        if (day == null) return;
        while (true) {
            output.println("1. Month-start balance | 2. Day-start balance | 3. Both (same entered value) | 4. View balances | 5. Return");
            int action = integer("Balance action", 1, 5);
            var records = savedIds.stream().map(id -> repository.findById(id).orElseThrow()).toList();
            if (action == 5) return;
            if (action == 4) { balances.print(day, records, output); continue; }
            var programs = balances.programs(records);
            for (int i = 0; i < programs.size(); i++) output.println((i + 1) + ". " + programs.get(i));
            String program = programs.get(integer("Provider", 1, programs.size()) - 1);
            BigDecimal value = amount("Starting USD value (use your recorded valuation for points programs)", false, false, true);
            if (action == 1 || action == 3) balances.recordOpening(day.withDayOfMonth(1), program, value, true);
            if (action == 2 || action == 3) balances.recordOpening(day, program, value, false);
            output.println("Starting balance recorded for " + program + ".");
        }
    }

    /** Reads original text and routes item/earning navigation without editing recorded values. */
    private String text(String label) {
        output.println(label + ":");
        String value = input.nextLine();
        if (readingBalances && value.trim().equalsIgnoreCase("BACK")) throw new ReturnToMenu();
        if (readingBalances && value.trim().equalsIgnoreCase("EXIT")) throw new ExitCli();
        if (readingEarning && value.trim().equalsIgnoreCase("BACK")) {
            throw new PreviousEarningQuestion();
        }
        if (readingItem && value.trim().equalsIgnoreCase("BACK")) throw new PreviousItemQuestion();
        if (readingItem && value.trim().equalsIgnoreCase("DONE")) throw new ItemEntryCancelled();
        return value;
    }

    /** Requires text without silently discarding a draft after a blank entry. */
    private String required(String label) {
        while (true) {
            String value = text(label);
            if (!value.isBlank()) return value;
            output.println("Please enter a value.");
        }
    }

    /** Preserves unknown optional text as null. */
    private String optional(String label) {
        String value = text(label);
        return value.isBlank() ? null : value;
    }

    /** Reads explicit yes/no choices with the displayed default. */
    private boolean yes(String label, boolean defaultValue) {
        while (true) {
            String value = text(label + (defaultValue ? " [Y/n]" : " [y/N]")).trim();
            if (value.isEmpty()) return defaultValue;
            if (value.equalsIgnoreCase("y") || value.equalsIgnoreCase("yes")) return true;
            if (value.equalsIgnoreCase("n") || value.equalsIgnoreCase("no")) return false;
            output.println("Enter y or n.");
        }
    }

    /** Parses a decimal with explicit unknown and positivity rules. */
    private BigDecimal amount(String label, boolean optional, boolean positive) {
        return amount(label, optional, positive, false);
    }

    /** Signed balances are preserved; transaction activity cannot be negative. */
    private BigDecimal amount(String label, boolean optional, boolean positive, boolean signed) {
        while (true) {
            String value = text(label).trim();
            if (optional && value.isEmpty()) return null;
            try {
                BigDecimal number = new BigDecimal(value.startsWith("$") ? value.substring(1) : value);
                if (signed || positive && number.signum() > 0 || !positive && number.signum() >= 0) return number;
            } catch (NumberFormatException exception) { }
            output.println("Enter a " + (signed ? "decimal" : positive ? "positive" : "nonnegative") + " amount"
                    + (optional ? ", or leave blank for unknown." : "."));
        }
    }

    /** Reads a bounded integer without losing previous fields. */
    private int integer(String label, int minimum, int maximum) {
        while (true) {
            try {
                int value = Integer.parseInt(text(label).trim());
                if (value >= minimum && value <= maximum) return value;
            } catch (NumberFormatException exception) { }
            output.println("Enter a whole number from " + minimum + " to " + maximum + ".");
        }
    }

    /** An unknown coupon qualification stays unknown, independently of purchased quantity. */
    private Integer optionalQuantity(String label) {
        while (true) {
            String value = optional(label);
            if (value == null) return null;
            try {
                String entered = value.trim();
                var offer = java.util.regex.Pattern.compile("(?i)\\$?\\d+(?:\\.\\d+)?\\s+off\\s+(\\d+)").matcher(entered);
                if (offer.matches()) entered = offer.group(1);
                int number = Integer.parseInt(entered);
                if (number > 0) {
                    if (offer.matches()) output.println("Items required per MQ: " + number + "; MQ value stays as entered above.");
                    return number;
                }
            } catch (NumberFormatException ignored) { }
            output.println("Enter a positive whole number, or leave blank for unknown.");
        }
    }

    /** Reads a typed choice without guessing from free-text labels. */
    private <E extends Enum<E>> E choice(String label, Class<E> type) {
        while (true) {
            E[] values = type.getEnumConstants();
            for (int i = 0; i < values.length; i++) {
                output.println((i + 1) + ". " + values[i].name().replace('_', ' '));
            }
            String entered = text(label).trim().toUpperCase(Locale.ROOT);
            try {
                int index = Integer.parseInt(entered) - 1;
                if (index >= 0 && index < values.length) return values[index];
            } catch (NumberFormatException exception) {
                try { return Enum.valueOf(type, entered); }
                catch (IllegalArgumentException invalid) { }
            }
            output.println("Choose a displayed number or name.");
        }
    }

    /** Acknowledges a readable time while preserving its originally entered spelling. */
    private String receiptTime() {
        while (true) {
            String raw = optional("Receipt time (e.g. 940 am, 9:40 am, or 21:40; blank unknown)");
            if (raw == null) return null;
            if (ReceiptTimeInput.parse(raw).isPresent()) {
                output.println("Time understood as: " + ReceiptTimeInput.display(raw));
                return raw;
            }
            output.println("Enter a valid time such as 940 am, 9:40 PM, or 21:40; or leave blank.");
        }
    }

    /** Accepts common numeric and written dates, acknowledging the interpretation. */
    private LocalDate date(String label) {
        while (true) {
            String value = optional(label);
            if (value == null) return null;
            List<LocalDate> candidates = ReceiptDateInput.candidates(value);
            if (candidates.isEmpty()) {
                output.println("Enter a date such as 10/4/2026 or 2026-10-04, 10/4/26, 26/10/4, or October 4, 2026; or leave blank.");
                continue;
            }
            LocalDate selected = candidates.get(0);
            output.println("Date understood as: " + selected.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US))
                    + " (" + selected + ")");
            return selected;
        }
    }

    /** Requires a known identifier only when linking an existing instrument. */
    private UUID uuid(String label) {
        while (true) {
            try { return UUID.fromString(text(label).trim()); }
            catch (IllegalArgumentException exception) { output.println("Enter the recorded instrument UUID."); }
        }
    }

    /** A draft item and its actual deductions; no advertised coupon is fabricated. */
    private record Item(PurchaseItem item, List<CouponApplied> deductions, Map<UUID, DiscountKind> kinds) { }

    /** UI selection retains existing financial representations without inventing a denomination. */
    private record EarningSelection(EarningKind kind, BenefitKind form, boolean other, String provider) { }

    /** Unsaved answers survive navigation; a stable earning identity is assigned only on completion. */
    private static final class EarningAnswers {
        private EarningSelection selection;
        private String provider, offer, lifecycle, basis, requirementCategory;
        private PromotionType requirementType;
        private BigDecimal required, entered, valuation;
        private Scope scope;
        private List<UUID> lines = List.of();
        private Currency currency;
        private BigDecimal expected, confirmed;
        private RebateStatus status;
        private LocalDate expiration;

        /** Builds one earning using the final choices, not stale skipped answers. */
        Earning build() {
            return new Earning(UUID.randomUUID(), selection.kind(), provider, offer, scope, lines,
                    expected == null ? null : new RebateBenefit(selection.form(), expected, currency, selection.form() == BenefitKind.CASH ? null : provider),
                    confirmed == null ? null : new RebateBenefit(selection.form(), confirmed, currency, selection.form() == BenefitKind.CASH ? null : provider),
                    status, expiration, null, null, List.of(), lifecycle,
                    new ReceiptObservations.OfferRequirement(requirementType, required, requirementCategory),
                    valuation == null ? null : new ReceiptObservations.RecordedValuation(valuation, Currency.getInstance("USD"), basis));
        }
    }

    /** Mutable entry state exists only before save; publication captures immutable facts. */
    private static final class Draft {
        private final UUID id = UUID.randomUUID();
        private String number, time, retailer, actor;
        private LocalDate date;
        private BigDecimal tax, total, savingPercent;
        private final List<Item> items = new ArrayList<>();
        private final List<Payment> payments = new ArrayList<>();
        private final List<RegisterRewardRedemption> knownRR = new ArrayList<>();
        private final List<UnidentifiedRR> unknownRR = new ArrayList<>();
        private final List<Earning> earnings = new ArrayList<>();
        private final List<SavingsCategory> savings = new ArrayList<>();
        private final List<ReceiptEvidence> evidence = new ArrayList<>();
        private WalgreensCashAccount account;

        /** Builds fresh working contents, preserving draft IDs. */
        CouponTransaction transaction() {
            CouponTransaction transaction = new CouponTransaction(new ReceiptTotals(tax, total));
            Map<UUID, DiscountKind> kinds = new HashMap<>();
            for (Item item : items) {
                transaction.addPurchaseItem(item.item());
                item.deductions().forEach(transaction::addAppliedCoupon);
                kinds.putAll(item.kinds());
            }
            payments.forEach(transaction::addPayment);
            knownRR.forEach(transaction::addRegisterRewardRedemption);
            transaction.setObservations(new ReceiptObservations(date, time, earnings, unknownRR, account, savings, kinds, savingPercent));
            return transaction;
        }

        /** No combined timestamp is invented from partial date/time observations. */
        ReceiptMeta metadata() { return new ReceiptMeta(retailer, number, null, null); }

        /** Creates a reviewable receipt and validates relationships before save. */
        ReceiptRecord receipt() {
            return new ReceiptRecord(id, ReceiptStatus.ACTIVE, 1, transaction(), metadata(),
                    evidence.stream().map(ReceiptEvidence::getEvidenceId).toList());
        }
    }

    /** Original captured values retained for retries even after retrievals. */
    private record PendingSave(UUID id, ReceiptSnapshot contents, ReceiptMeta metadata,
            List<ReceiptEvidence> evidence, ReceiptWriteRequest request, ReceiptCreateCtx context) {
        /** Reuses every original identity and value. */
        ReceiptWriteResult submit(TransactionRepository repository) {
            return repository.save(id, contents.toTransaction(), metadata, evidence, request, context);
        }
    }
}
