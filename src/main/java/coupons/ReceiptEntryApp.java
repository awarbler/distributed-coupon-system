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
import java.util.Collections;
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
import java.util.TreeSet;
import java.util.UUID;

import coupons.ReceiptObservations.DiscountKind;
import coupons.ReceiptObservations.Earning;
import coupons.ReceiptObservations.EarningKind;
import coupons.ReceiptObservations.EventReference;
import coupons.ReceiptObservations.FooterStatus;
import coupons.ReceiptObservations.Inclusion;
import coupons.ReceiptObservations.SavingsCategory;
import coupons.ReceiptObservations.Scope;
import coupons.ReceiptObservations.UnidentifiedRR;
import coupons.ReceiptObservations.WalgreensCashAccount;
import coupons.repository.GiftCardPromotion;
import coupons.repository.InMemoryTransactionRepository;
import coupons.repository.PurchaseLineSnap;
import coupons.repository.RRUsageLink;
import coupons.repository.ReceiptChangeContext;
import coupons.repository.ReceiptCreateCtx;
import coupons.repository.ReceiptEvidence;
import coupons.repository.ReceiptMeta;
import coupons.repository.ReceiptRecord;
import coupons.repository.ReceiptSnapshot;
import coupons.repository.ReceiptStatus;
import coupons.repository.ReceiptTotalsSnap;
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
    private String queuedMainAction;
    private boolean editingSection;
    private static final class EditDone extends RuntimeException { }
    private final ProviderBalanceSummary balances;

    /** Returns from the console without terminating the JVM used by callers or tests. */
    private static final class ExitCli extends RuntimeException { }

    /** Enables navigation commands only while collecting the current item. */
    private boolean readingItem;
    private static final class PreviousItemQuestion extends RuntimeException { }

    /** Enables previous-question navigation during an unsaved earning. */
    private boolean readingEarning;

    /** Signals navigation, without discarding the receipt or prior earning answers. */
    private static final class PreviousEarningQuestion extends RuntimeException { }

    /** Cancels one unsaved item without discarding previously entered receipt facts. */
    private static final class ItemEntryCancelled extends RuntimeException { }

    private static final class Navigate extends RuntimeException {
        final boolean back;
        Navigate(boolean back) { this.back = back; }
    }
    private static final class EarningCancelled extends RuntimeException { }
    private static final class QuestionFrame {
        final List<String> answers = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        int cursor;
        String currentLabel;
    }
    private final java.util.ArrayDeque<QuestionFrame> questionFrames = new java.util.ArrayDeque<>();
    private boolean receiptInProgress;

    /** Replays only an uncommitted form, retaining earlier answers; writes belong after the form returns. */
    private <T> T questions(java.util.function.Supplier<T> form, Runnable reset) {
        QuestionFrame frame = new QuestionFrame();
        questionFrames.push(frame);
        try {
            while (true) {
                reset.run(); frame.cursor = 0;
                try { return form.get(); }
                catch (EditDone done) { reset.run(); throw done; }
                catch (Navigate navigation) {
                    if (!navigation.back || frame.cursor == 0) { reset.run(); throw navigation; }
                    int previous = Math.min(frame.cursor, frame.answers.size()) - 1;
                    // Invalid attempts at the current question are not completed earlier questions.
                    while (previous >= 0 && frame.labels.get(previous).equals(frame.currentLabel)) previous--;
                    if (previous < 0) { reset.run(); throw navigation; }
                    while (previous > 0 && frame.labels.get(previous).equals(frame.labels.get(previous - 1))) previous--;
                    frame.answers.subList(previous, frame.answers.size()).clear();
                    frame.labels.subList(previous, frame.labels.size()).clear();
                    output.println("Back to the previous question; earlier answers retained.");
                }
            }
        } finally { questionFrames.pop(); }
    }
    private <T> T questions(java.util.function.Supplier<T> form) { return questions(form, () -> { }); }
    private void draftQuestions(Draft draft, Runnable form) {
        Draft before = draft.copy();
        questions(() -> { form.run(); return null; }, () -> draft.restore(before));
    }
    private String exitConfirmation() {
        return repository.storagePath().isPresent()
                ? "Exit program? Completed draft checkpoints are kept for Resume; unfinished answers may be lost. YES closes the program; NO keeps editing"
                : "Exit CLI and discard unsaved work? YES closes the program; NO keeps this draft open. Saved records remain stored";
    }
    private boolean navigationConfirmation(String label) {
        while (true) {
            output.println(label + " [y/N]:");
            String answer = input.nextLine().trim();
            if (answer.isEmpty() || answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("BACK") || answer.equalsIgnoreCase("CANCEL")) return false;
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return true;
            if (answer.equalsIgnoreCase("EXIT") && !label.startsWith("Exit ")
                    && navigationConfirmation(exitConfirmation())) throw new ExitCli();
            output.println("Enter Y to confirm, or N/BACK to retain your work.");
        }
    }
    private String menuText(String label) {
        try {
            if(label.contains(" / ")) {
                int colon=label.indexOf(": ");
                String title=colon<0?(label.startsWith("SAVE")?"RECEIPT ACTIONS":"CHOOSE AN ACTION"):label.substring(0,colon);
                String choices=colon<0?label:label.substring(colon+2);
                return numberedMenu(title,"Choice",label,choices);
            }
            return text(label);
        } catch (Navigate navigation) { return navigation.back ? "BACK" : "CANCEL"; }
    }

    /** Numbers are aliases for the same commands, including their existing confirmation/navigation rules. */
    private String numberedMenu(String title,String prompt,String logicalLabel,String choices) {
        output.println("\n"+title);
        var aliases=new LinkedHashMap<String,String>();
        var options=choices.split(" / ");
        for(int i=0;i<options.length;i++) {
            String command=options[i];
            int note=command.indexOf(" (");
            if(note>=0) command=command.substring(0,note);
            command=command.trim();
            aliases.put(Integer.toString(i+1),command);
            String caption=switch(command) {
                case "DONE" -> title.startsWith("EDIT ")?"Done — return to review":"Done";
                case "CANCEL" -> logicalLabel.startsWith("SAVE / EDIT") || logicalLabel.startsWith("DETAILS / ITEM /")
                        ? "Cancel draft — return to main menu (discard unsaved changes)" : "Cancel";
                case "EXIT" -> "Exit program";
                case "PAUSE" -> "Keep draft — return to main menu (resume later)";
                case "BACK" -> "Back";
                case "WORKSHEET" -> "Detailed breakdown";
                case "KIND" -> "Discount type";
                case "EDIT RECEIPT" -> "Edit this receipt";
                case "ALL" -> "All fields (full form)";
                default -> (command.substring(0,1).toUpperCase(Locale.ROOT)+command.substring(1).toLowerCase(Locale.ROOT))
                        .replace("sku","SKU").replace("Sku","SKU").replace("Rr", "RR").replace("Wc", "WC");
            };
            if(title.equals("EDIT PRODUCTS")) caption=switch(command) {
                case "SELECT" -> "Choose a product to edit";
                case "DESCRIPTION" -> "Change product name";
                case "SKU" -> "Change SKU / UPC";
                case "REGULAR PRICE" -> "Change regular price per item";
                case "PRICE" -> "Change entered price per item (before separate discounts)";
                case "QUANTITY" -> "Change quantity purchased";
                case "STORE COUPONS AND PROMOS" -> "Add a store coupon or sale/promotion discount";
                case "MANUFACTURER COUPONS" -> "Add a manufacturer coupon";
                case "ALL" -> "Open the complete product entry form";
                case "DONE" -> "Done editing products — return to receipt review";
                default -> caption;
            };
            if(logicalLabel.startsWith("Edit:") || logicalLabel.startsWith("DETAILS / ITEM /"))caption=switch(command) {
                case "DETAILS" -> "Receipt details (store, date, order type)";
                case "ITEM" -> "Products (prices and quantities)";
                case "CHECKOUT" -> "Tax and payment";
                case "DISCOUNTS" -> "Discounts and store promotions";
                case "COUPONS" -> "Manufacturer coupons";
                case "PROMOTIONS" -> "Online rebate eligibility";
                case "EARNINGS" -> "Rewards and rebates";
                case "ACCOUNT" -> "Printed account balances";
                case "SAVINGS" -> "Printed savings footer";
                case "SAVINGS LINKS" -> "Verify printed savings";
                case "EVIDENCE" -> "Attached receipt files";
                default -> caption;
            };
            output.println((i+1)+". "+caption);
        }
        return text(logicalLabel,prompt,aliases);
    }

    /** Creates a single-user console session with an isolated in-memory store. */
    public ReceiptEntryApp(Scanner input, PrintStream output) {
        this(input, output, new InMemoryTransactionRepository());
    }

    /** Allows integration tests to inspect the same repository used by the console. */
    ReceiptEntryApp(Scanner input, PrintStream output, InMemoryTransactionRepository repository) {
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.repository = Objects.requireNonNull(repository);
        this.balances = new ProviderBalanceSummary(repository);
        var history = repository.creationHistory();
        history.forEach(entry -> { if (repository.findById(entry.after().transactionId()).isPresent()) savedIds.add(entry.after().transactionId()); });
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
            System.err.println("Could not open saved receipts. Close any other running copy and try again. Existing data was not replaced.");
        }
    }

    /** Accepts a receipt number directly as well as menu choices. */
    public void run() {
        output.println("\n============================================================");
        output.println("                 GROCERY INTELLIGENCE PLATFORM");
        output.println("                     Receipts and rewards");
        output.println("============================================================");
        output.printf("%n  Saved receipts : %d%n",savedIds.size());
        if (repository.storagePath().isPresent()) {
            output.println("  Storage        : Saved on this device");
        } else output.println("  Storage        : Session only — closes without persistence");
        output.println("\nNAVIGATION");
        output.println("  BACK    Previous question or menu");
        output.println("  CANCEL  Cancel the current operation (confirmation required)");
        output.println("  EXIT    Close the program");
        output.println("\nChoose a menu option or enter a receipt number to begin.");
        output.println("------------------------------------------------------------");
        while (true) {
            output.println();
            output.println("MAIN MENU\n\n1. Enter a receipt (NEW or IMPORT)\n2. Retrieve a receipt\n3. Update a receipt\n4. Delete a receipt\n5. Register Rewards\n6. Account balances / Savings reports\n7. Gift-card promotions\n8. Receipt history / Retry last save\n9. Import a receipt file — select, edit and complete\n10. Resume an unfinished receipt\n11. Exit\n");
            try {
                String choice = queuedMainAction == null ? menuText("Receipt number or action") : queuedMainAction;
                queuedMainAction = null;
                switch (choice.trim().toUpperCase(Locale.ROOT)) {
                    case "BACK", "CANCEL" -> { continue; }
                    case "NEW", "1" -> { receiptInProgress = true; try { enterReceipt(null); } finally { receiptInProgress = false; } }
                    case "IMPORT", "I", "9" -> { receiptInProgress=true;try {importReceipt();}finally {receiptInProgress=false;} }
                    case "R", "RESUME", "10" -> {receiptInProgress=true;try {resumeDraft();}finally {receiptInProgress=false;}}
                    case "2" -> selectReceipt().ifPresent(this::receiptViews);
                    case "3", "UPDATE" -> {
                        var selected = selectReceipt();
                        if (selected.isPresent()) { receiptInProgress = true; try { correctReceipt(selected.orElseThrow()); } finally { receiptInProgress = false; } }
                    }
                    case "4", "DELETE" -> deleteReceipt();
                    case "5" -> enterReward();
                    case "6", "BALANCES" -> viewBalances();
                    case "BALANCE SETTINGS" -> balanceSettings();
                    case "7", "PROMOTIONS" -> giftCardPromotions();
                    case "8", "HISTORY" -> historyActions();
                    case "11", "EXIT" -> { return; }
                    default -> {
                        if (choice.isBlank()) output.println("Enter a receipt number or an action.");
                        else { receiptInProgress = true; try { enterReceipt(choice); } finally { receiptInProgress = false; } }
                    }
                }
            } catch (Navigate navigation) {
                output.println("Returned to main menu; no pending operation committed.");
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
        Draft draft = new Draft();draft.entryStage=0;
        continueReceiptEntry(draft,suppliedNumber,0);
    }
    private void continueReceiptEntry(Draft draft,String suppliedNumber,int stage) throws IOException {
        output.println("NEW RECEIPT");
        while (stage < 8) {
            try {
                switch (stage) {
                    case 0 -> draftQuestions(draft, () -> {
                        draft.number = suppliedNumber == null ? optional("Receipt number (blank unknown)") : suppliedNumber;
                        detailsFields(draft);
                    });
                    case 1 -> enterProducts(draft);
                    case 2 -> draftQuestions(draft, () -> resolveDeferredEarnings(draft));
                    case 3 -> {
                        output.println("Before tax: type COUPONS for shared deductions or PROMOTIONS to note online rebate eligibility on selected products.");
                        checkout(draft);
                    }
                    case 4 -> earnings(draft);
                    case 5 -> { if (isWalgreens(draft)) savings(draft); else account(draft); }
                    case 6 -> { if (isWalgreens(draft)) { if(draft.footerStatus != FooterStatus.NOT_PRINTED) account(draft); } else savings(draft); }
                    case 7 -> { if (questions(() -> yes("Attach a receipt photo or PDF?", false))) evidence(draft); }
                }
                stage++;draft.entryStage=stage;checkpointCompletedDraft(draft);
            } catch (Navigate navigation) {
                if (stage == 0 && (draft.items.isEmpty() || navigationConfirmation("Discard this receipt draft and return to main menu?"))) {removeDraft(draft);return;}
                stage = Math.max(0, stage - 1);
                if (stage == 6 && isWalgreens(draft) && draft.footerStatus == FooterStatus.NOT_PRINTED) stage = 5;
                if (stage == 2 && draft.deferredEarnings.isEmpty()) stage = 1;
                output.println("Receipt draft retained; returned to previous section.");
            }
        }
        reviewReceipt(draft);
    }

    private static boolean isReceiptImage(Path path) {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".heic") || name.endsWith(".heif");
    }

    private String importLabel(Path path) {
        try {
            var fields=new java.util.Properties();
            try(var reader=java.nio.file.Files.newBufferedReader(path,java.nio.charset.StandardCharsets.UTF_8)){fields.load(reader);}
            String number=fields.getProperty("receipt.number","Receipt number not read");
            String status=savedIds.stream().map(id->repository.findById(id).orElseThrow()).anyMatch(r->r.metadata()!=null && number.equals(r.metadata().receiptNumber()))?"Saved — use Update":"Ready for review";
            return number+" | "+fields.getProperty("receipt.date","Date not read")+" | "+fields.getProperty("receipt.time","Time not read")+" | "+status;
        } catch(IOException | IllegalArgumentException invalid){return "Unreadable prepared draft";}
    }

    private List<Path> selectImportPhotos() throws IOException {
        String action=menuText("Photos: BROWSE / LIST / BACK").trim().toUpperCase(Locale.ROOT);
        if(action.equals("BACK") || action.equals("CANCEL"))return List.of();
        List<Path> selected;
        if(action.equals("BROWSE")) {
            if(java.awt.GraphicsEnvironment.isHeadless()) {output.println("A desktop file picker is unavailable. Choose PHOTOS > LIST instead.");return List.of();}
            var result=new ArrayList<Path>();
            try {
                java.awt.EventQueue.invokeAndWait(()-> {
                    var picker=new java.awt.FileDialog((java.awt.Frame)null,"Select photos or PDF for ONE receipt",java.awt.FileDialog.LOAD);
                    try {
                        picker.setMultipleMode(true);picker.setFilenameFilter((dir,name)->isReceiptImage(Path.of(name)));
                        picker.setVisible(true);for(var file:picker.getFiles())result.add(file.toPath());
                    } finally {picker.dispose();}
                });
            } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Photo selection interrupted.");}
            catch(java.lang.reflect.InvocationTargetException failure){throw new IOException("Photo picker unavailable. Use PHOTOS > LIST.");}
            selected=result;
        } else if(action.equals("LIST")) {
            var files=new ArrayList<Path>();Path folder=Path.of("docs","receiptsImages");
            if(java.nio.file.Files.isDirectory(folder)) {
                try(var paths=java.nio.file.Files.walk(folder,3)){paths.filter(java.nio.file.Files::isRegularFile).filter(ReceiptEntryApp::isReceiptImage).sorted().forEach(files::add);}
            }
            if(files.isEmpty()){output.println("No photos found. Use BROWSE to choose images.");return List.of();}
            output.println("\nRECEIPT PHOTOS");
            for(int i=0;i<files.size();i++)output.println((i+1)+". "+files.get(i).getFileName());
            String input=questions(()->required("Photo numbers in top-to-bottom order, separated by commas (0 returns)"));
            if(input.equals("0"))return List.of();
            var chosen=new ArrayList<Path>();
            try {
                for(String value:input.split(",")) {Path file=files.get(Integer.parseInt(value.trim())-1);if(chosen.contains(file))throw new IllegalArgumentException();chosen.add(file);}
            } catch(IllegalArgumentException | IndexOutOfBoundsException invalid){output.println("Choose listed photo numbers once each.");return List.of();}
            selected=chosen;
        } else {output.println("Choose BROWSE, LIST or BACK.");return List.of();}
        if(selected.isEmpty())return List.of();
        output.println("\nSELECTED FILES — one receipt, top to bottom");
        for(int i=0;i<selected.size();i++)output.println((i+1)+". "+selected.get(i).getFileName());
        return questions(()->yes("Read these files as one receipt?",false))?List.copyOf(selected):List.of();
    }

    /** Assisted extraction is reviewed in the existing editor; original image bytes commit only on SAVE. */
    private void importReceipt() throws IOException {
        String entered=questions(()->required("Receipt import: FILES selects photos or PDF; LIST selects a prepared draft; or enter a .properties path; BACK returns"));
        if(entered.equalsIgnoreCase("PHOTOS") || entered.equalsIgnoreCase("FILES") || entered.equalsIgnoreCase("PDF")) {
            var photos=selectImportPhotos();
            if(photos.isEmpty())return;
            output.println("Reading Walgreens receipt files on this device. No upload is performed. Other store layouts may require manual entry.");
            Path prepared=new WalgreensImageImport().prepare(photos,Path.of(".coupon-data","imports"));
            output.println("Extracted draft retained for reopening through IMPORT > LIST. SAVE approves the receipt.");
            entered=prepared.toString();
        }
        if(entered.equalsIgnoreCase("LIST")) {
            var files=new ArrayList<Path>();
            Path folder=Path.of(".coupon-data","imports");
            if(java.nio.file.Files.isDirectory(folder)) {
                try(var paths=java.nio.file.Files.walk(folder,5)) {
                    paths.filter(java.nio.file.Files::isRegularFile)
                            .filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".properties"))
                            .filter(p->PreparedImportList.visible(p,repository))
                            .sorted().forEach(files::add);
                }
            }
            if(files.isEmpty()) {
                output.println("No unfinished imports found. Saved receipts are under Retrieve or Update. Choose FILES to import another receipt.");
                return;
            }
            output.println("\nSELECT A RECEIPT IMPORT — opens for editing before SAVE");
            for(int i=0;i<files.size();i++)output.println((i+1)+". "+importLabel(files.get(i)));
            String selection=questions(()->required("File number to open, REMOVE to remove an entry from this list, or 0 to return"));
            if(selection.equals("0"))return;
            if(selection.equalsIgnoreCase("REMOVE") || selection.equalsIgnoreCase("DELETE")) {
                int removed=questions(()->integer("File number to remove from list (0 cancels)",0,files.size()));
                if(removed==0)return;
                Path manifest=files.get(removed-1);
                if(questions(()->yes("Remove "+importLabel(manifest)+" from import list? Saved receipts and attachments are kept",false))) {
                    PreparedImportList.remove(manifest);
                    output.println("Removed from import list. Saved receipts and original files retained.");
                }
                return;
            }
            int selected;
            try {selected=Integer.parseInt(selection);}catch(NumberFormatException invalid){output.println("Choose a listed file number, REMOVE, or 0.");return;}
            if(selected<1 || selected>files.size()){output.println("Choose a listed file number.");return;}
            entered=files.get(selected-1).toString();
        }
        entered=entered.trim();
        if(entered.length()>1 && ((entered.startsWith("\"")&&entered.endsWith("\""))||(entered.startsWith("'")&&entered.endsWith("'"))))entered=entered.substring(1,entered.length()-1);
        Path path=Path.of(entered);
        String filename=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(filename.endsWith(".pdf")) {
            path=new WalgreensImageImport().prepare(List.of(path),Path.of(".coupon-data","imports"));
            filename=path.getFileName().toString().toLowerCase(Locale.ROOT);
        }
        if(!filename.endsWith(".properties")) {
            output.println("Choose PHOTOS for local image reading, or select a prepared .properties draft.");
            return;
        }
        var imported=ReceiptImportReader.read(path);
        var existing=repository.findById(imported.importId()).or(()->repository.findDeletedById(imported.importId()));
        if(existing.isPresent()) {
            output.println("This image import is already saved: "+receiptNumber(existing.orElseThrow())+". Nothing added; use Update a receipt for corrections.");
            return;
        }
        Draft draft=new Draft(imported.importId());draft.imported=true;draft.actor="Local owner";
        draft.retailer=imported.retailer()==null?questions(()->required("Missing retailer — verify from image")):imported.retailer();
        draft.number=imported.receiptNumber();draft.date=imported.purchaseDate();draft.time=imported.printedTime();draft.purchaseChannel=imported.purchaseChannel();
        draft.tax=imported.tax()==null?questions(()->amount("Missing sales tax — enter the verified amount",false,false)):imported.tax();
        draft.total=imported.printedTotal()==null?questions(()->amount("Missing printed total — enter the verified amount",false,false)):imported.printedTotal();
        for(int i=0;i<imported.items().size();i++) {
            var row=imported.items().get(i);int line=i+1;
            String name=row.description()==null?questions(()->required("Missing description for line "+line)):row.description();
            int quantity=row.quantity()==null?questions(()->integer("Missing quantity for line "+line,1,Integer.MAX_VALUE)):row.quantity();
            BigDecimal price=row.enteredUnitPrice()==null?questions(()->amount("Missing entered unit price for line "+line,false,false)):row.enteredUnitPrice();
            var deductions=imported.discounts().stream().filter(c->c.application().getLineItemIds().equals(List.of(row.lineId()))).toList();
            var kinds=new HashMap<UUID,DiscountKind>();deductions.forEach(c->kinds.put(c.application().getApplicationId(),c.kind()));
            draft.items.add(new Item(new PurchaseItem(row.lineId(),name,row.sku(),row.regularUnitPrice(),price,quantity),deductions.stream().map(ReceiptImportDraft.ImportedDiscount::application).toList(),kinds));
        }
        for(var coupon:imported.discounts())if(coupon.application().getLineItemIds().size()!=1) {
            draft.sharedCoupons.add(coupon.application());draft.sharedKinds.put(coupon.application().getApplicationId(),coupon.kind());
        }
        draft.payments.addAll(imported.payments());draft.unknownRR.addAll(imported.rrUses());draft.earnings.addAll(imported.earnings());
        draft.account=imported.account();draft.savings.addAll(imported.savings());draft.savingPercent=imported.savingPercent();draft.footerStatus=imported.footerStatus();
        draft.evidence.addAll(imported.evidence());draft.importIssues.addAll(imported.issues());
        var tx=draft.transaction();
        if(new TransactionCalculator().calculateReceiptDifference(tx).signum()!=0)
            draft.importIssues.add(new ReceiptImportDraft.ReviewIssue("checkout",null,"Calculated checkout differs from the printed total. Verify prices, deductions, tax and rewards used; preserve any real receipt discrepancy."));
        if(draft.total.signum()!=0&&draft.payments.stream().noneMatch(p->p.getPaymentType()!=PaymentType.WALGREENS_CASH))
            draft.importIssues.add(new ReceiptImportDraft.ReviewIssue("payments",null,"Payment is not recorded for a nonzero printed total. Add the actual tender through EDIT > CHECKOUT > PAYMENTS, or explicitly retain unknown payment."));
        var duplicates=repository.findPossibleDuplicates(draft.metadata());
        if(!duplicates.isEmpty())output.println("Possible duplicate: "+duplicates.size()+" saved receipt(s) have matching metadata. Review before saving.");
        reviewReceipt(draft);
    }

    private void addImportReviewEvidence(Draft draft) {
        UUID id=UUID.nameUUIDFromBytes((draft.id+":import-review").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(draft.evidence.stream().anyMatch(e->e.getEvidenceId().equals(id)))return;
        StringBuilder audit=new StringBuilder("Assisted receipt import\nReviewed and SAVE requested by: Local owner\nAt: "+Instant.now()+"\n");
        for(var issue:draft.importIssues)audit.append("Reviewed ").append(issue.field()).append(": ").append(issue.explanation())
                .append(" | original text: ").append(Objects.toString(issue.sourceText(),"not supplied")).append('\n');
        draft.evidence.add(new ReceiptEvidence(id,draft.id,"text/plain",audit.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void checkpointDraft(Draft draft) throws IOException {
        if(repository.storagePath().isEmpty())return;
        Draft captured=draft.copy();
        if(captured.tax==null)captured.tax=BigDecimal.ZERO;
        if(captured.total==null)captured.total=BigDecimal.ZERO;
        ReceiptDraftFile.write(ReceiptDraftFile.folder(repository.storagePath().orElseThrow()),
                new ReceiptDraftFile.Checkpoint(draft.id,ReceiptSnapshot.from(captured.transaction()),draft.metadata(),draft.actor,draft.imported,draft.importIssues,draft.evidence,
                        draft.entryStage,draft.entryStage<8,draft.tax!=null,draft.total!=null,draft.deferredEarnings));
    }
    private void checkpointCompletedDraft(Draft draft) {
        if(draft.correcting)return;
        try {checkpointDraft(draft);}catch(IOException failure){output.println("Draft checkpoint unavailable; keep this session open and retry PAUSE or SAVE.");}
    }
    private void removeDraft(Draft draft) throws IOException {
        if(repository.storagePath().isPresent())ReceiptDraftFile.remove(ReceiptDraftFile.folder(repository.storagePath().orElseThrow()),draft.id);
    }
    private void resumeDraft() throws IOException {
        if(repository.storagePath().isEmpty()){output.println("Draft recovery requires local storage.");return;}
        Path folder=ReceiptDraftFile.folder(repository.storagePath().orElseThrow());
        var drafts=new ArrayList<ReceiptDraftFile.Checkpoint>();
        if(java.nio.file.Files.isDirectory(folder))try(var files=java.nio.file.Files.list(folder)) {
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".draft")).sorted().toList()) {
                try {
                    var saved=ReceiptDraftFile.read(file);
                    if(repository.findById(saved.id()).isEmpty() && repository.findDeletedById(saved.id()).isEmpty())drafts.add(saved);
                } catch(IOException invalid){output.println("An unfinished draft could not be read; retained for recovery.");}
            }
        }
        if(drafts.isEmpty()){output.println("No unfinished receipts available.");return;}
        output.println("\nUNFINISHED RECEIPTS — not approved; excluded from balances");
        for(int i=0;i<drafts.size();i++) {
            var saved=drafts.get(i);var obs=saved.snapshot().observations();
            output.println((i+1)+". "+Objects.toString(saved.metadata().receiptNumber(),"Receipt number not entered")+" | "+Objects.toString(obs.purchaseDate(),"Date not entered")+" | "+ReceiptTimeInput.display(obs.printedTime()));
        }
        int index=questions(()->integer("Draft number (0 returns)",0,drafts.size()))-1;if(index<0)return;
        var saved=drafts.get(index);Draft draft=correctionDraft(saved.id(),saved.snapshot(),saved.metadata());
        draft.correcting=false;draft.actor=saved.actor();draft.imported=saved.imported();draft.importIssues.addAll(saved.issues());draft.evidence.addAll(saved.evidence());
        draft.deferredEarnings.addAll(saved.deferredEarnings());
        draft.entryStage=saved.partial()?saved.entryStage():8;
        if(saved.partial()) {
            if(!saved.taxKnown())draft.tax=null;if(!saved.totalKnown())draft.total=null;
            continueReceiptEntry(draft,draft.number,draft.entryStage);
        } else reviewReceipt(draft);
    }

    private void reviewReceipt(Draft draft) throws IOException {
        reviewReceiptActions(draft);
    }
    private void reviewReceiptActions(Draft draft) throws IOException {
        while (true) {
            try {checkpointDraft(draft);}
            catch(IOException failure){output.println("Could not checkpoint this draft. Keep this session open and retry PAUSE or SAVE.");}
            output.println("REVIEW — all changes are still unsaved");
            display(draft.receipt());
            String action = menuText(draft.imported ? "SAVE / EDIT / REBATES / ISSUES / ITEMIZED / TOTALS / ACCOUNTS / PAUSE / CANCEL / EXIT"
                    : "SAVE / EDIT / ITEMIZED / TOTALS / ACCOUNTS / ISSUES / PAUSE / CANCEL / EXIT").trim().toUpperCase(Locale.ROOT);
            if (action.equals("EXIT")) {
                if (yes(repository.storagePath().isPresent() ? "Exit CLI? Discard this unsaved receipt; saved receipts stay on disk"
                        : "Exit CLI? This unsaved receipt and session-only records will be lost", false)) throw new ExitCli();
                continue;
            }
            if(action.equals("PAUSE")) {
                if(repository.storagePath().isEmpty()){output.println("Pause requires local storage; keep editing or SAVE.");continue;}
                try {checkpointDraft(draft);}
                catch(IOException failure){output.println("Draft could not be kept. Your work is still open; retry PAUSE or SAVE.");continue;}
                output.println("Draft kept on this device. Choose 10 (Resume) at the main menu to resume. Balances are unchanged.");return;
            }
            if (action.equals("CANCEL")) {
                removeDraft(draft);
                output.println("Cancelled; nothing saved.");
                return;
            }
            if(action.equals("ISSUES")) {ReceiptIssues.print(draft.receipt(),output);continue;}
            if(draft.imported && action.equals("REBATES")) {
                editingSection=true;
                try {earnings(draft);}catch(EditDone done){output.println("Rebates retained; returned to receipt review.");}
                catch(Navigate navigation){output.println("Returned to receipt review; completed rewards retained.");}
                finally {editingSection=false;}
                continue;
            }
            if(action.equals("ITEMIZED")) { ReceiptWorksheetReport.itemized(draft.receipt(),output); continue; }
            if(action.equals("TOTALS")) { ReceiptWorksheetReport.compact(draft.receipt(),output); continue; }
            if(action.equals("ACCOUNTS")) { var records=new ArrayList<>(savedIds.stream().map(id->repository.findById(id).orElseThrow()).toList()); records.add(draft.receipt()); balances.printTransaction(draft.receipt(),records,output); continue; }
            if (action.equals("SAVE")) {
                if(ReceiptIssues.list(draft.receipt()).stream().anyMatch(ReceiptIssues.Issue::blocking)) {
                    ReceiptIssues.print(draft.receipt(),output);output.println("Receipt not saved: fix the blocking issues above. Draft retained.");continue;
                }
                if(draft.imported) {
                    // SAVE is the owner's approval. Background recovery notes never demand a second approval.
                    var duplicates=repository.findPossibleDuplicates(draft.metadata());
                    if(!duplicates.isEmpty()&&!yes("A saved receipt has matching metadata. Save a separate receipt anyway?",false))continue;
                    addImportReviewEvidence(draft);
                }
                var contents=ReceiptSnapshot.from(draft.transaction());var evidence=List.copyOf(draft.evidence);
                if(last==null || !last.id().equals(draft.id) || !last.contents().equals(contents)
                        || !Objects.equals(last.metadata(),draft.metadata()) || !last.evidence().equals(evidence)
                        || !last.context().equals(new ReceiptCreateCtx(draft.actor)))
                    last = new PendingSave(draft.id, contents, draft.metadata(), evidence,
                        new ReceiptWriteRequest(UUID.randomUUID(), Instant.now()), new ReceiptCreateCtx(draft.actor));
                ReceiptWriteResult result;
                try { result = last.submit(repository); }
                catch (IllegalArgumentException | IllegalStateException failure) {
                    output.println("Receipt not saved: " + failure.getMessage() + "; draft retained for correction or retry.");
                    continue;
                }
                savedIds.add(draft.id);
                try {removeDraft(draft);}catch(IOException cleanup){output.println("Receipt saved; an obsolete draft could not be cleared.");}
                output.println("Save result: " + writeSummary(result));
                output.println("For online gift-card offers spanning receipts, use menu 7 to track eligible purchases and notes.");
                var committed = repository.findById(draft.id).orElseThrow();
                display(committed);
                displayAccountCarry(null, committed);
                confirmReceiptSaved(committed);
                return;
            }
            if (action.equals("BACK")) {
                try { if (questions(() -> yes("Attach a receipt photo or PDF?", false))) evidence(draft); }
                catch (Navigate navigation) { output.println("Draft retained; returned to review."); }
                continue;
            }
            if (!action.equals("EDIT")) continue;
            try {
            String section = menuText("Edit: DETAILS / ITEM / CHECKOUT / WORKSHEET / DISCOUNTS / COUPONS / PROMOTIONS / EARNINGS / ACCOUNT / SAVINGS / SAVINGS LINKS / EVIDENCE / DONE")
                    .trim().toUpperCase(Locale.ROOT);
            if(section.equals("DONE")) continue;
            editingSection = true;
            output.println("Edit one field at a time. DONE returns to review; SAVE approves the receipt.");
            editingItem = -1; editingEarning=null; editingCoupon=null;
            if (editSelectedFields(draft, section)) continue;
            switch (section) {
                case "DETAILS" -> {
                    draftQuestions(draft, () -> {
                        draft.number = optional("Receipt number (blank unknown)");
                        detailsFields(draft);
                    });
                }
                case "ITEM" -> {
                    showItems(draft);
                    int index = questions(() -> integer("Item number to edit (0 adds an item)", 0, draft.items.size())) - 1;
                    Item edited = readItem(index < 0 ? null : draft.items.get(index));
                    if (edited != null) {
                        if (index < 0) draft.items.add(edited);
                        else draft.items.set(index, edited);
                        clearSavings(draft);
                        invalidateDraftPromotionAmounts(draft);
                    }
                }
                case "WORKSHEET" -> draft.worksheet=worksheetMetadata(draft.transaction(),draft.worksheet,menuText("GROUPS / USES / ORDER").toUpperCase(Locale.ROOT));
                case "CHECKOUT" -> { checkout(draft); clearSavings(draft); invalidateDraftPromotionAmounts(draft); }
                case "DISCOUNTS" -> {
                    draftQuestions(draft, () -> {
                        var tx = draft.transaction();
                        var itemDiscounts = draft.items.stream().flatMap(item -> item.deductions().stream()).toList();
                        var kinds = classifyDiscounts(itemDiscounts, tx.getObservations().discountKinds());
                        for (int i = 0; i < draft.items.size(); i++) {
                            var item = draft.items.get(i);
                            var updatedKinds = new HashMap<>(item.kinds());
                            item.deductions().forEach(c -> updatedKinds.put(c.getApplicationId(), kinds.get(c.getApplicationId())));
                            draft.items.set(i, new Item(item.item(), item.deductions(), Map.copyOf(updatedKinds)));
                        }
                        clearSavings(draft); invalidateDraftPromotionAmounts(draft);
                    });
                }
                case "COUPONS" -> { editSharedCoupons(draft); clearSavings(draft); invalidateDraftPromotionAmounts(draft); }
                case "PROMOTIONS" -> receiptPromotion(draft);
                case "EARNINGS" -> { editEarnings(draft); clearSavings(draft); }
                case "ACCOUNT" -> account(draft);
                case "SAVINGS" -> savings(draft);
                case "SAVINGS LINKS" -> detailedSavings(draft);
                case "EVIDENCE" -> evidence(draft);
                default -> output.println("Choose one of the named sections.");
            }
            } catch (EditDone done) { output.println("Returned to receipt review; completed edits retained."); }
            catch (Navigate navigation) { output.println("Returned to receipt review; draft retained."); }
            finally { editingSection = false; }
        }
    }

    /** A saved correction uses the same field editor without changing legacy snapshot contents. */
    private Draft correctionDraft(ReceiptSnapshot snapshot, ReceiptMeta metadata) {
        return correctionDraft(UUID.randomUUID(),snapshot,metadata);
    }
    private Draft correctionDraft(UUID id,ReceiptSnapshot snapshot,ReceiptMeta metadata) {
        Draft draft = new Draft(id);
        draft.number = metadata == null ? null : metadata.receiptNumber();
        draft.retailer = metadata == null ? "Unknown" : metadata.retailer();
        draft.actor = "Local owner"; draft.correcting = true;draft.purchaseChannel=metadata==null?ReceiptMeta.PurchaseChannel.UNKNOWN:metadata.purchaseChannel();
        var o = snapshot.observations();
        draft.date=o.purchaseDate(); draft.time=o.printedTime(); draft.account=o.walgreensCash();
        draft.savingPercent=o.receiptSavingValuePercent(); draft.footerStatus=o.footerStatus();
        draft.worksheet=o.worksheet(); draft.earnings.addAll(o.earnings()); draft.unknownRR.addAll(o.unidentifiedRR());
        draft.savings.addAll(o.savings()); draft.promotionLinks.addAll(o.promotionLinks());
        draft.tax=snapshot.receiptTotals().taxAmount(); draft.total=snapshot.receiptTotals().finalTotal();
        for(var line:snapshot.purchaseItems()) {
            var deductions=snapshot.appliedCoupons().stream().filter(c->c.getLineItemIds().equals(List.of(line.lineItemId()))).toList();
            var kinds=new HashMap<UUID,DiscountKind>();
            deductions.forEach(c->{var kind=o.discountKinds().get(c.getApplicationId());if(kind!=null)kinds.put(c.getApplicationId(),kind);});
            draft.items.add(new Item(new PurchaseItem(line.lineItemId(),line.productName(),line.productCode(),line.regularUnitPrice(),line.saleUnitPrice(),line.quantity()),deductions,kinds));
        }
        snapshot.appliedCoupons().stream().filter(c->c.getLineItemIds().size()!=1).forEach(c->{draft.sharedCoupons.add(c);var kind=o.discountKinds().get(c.getApplicationId());if(kind!=null)draft.sharedKinds.put(c.getApplicationId(),kind);});
        for(var payment:snapshot.payments()) draft.payments.add(new Payment(payment.paymentId(),payment.paymentType(),payment.amount(),payment.notes(),payment.accountName()));
        draft.knownRR.addAll(snapshot.registerRewardRedemptions());
        return draft;
    }

    /** Each field is applied only after its own question succeeds. DONE never discards completed fields. */
    private boolean editSelectedFields(Draft draft,String section) {
        if(!List.of("DETAILS","ITEM","CHECKOUT","TOTALS","ACCOUNT","SAVINGS","EARNINGS","COUPONS","DISCOUNTS","REGULAR PRICES").contains(section)) return false;
        boolean completedFields = false;
        while(true) {
            String choices=switch(section) {
                case "DETAILS" -> "NUMBER / RETAILER / DATE / TIME / ORDER TYPE" + (draft.correcting ? "" : " / ENTRANT");
                case "ITEM" -> "SELECT / DESCRIPTION / SKU / REGULAR PRICE / PRICE / QUANTITY / STORE COUPONS AND PROMOS / MANUFACTURER COUPONS";
                case "REGULAR PRICES" -> "SELECT / REGULAR PRICE";
                case "EARNINGS" -> "SELECT / AMOUNT / STATUS / OFFER / PRODUCTS / EXPIRY / VALUE";
                case "COUPONS" -> "SELECT / LABEL / AMOUNT / REQUIRED / PRODUCTS";
                case "DISCOUNTS" -> "SELECT / KIND / ADD / LABEL / AMOUNT / REQUIRED / PRODUCTS";
                case "CHECKOUT" -> "TAX / RR / WC / PAYMENTS / PRINTED TOTAL";
                case "TOTALS" -> "TAX / PRINTED TOTAL";
                case "ACCOUNT" -> "START / EARNED / USED / CLOSE / NOT PRINTED";
                default -> "MYWALGREENS / MANUFACTURER / TOTAL SAVINGS / PERCENT / NOT PRINTED";
            };
            String field;
            String sectionTitle=switch(section) {
                case "DETAILS" -> "RECEIPT DETAILS";case "ITEM" -> "PRODUCTS";case "CHECKOUT" -> "TAX AND PAYMENT";case "TOTALS" -> "TAX AND PRINTED TOTAL";
                case "DISCOUNTS" -> "DISCOUNTS AND STORE PROMOTIONS";case "EARNINGS" -> "REWARDS AND REBATES";
                case "ACCOUNT" -> "PRINTED ACCOUNT BALANCES";case "SAVINGS" -> "PRINTED SAVINGS FOOTER";default -> section;
            };
            if(section.equals("ITEM")) {
                if(editingItem>=0 && editingItem<draft.items.size()) {
                    var selected=draft.items.get(editingItem).item();
                    output.println("\nSelected product: line "+(editingItem+1)+" — "+selected.getProductName());
                    output.println("SKU / UPC: "+Objects.toString(selected.getProductCode(),"Not entered")
                            +" | Quantity: "+selected.getQuantity());
                    output.println("Regular price each: "+Objects.toString(selected.getRegularUnitPrice(),"Not entered")
                            +" | Entered price each: "+selected.getSaleUnitPrice());
                } else output.println("\nChoose a product first, then choose what to change.");
                output.println("To change an existing discount, use Edit > Discounts and store promotions.");
            }
            try { field=numberedMenu("EDIT "+sectionTitle,"Field to change","Field to change",choices+(section.equals("EARNINGS")?" / ADD / UPDATE / DELETE / DONE":List.of("ITEM","COUPONS").contains(section)?" / ALL / ADD / UPDATE / DELETE / DONE":section.equals("DISCOUNTS")?" / ALL / UPDATE / DELETE / DONE":" / ALL / DONE")).trim().toUpperCase(Locale.ROOT); }
            catch(EditDone done) { return true; }
            catch(Navigate navigation) { return true; }
            if(List.of("DONE","BACK","CANCEL").contains(field)) return true;
            if(field.equals("UPDATE"))field="SELECT";
            if(field.equals("DELETE") && List.of("ITEM","COUPONS","DISCOUNTS","EARNINGS").contains(section)) {
                deleteDraftEntry(draft,section);checkpointCompletedDraft(draft);continue;
            }
            if(field.equals("ADD") && section.equals("ITEM")) {
                var added=readItem(null);if(added!=null){draft.items.add(added);clearSavings(draft);invalidateDraftPromotionAmounts(draft);checkpointCompletedDraft(draft);}continue;
            }
            if(field.equals("ADD") && section.equals("EARNINGS")) {
                var added=readEarning(draft,null);if(added!=null){draft.earnings.add(added);clearSavings(draft);checkpointCompletedDraft(draft);}continue;
            }
            if(section.equals("EARNINGS") && (field.equals("ADD") || field.equals("ALL"))) {
                editEarnings(draft);clearSavings(draft);return true;
            }
            if(field.equals("ALL")) {
                if(completedFields) output.println("Completed edits retained. Reopen the section and choose ALL to start its full form.");
                return completedFields;
            }
            try {
                if(section.equals("DETAILS")) {
                    switch(field) {
                        case "NUMBER" -> draft.number=questions(()->optional("Receipt number (blank unknown)"));
                        case "RETAILER" -> draft.retailer=questions(()->required("Retailer"));
                        case "DATE" -> { draft.date=questions(()->date("Receipt date (blank unknown)"));invalidateDraftPromotionAmounts(draft); }
                        case "TIME" -> draft.time=questions(this::receiptTime);
                        case "ORDER TYPE" -> draft.purchaseChannel=questions(()->choice("Order type — online orders cannot earn or use Register Rewards",ReceiptMeta.PurchaseChannel.class));
                        case "ENTRANT" -> { if(draft.correcting) {output.println("The original entrant is preserved in receipt history.");continue;} draft.actor=questions(()->required("Entered by")); }
                        default -> { output.println("Choose a listed field or DONE.");continue; }
                    }
                } else if(section.equals("CHECKOUT") || section.equals("TOTALS")) {
                    switch(field) {
                        case "TAX","1" -> draft.tax=questions(()->amount("Tax printed on receipt",false,false));
                        case "PRINTED TOTAL","TOTAL","5" -> draft.total=questions(()->amount("Final total PRINTED on receipt",false,false));
                        case "RR","2" -> { if(section.equals("TOTALS"))continue;editRRDeduction(draft); }
                        case "WC","3" -> { if(section.equals("TOTALS"))continue;editWCDeduction(draft); }
                        case "PAYMENTS","4" -> { if(section.equals("TOTALS"))continue;editPayment(draft); }
                        default -> { output.println("Choose a listed field or DONE.");continue; }
                    }
                    clearSavings(draft);
                } else if(section.equals("ITEM") || section.equals("REGULAR PRICES")) {
                    editItemField(draft,field);
                } else if(section.equals("EARNINGS")) {
                    editEarningField(draft,field);
                } else if(section.equals("COUPONS") || section.equals("DISCOUNTS")) {
                    editCouponField(draft,field,section.equals("DISCOUNTS"));
                } else if(section.equals("ACCOUNT")) {
                    if(field.equals("NOT PRINTED")) { markFooterNotPrinted(draft);continue; }
                    var a=draft.account;
                    BigDecimal start=a==null?null:a.startingBalance(),earned=a==null?null:a.earned(),used=a==null?null:a.used(),close=a==null?null:a.endingBalance();
                    switch(field) {
                        case "START" -> start=questions(()->amount("Receipt Opening Balance (blank unknown)",true,false,true));
                        case "EARNED" -> earned=questions(()->amount("Receipt Earned This Visit (blank unknown)",true,false));
                        case "USED" -> used=questions(()->amount("Receipt Redeemed This Visit (blank unknown)",true,false));
                        case "CLOSE" -> close=questions(()->amount("Receipt Closing Balance (blank unknown)",true,false,true));
                        default -> { output.println("Choose a listed field or DONE.");continue; }
                    }
                    draft.account=new WalgreensCashAccount(start,used,earned,close);draft.footerStatus=FooterStatus.PRINTED;
                } else {
                    if(field.equals("NOT PRINTED")) {markFooterNotPrinted(draft);continue;}
                    switch(field) {
                        case "MYWALGREENS" -> draftQuestions(draft,()->printedSavings(draft,"myWalgreens Savings","Receipt myWalgreens Savings"));
                        case "MANUFACTURER" -> draftQuestions(draft,()->printedSavings(draft,"Mfg coupon savings","Receipt MFG Coupon savings"));
                        case "TOTAL SAVINGS","TOTAL" -> draftQuestions(draft,()->printedSavings(draft,"Your total Savings","Receipt Total Savings"));
                        case "PERCENT" -> draft.savingPercent=questions(()->amount("Receipt Saving Value (percentage, without %)",false,false));
                        default -> {output.println("Choose a listed field or DONE.");continue;}
                    }
                    draft.footerStatus=FooterStatus.PRINTED;
                }
                if(field.equals("SELECT")) output.println("Selection retained. Choose a field to change, or DONE.");
                else {
                    completedFields = true;
                    if(!draft.correcting)try {checkpointDraft(draft);}catch(IOException failure){output.println("Draft checkpoint unavailable; completed edit is still open.");}
                    output.println("Field updated in draft. Choose another field or DONE; SAVE is still required.");
                }
            } catch(EditDone done) { return true; }
            catch(Navigate navigation) { output.println("Field cancelled; completed edits retained. Choose another field or DONE."); }
            catch(IllegalArgumentException invalid) { output.println("Field not changed: " + invalid.getMessage() + ". Completed edits retained."); }
        }
    }

    private void deleteDraftEntry(Draft draft,String section) {
        questions(()-> {
            if(section.equals("ITEM")) {
                if(draft.items.size()<=1){output.println("Keep at least one product on the receipt. Use Delete receipt for the entire transaction.");return true;}
                showItems(draft);int index=integer("Product number to delete (0 cancels)",0,draft.items.size())-1;if(index<0)return true;
                var item=draft.items.get(index);UUID id=item.item().getLineItemId();
                boolean linked=draft.sharedCoupons.stream().anyMatch(c->c.getLineItemIds().contains(id))
                        || draft.earnings.stream().anyMatch(e->e.lineItemIds().contains(id))
                        || draft.promotionLinks.stream().anyMatch(l->l.eligibleLineIds().contains(id))
                        || draft.worksheet.uses().values().stream().anyMatch(u->u.lineIds().contains(id));
                if(linked){output.println("This product is linked to coupons, rewards, or promotions. Update or delete those links before deleting the product.");return true;}
                if(!yes("Delete "+item.item().getProductName()+" and its item discounts from this draft?",false))return true;
                draft.items.remove(index);editingItem=-1;
            } else if(section.equals("EARNINGS")) {
                if(draft.earnings.isEmpty()){output.println("No rewards or rebates to delete.");return true;}
                for(int i=0;i<draft.earnings.size();i++){var e=draft.earnings.get(i);output.println((i+1)+". "+e.provider()+" | "+Objects.toString(e.offerReference(),"No offer description"));}
                int index=integer("Reward / rebate number to delete (0 cancels)",0,draft.earnings.size())-1;if(index<0)return true;
                var earning=draft.earnings.get(index);
                if(!yes("Delete "+earning.provider()+" | "+Objects.toString(earning.offerReference(),"No offer description")+" from this draft?",false))return true;
                draft.earnings.remove(index);draft.deferredEarnings.remove(earning.earningId());
                var groups=new HashMap<>(draft.worksheet.earningGroups());groups.remove(earning.earningId());
                draft.worksheet=new ReceiptWorksheetData(groups,draft.worksheet.uses(),draft.worksheet.dayOrder());editingEarning=null;
            } else {
                var coupons=new ArrayList<CouponApplied>();draft.items.forEach(i->coupons.addAll(i.deductions()));coupons.addAll(draft.sharedCoupons);
                if(coupons.isEmpty()){output.println("No coupons or discounts to delete.");return true;}
                for(int i=0;i<coupons.size();i++)output.println((i+1)+". "+coupons.get(i).getPrintedLabel()+" | "+dollars(coupons.get(i).getAppliedAmount()));
                int index=integer("Coupon / discount number to delete (0 cancels)",0,coupons.size())-1;if(index<0)return true;
                var coupon=coupons.get(index);UUID id=coupon.getApplicationId();
                if(!yes("Delete "+coupon.getPrintedLabel()+" ("+dollars(coupon.getAppliedAmount())+") from this draft?",false))return true;
                for(int i=0;i<draft.items.size();i++) {var item=draft.items.get(i);var kinds=new HashMap<>(item.kinds());kinds.remove(id);
                    draft.items.set(i,new Item(item.item(),item.deductions().stream().filter(c->!c.getApplicationId().equals(id)).toList(),kinds));}
                draft.sharedCoupons.removeIf(c->c.getApplicationId().equals(id));draft.sharedKinds.remove(id);editingCoupon=null;
            }
            clearSavings(draft);invalidateDraftPromotionAmounts(draft);
            output.println("Deleted from draft. SAVE applies the change; previous saved revisions remain in history.");return true;
        });
    }

    private void markFooterNotPrinted(Draft draft) {
        draft.footerStatus=FooterStatus.NOT_PRINTED;draft.account=null;draft.savingPercent=null;
        draft.savings.removeIf(s->List.of("myWalgreens Savings","Mfg coupon savings","Your total Savings").contains(s.printedLabel()));
        output.println("Footer marked Not printed; rewards and payments retained.");
    }

    /** Both receipt entry and editing use the same status choices. */
    private RebateStatus earningStatus(EarningKind kind, BenefitKind form) {
        output.println("1. Expected, not confirmed\n2. Submitted, awaiting result");
        output.println("3. Approved / earned\n4. "+(form==BenefitKind.POINTS?"Points added to account":"Added to program account"));
        output.println(kind==EarningKind.REGISTER_REWARD
                ? "5. Register Reward Printed\n6. Rejected / not issued"
                : "5. Received / delivered\n6. Rejected");
        return switch(integer("What has happened to this benefit?",1,6)) {
            case 1 -> RebateStatus.EXPECTED;
            case 2 -> RebateStatus.SUBMITTED;
            case 3 -> RebateStatus.APPROVED;
            case 4 -> RebateStatus.CREDITED;
            case 5 -> RebateStatus.PAID;
            default -> RebateStatus.REJECTED;
        };
    }

    private UUID editingEarning;
    private UUID editingCoupon;
    private void editEarningField(Draft draft,String field) {
        if(field.equals("SELECT") || draft.earnings.stream().noneMatch(e->e.earningId().equals(editingEarning))) {
            for(int i=0;i<draft.earnings.size();i++) {var e=draft.earnings.get(i);output.println((i+1)+". "+e.provider()+" | "+Objects.toString(e.offerReference(),"No offer description"));}
            if(draft.earnings.isEmpty()) {output.println("No earnings; choose ALL to add one, or DONE.");return;}
            editingEarning=draft.earnings.get(questions(()->integer("Earning number",1,draft.earnings.size()))-1).earningId();
            if(field.equals("SELECT"))return;
        }
        int index=0;while(!draft.earnings.get(index).earningId().equals(editingEarning))index++;
        var old=draft.earnings.get(index);var expected=old.expected();var confirmed=old.confirmed();
        var status=old.status();var offer=old.offerReference();var expires=old.expirationDate();var valuation=old.valuation();
        var scope=old.scope();var ids=old.lineItemIds();
        switch(field) {
            case "AMOUNT" -> {
                var amount=questions(()->amount("Earning amount (original units)",false,true));
                var b=confirmed==null?expected:confirmed;
                var revised=new RebateBenefit(b.kind(),amount,b.currency(),b.program());
                if(confirmed==null)expected=revised;else confirmed=revised;
                if(b.kind()==BenefitKind.POINTS && b.amount().compareTo(amount)!=0) valuation=null;
            }
            case "STATUS" -> {
                var benefit=old.confirmed()==null?old.expected():old.confirmed();
                status=questions(()->earningStatus(old.kind(),benefit.kind()));
            }
            case "OFFER" -> offer=questions(()->optional("Offer description (blank unknown)"));
            case "EXPIRY" -> expires=questions(()->date("Expiration date (blank unknown)"));
            case "VALUE" -> valuation=questions(()-> {
                var value=amount("Saved USD valuation (blank unknown)",true,false);
                return value==null?null:new ReceiptObservations.RecordedValuation(value,Currency.getInstance("USD"),required("Valuation basis"));
            });
            case "PRODUCTS" -> {
                var chosen=questions(()->earningItems(draft));ids=chosen==null?List.of():chosen;
                scope=chosen==null?Scope.UNKNOWN:ids.isEmpty()?Scope.RECEIPT:ids.size()==1?Scope.ITEM:Scope.MULTI_ITEM;
            }
            default -> {output.println("Choose a listed field or DONE.");return;}
        }
        draft.earnings.set(index,new Earning(old.earningId(),old.kind(),old.provider(),offer,scope,ids,expected,confirmed,status,expires,
                old.instrumentId(),old.claimId(),old.evidenceIds(),old.recordedLifecycle(),old.requirement(),valuation));
        clearSavings(draft);
    }

    private void editCouponField(Draft draft,String field,boolean classification) {
        if(field.equals("ADD")) { addDiscount(draft,-1,classification?null:DiscountKind.MANUFACTURER); return; }
        var all=new ArrayList<CouponApplied>();draft.items.forEach(i->all.addAll(i.deductions()));all.addAll(draft.sharedCoupons);
        if(field.equals("SELECT") || all.stream().noneMatch(c->c.getApplicationId().equals(editingCoupon))) {
            if(all.isEmpty()) {output.println("No deductions; choose ALL for the full form, or DONE.");return;}
            for(int i=0;i<all.size();i++)output.println((i+1)+". "+all.get(i).getPrintedLabel()+" | "+dollars(all.get(i).getAppliedAmount()));
            editingCoupon=all.get(questions(()->integer("Coupon / deduction number",1,all.size()))-1).getApplicationId();
            if(field.equals("SELECT"))return;
        }
        var old=all.stream().filter(c->c.getApplicationId().equals(editingCoupon)).findFirst().orElseThrow();
        var label=old.getPrintedLabel();var value=old.getAppliedAmount();var required=old.getRequiredQuantity();
        List<UUID> ids=old.getLineItemIds();boolean wholeReceipt=old.isWholeReceipt();
        DiscountKind kind=null;
        if(classification && field.equals("KIND")) {
            output.println("1. Sale/promotion reduction\n2. Store coupon / IVC / online coupon code\n3. Manufacturer coupon");
            kind=switch(questions(()->integer("Discount type",1,3))){case 1->DiscountKind.SALE_PROMOTION;case 2->DiscountKind.STORE_COUPON;default->DiscountKind.MANUFACTURER;};
        } else switch(field) {
            case "LABEL" -> label=questions(()->required("Coupon description"));
            case "AMOUNT" -> value=questions(()->amount("Total coupon deduction (entered once)",false,true));
            case "REQUIRED" -> required=questions(()->optionalQuantity("Products required (blank unknown)"));
            case "PRODUCTS" -> {
                var selected=questions(()->earningItems(draft));
                wholeReceipt=selected!=null && selected.isEmpty();ids=selected==null?List.of():selected;
            }
            default -> {output.println("Choose a listed field or DONE.");return;}
        }
        var revised=new CouponApplied(old.getApplicationId(),label,value,ids,required,wholeReceipt);
        for(int i=0;i<draft.items.size();i++) {
            var item=draft.items.get(i);var deductions=new ArrayList<>(item.deductions());var kinds=new HashMap<>(item.kinds());
            for(int j=0;j<deductions.size();j++)if(deductions.get(j).getApplicationId().equals(editingCoupon)) {
                deductions.set(j,revised);if(kind!=null)kinds.put(editingCoupon,kind);
            }
            draft.items.set(i,new Item(item.item(),List.copyOf(deductions),Map.copyOf(kinds)));
        }
        for(int i=0;i<draft.sharedCoupons.size();i++)if(draft.sharedCoupons.get(i).getApplicationId().equals(editingCoupon)){draft.sharedCoupons.set(i,revised);if(kind!=null)draft.sharedKinds.put(editingCoupon,kind);}
        clearSavings(draft);invalidateDraftPromotionAmounts(draft);
    }

    /** Collect every answer before changing the draft; cancellation leaves it intact. */
    private void addDiscount(Draft draft,int selectedItem,DiscountKind fixedKind) {
        questions(() -> {
            DiscountKind kind=fixedKind;
            if(kind==null) {
                output.println("1. Sale/promotion reduction\n2. Store coupon / IVC / online coupon code\n3. Manufacturer coupon\n4. Unknown / mixed discount");
                kind=switch(integer("Discount type",1,4)) {
                    case 1 -> DiscountKind.SALE_PROMOTION;
                    case 2 -> DiscountKind.STORE_COUPON;
                    case 3 -> DiscountKind.MANUFACTURER;
                    default -> DiscountKind.OTHER;
                };
            }
            String label=required("Discount description");
            int count=kind==DiscountKind.MANUFACTURER?integer("Quantity of manufacturer coupons",1,1000):1;
            BigDecimal value=amount(kind==DiscountKind.MANUFACTURER?"Value per manufacturer coupon":"Total discount for selected products (not per unit)",false,true);
            Integer required=kind==DiscountKind.MANUFACTURER?optionalQuantity("Products required per coupon (blank unknown)"):null;
            List<UUID> ids=selectedItem>=0?List.of(draft.items.get(selectedItem).item().getLineItemId()):earningItems(draft);
            boolean wholeReceipt=ids!=null && ids.isEmpty();
            if(ids==null) ids=List.of();
            BigDecimal beforePrice=null;
            if(selectedItem>=0 && kind==DiscountKind.SALE_PROMOTION
                    && yes("Does the current item price already include this sale reduction?",false))
                beforePrice=amount("Unit price BEFORE this reduction (regular price if applicable)",false,false);
            if(beforePrice!=null) {
                var row=draft.items.get(selectedItem);var product=row.item();
                draft.items.set(selectedItem,new Item(new PurchaseItem(product.getLineItemId(),product.getProductName(),product.getProductCode(),product.getRegularUnitPrice(),beforePrice,product.getQuantity()),row.deductions(),row.kinds()));
            }
            for(int i=0;i<count;i++) {
                var coupon=new CouponApplied(UUID.randomUUID(),label,value,ids,required,wholeReceipt);
                draft.sharedCoupons.add(coupon);draft.sharedKinds.put(coupon.getApplicationId(),kind);
            }
            clearSavings(draft);invalidateDraftPromotionAmounts(draft);
            output.println("Discount added once. If the entered price already includes this reduction, correct PRICE to the price before that reduction. RR and Walgreens Cash used belong in CHECKOUT.");
            return true;
        });
    }

    private int editingItem = -1;
    private void editItemField(Draft draft,String field) {
        if(field.equals("SELECT") || editingItem<0 || editingItem>=draft.items.size()) {
            showItems(draft); editingItem=questions(()->integer("Item number",1,draft.items.size()))-1;
            if(field.equals("SELECT")) return;
        }
        if(field.equals("STORE COUPONS AND PROMOS")) { addDiscount(draft,editingItem,null);return; }
        if(field.equals("MANUFACTURER COUPONS")) { addDiscount(draft,editingItem,DiscountKind.MANUFACTURER);return; }
        int index=editingItem;var previous=draft.items.get(index);var item=previous.item();
        String name=item.getProductName(),sku=item.getProductCode();
        BigDecimal regular=item.getRegularUnitPrice(),price=item.getSaleUnitPrice();int quantity=item.getQuantity();
        switch(field) {
            case "DESCRIPTION" -> name=questions(()->required("Item description"));
            case "SKU" -> sku=questions(()->optional("SKU / UPC (blank unknown)"));
            case "REGULAR PRICE" -> regular=questions(()->amount("Regular unit price",false,false));
            case "PRICE" -> price=questions(()->amount("Entered price per item, before discounts recorded separately",false,false));
            case "QUANTITY" -> quantity=questions(()->integer("Quantity",1,Integer.MAX_VALUE));
            default -> {output.println("Choose a listed field or DONE.");return;}
        }
        draft.items.set(index,new Item(new PurchaseItem(item.getLineItemId(),name,sku,regular,price,quantity),previous.deductions(),previous.kinds()));
        clearSavings(draft);invalidateDraftPromotionAmounts(draft);
    }

    private void editRRDeduction(Draft draft) {
        if(draft.purchaseChannel==ReceiptMeta.PurchaseChannel.ONLINE && draft.unknownRR.isEmpty()) {output.println("Online orders cannot use Register Rewards.");return;}
        var result=questions(()-> {
            for(int i=0;i<draft.unknownRR.size();i++)output.println((i+1)+". "+dollars(draft.unknownRR.get(i).amount())+" | "+draft.unknownRR.get(i).recordedDescription());
            int index=integer("RR deduction (0 adds; DONE returns)",0,draft.unknownRR.size())-1;
            BigDecimal amount=amount("RR amount used (0 removes a draft deduction)",false,false);
            if(draft.purchaseChannel==ReceiptMeta.PurchaseChannel.ONLINE && amount.signum()>0)throw new IllegalArgumentException("Online orders cannot use Register Rewards; enter 0 to remove the incorrect use.");
            var old=index<0?null:draft.unknownRR.get(index);
            var use=amount.signum()==0?null:new UnidentifiedRR(old==null?UUID.randomUUID():old.eventId(),amount,rrDescription(),old==null?null:old.notes());
            return new java.util.AbstractMap.SimpleEntry<Integer,UnidentifiedRR>(index,use);
        });
        if(result.getKey()<0) {if(result.getValue()!=null)draft.unknownRR.add(result.getValue());}
        else if(result.getValue()==null)draft.unknownRR.remove((int)result.getKey());else draft.unknownRR.set(result.getKey(),result.getValue());
    }

    private void editWCDeduction(Draft draft) {
        var amount=questions(()->amount("Walgreens Cash used (0 if none)",false,false));
        var matches=draft.payments.stream().filter(p->p.getPaymentType()==PaymentType.WALGREENS_CASH).toList();
        if(matches.size()>1) {output.println("Multiple WC uses exist; use PAYMENTS to select one without merging its history.");return;}
        Payment old=matches.isEmpty()?null:matches.get(0);
        if(old!=null)draft.payments.remove(old);
        if(amount.signum()>0)draft.payments.add(new Payment(old==null?UUID.randomUUID():old.getPaymentId(),PaymentType.WALGREENS_CASH,amount,old==null?null:old.getNotes()));
    }

    private void editPayment(Draft draft) {
        record Change(int index,Payment payment) { }
        var change=questions(()-> {
            for(int i=0;i<draft.payments.size();i++) {var p=draft.payments.get(i);output.println((i+1)+". "+p.getPaymentType()+" | "+dollars(p.getAmount()));}
            int index=integer("Payment number (0 adds; DONE returns)",0,draft.payments.size())-1;
            Payment old=index<0?null:draft.payments.get(index);
            PaymentType type=old==null?null:old.getPaymentType();
            if(type==null) {output.println("1. Cash\n2. Card\n3. Gift card\n4. Other");type=switch(integer("Payment method",1,4)){case 1->PaymentType.CASH;case 2->PaymentType.CARD;case 3->PaymentType.GIFT_CARD;default->PaymentType.OTHER;};}
            BigDecimal value=amount("Payment amount (0 removes a draft payment)",false,false);
            if(value.signum()==0)return new Change(index,null);
            String notes=old==null?optional("Payment notes (blank unknown)"):old.getNotes();
            String account=old!=null?old.getAccountName():type==PaymentType.GIFT_CARD||type==PaymentType.OTHER?optional("Account name (blank unassigned)"):null;
            return new Change(index,new Payment(old==null?UUID.randomUUID():old.getPaymentId(),type,value,notes,account));
        });
        if(change.index()<0) {if(change.payment()!=null)draft.payments.add(change.payment());}
        else if(change.payment()==null)draft.payments.remove(change.index());else draft.payments.set(change.index(),change.payment());
    }

    private void enterProducts(Draft draft) {
        output.println("Enter purchased products only. Tax comes after the items.");
        boolean directEntry = draft.items.isEmpty();
        while (true) {
            if (!directEntry) {
                String action = menuText("Products: ADD / EDIT / DONE (BACK returns to receipt details)").trim().toUpperCase(Locale.ROOT);
                if (action.equals("BACK") || action.equals("CANCEL")) throw new Navigate(true);
                if (action.equals("DONE")) return;
                if (action.equals("EDIT")) {
                    try {
                        showItems(draft);
                        int selected = questions(() -> integer("Item number", 1, draft.items.size())) - 1;
                        Item changed = readItem(draft.items.get(selected));
                        if (changed != null) { draft.items.set(selected, changed); clearSavings(draft); invalidateDraftPromotionAmounts(draft); }
                    } catch (Navigate navigation) { output.println("Products retained."); }
                    continue;
                }
                if (!action.equals("ADD")) continue;
            }
            Item item = readItem(null);
            if (item == null) {
                if (draft.items.isEmpty()) { output.println("Enter at least one purchased item before checkout."); continue; }
                return;
            }
            draft.items.add(item);checkpointCompletedDraft(draft);
            while (true) {
                try {
                    itemEarnings(draft, item);
                    if (!questions(() -> yes("Add another purchased item? No continues to tax and checkout", false))) return;
                    directEntry = true;
                    break;
                } catch (Navigate navigation) { output.println("Current product retained; returned to its earnings."); }
            }
        }
    }

    /** Edits a captured revision without rebuilding or dropping unrelated financial records. */
    private void correctReceipt(ReceiptRecord original) throws IOException {
        ReceiptSnapshot proposed = ReceiptSnapshot.from(original.transaction());
        ReceiptMeta metadata = original.metadata();
        ReceiptWriteRequest request = null;
        ReceiptChangeContext context = null;
        String correctionReason = "Receipt correction saved by local owner";
        while (true) {
            output.println("CORRECT SAVED RECEIPT — original revision " + original.revision());
            output.println("BEFORE — saved revision " + original.revision() + " (retained in history)");
            output.println("PROPOSED — unsaved changes");
            display(new ReceiptRecord(original.transactionId(), original.status(), original.revision(),
                    proposed.toTransaction(), metadata, original.evidenceIds()));
            String action = menuText("DETAILS / ITEM / CHECKOUT / WORKSHEET / REGULAR PRICES / DISCOUNTS / COUPONS / PROMOTIONS / EARNINGS / ACCOUNT / SAVINGS / RR NOTES / REASON / SAVE / CANCEL")
                    .trim().toUpperCase(Locale.ROOT);
            if (action.equals("CANCEL")) return; // cancellation was confirmed before routing
            if (action.equals("BACK")) {
                if (ReceiptSnapshot.from(original.transaction()).equals(proposed) && Objects.equals(original.metadata(), metadata)
                        || navigationConfirmation("Discard proposed corrections and return to receipt selection?")) return;
                continue;
            }
            if (action.equals("REASON")) { correctionReason = required("Correction reason"); request = null; continue; }
            if (action.equals("SAVE")) {
                if (request == null) {
                    try {
                        var pending = proposed;
                        var reviewed = questions(() -> reviewCorrectionPromotions(original, pending, false));
                        if (reviewed == null) continue;
                        proposed = reviewed;
                        context = new ReceiptChangeContext("Local owner", correctionReason, "Local owner", Instant.now());
                    } catch (Navigate navigation) { output.println("Proposed corrections retained; returned to review."); continue; }
                    if (context == null) continue;
                    request = new ReceiptWriteRequest(UUID.randomUUID(), Instant.now());
                }
                try {
                    var result = repository.update(original.transactionId(), original.revision(),
                            proposed.toTransaction(), metadata, List.of(), request, context);
                    output.println("Correction saved: " + writeSummary(result));
                    var updated = repository.findById(original.transactionId()).orElseThrow();
                    display(updated);
                    displayHistory(updated);
                    displayAccountCarry(original, updated);
                    confirmReceiptSaved(updated);
                    return;
                } catch (java.util.ConcurrentModificationException conflict) {
                    output.println("Revision conflict: saved receipt changed. Proposed edits remain below; nothing overwritten.");
                    output.println("Current saved revision: " + repository.findById(original.transactionId()).orElseThrow().revision());
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    output.println("Correction not saved: " + failure.getMessage() + "; proposed edits retained. SAVE retries the same request.");
                }
                continue;
            }
            // Any further edit requires a fresh review, confirmation, and request identity.
            request = null;
            context = null;
            try {
                ReceiptMeta editedMetadata = metadata;
                var lines = new ArrayList<>(proposed.purchaseItems());
                var applied = new ArrayList<>(proposed.appliedCoupons());
                var totals = proposed.receiptTotals();
                var editedPayments = proposed.payments();
                var obs = proposed.observations();
                editingSection = true;
                var editDraft = correctionDraft(proposed, metadata);
                editingItem = -1; editingEarning=null; editingCoupon=null;
                boolean fieldsEdited = editSelectedFields(editDraft, action);
                if(fieldsEdited) {
                    var edited = ReceiptSnapshot.from(editDraft.transaction());
                    lines.clear(); lines.addAll(edited.purchaseItems());
                    var revisedCoupons = new LinkedHashMap<UUID,CouponApplied>();
                    edited.appliedCoupons().forEach(c->revisedCoupons.put(c.getApplicationId(),c));
                    applied.removeIf(c->!revisedCoupons.containsKey(c.getApplicationId()));
                    applied.replaceAll(c->revisedCoupons.get(c.getApplicationId()));
                    var existingIds=applied.stream().map(CouponApplied::getApplicationId).collect(java.util.stream.Collectors.toSet());
                    edited.appliedCoupons().stream().filter(c->!existingIds.contains(c.getApplicationId())).forEach(applied::add);
                    totals = edited.receiptTotals(); editedPayments = edited.payments(); obs = edited.observations();
                    var mergedKinds = new HashMap<>(edited.observations().discountKinds());
                    if(action.equals("DISCOUNTS")) for(var item:editDraft.items) mergedKinds.putAll(item.kinds());
                    obs = new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),obs.earnings(),obs.unidentifiedRR(),obs.walgreensCash(),obs.savings(),mergedKinds,obs.receiptSavingValuePercent(),obs.promotionLinks(),obs.worksheet(),obs.footerStatus());
                    if(action.equals("DETAILS")) editedMetadata = new ReceiptMeta(editDraft.retailer,editDraft.number,
                            metadata == null ? null : metadata.purchasedAt(),metadata == null ? null : metadata.storeReference(),editDraft.purchaseChannel);
                } else switch (action) {
                    case "DETAILS" -> {
                        var oldMetadata = metadata;
                        var entered = questions(() -> new CorrectionDetails(new ReceiptMeta(required("Retailer"), optional("Receipt number (blank unknown)"),
                                oldMetadata == null ? null : oldMetadata.purchasedAt(), oldMetadata == null ? null : oldMetadata.storeReference(),oldMetadata==null?ReceiptMeta.PurchaseChannel.UNKNOWN:oldMetadata.purchaseChannel()),
                                date("Receipt date (blank unknown)"), receiptTime()));
                        editedMetadata = entered.metadata();
                        LocalDate date = entered.date();
                        String time = entered.time();
                        obs = new ReceiptObservations(date, time, obs.earnings(), obs.unidentifiedRR(),
                                obs.walgreensCash(), obs.savings(), obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus());
                    }
                    case "RR NOTES" -> {
                        var uses = new ArrayList<>(obs.unidentifiedRR());
                        if (uses.isEmpty()) { output.println("No receipt-reported RR uses."); continue; }
                        for (int i = 0; i < uses.size(); i++) output.println((i + 1) + ". " + dollars(uses.get(i).amount()) + " | " + uses.get(i).recordedDescription());
                        record Note(int index, String text) { }
                        var note = questions(() -> new Note(integer("RR deduction number", 1, uses.size()) - 1, text("Notes (blank clears)")));
                        var old = uses.get(note.index());
                        uses.set(note.index(), new UnidentifiedRR(old.eventId(), old.amount(), old.recordedDescription(), note.text().isBlank() ? null : note.text()));
                        obs = new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),obs.earnings(),uses,obs.walgreensCash(),obs.savings(),obs.discountKinds(),obs.receiptSavingValuePercent(),obs.promotionLinks(), obs.worksheet(), obs.footerStatus());
                    }
                    case "ITEM" -> {
                        for (int i = 0; i < lines.size(); i++) output.println((i + 1) + ". " + lines.get(i).productName());
                        if (lines.isEmpty()) { output.println("No purchase lines to correct."); continue; }
                        var changed = questions(() -> {
                            int index = integer("Item number", 1, lines.size()) - 1;
                            var old = lines.get(index);
                            return new PurchaseLineSnap(old.lineItemId(), required("Item description"), optional("SKU / UPC (blank unknown)"),
                                    old.regularUnitPrice(), amount("Unit price", false, false), integer("Quantity", 1, Integer.MAX_VALUE));
                        });
                        for (int index = 0; index < lines.size(); index++) if (lines.get(index).lineItemId().equals(changed.lineItemId())) lines.set(index, changed);
                        output.println("Existing discounts retained; review their applicability to the corrected item.");
                    }
                    case "WORKSHEET" -> {
                        var current=proposed.toTransaction();
                        var result=questions(()->worksheetMetadata(current,current.getObservations().worksheet(),menuText("GROUPS / USES / ORDER").toUpperCase(Locale.ROOT)));
                        obs=new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),obs.earnings(),obs.unidentifiedRR(),obs.walgreensCash(),obs.savings(),obs.discountKinds(),obs.receiptSavingValuePercent(),obs.promotionLinks(),result,obs.footerStatus());
                    }
                    case "REGULAR PRICES" -> {
                        var revised = questions(() -> {
                            var values = new ArrayList<PurchaseLineSnap>();
                            for (int i=0;i<lines.size();i++) {
                                var old=lines.get(i);
                                output.println("Line " + (i+1) + ": " + old.productName());
                                var regularPrice = amount("Regular unit price (blank keeps existing)",true,false);
                                values.add(new PurchaseLineSnap(old.lineItemId(),old.productName(),old.productCode(),regularPrice==null?old.regularUnitPrice():regularPrice,old.saleUnitPrice(),old.quantity()));
                            }
                            return values;
                        });
                        lines.clear(); lines.addAll(revised);
                    }
                    case "TOTALS" -> totals = questions(() -> new ReceiptTotalsSnap(amount("Tax printed on receipt", false, false),
                            amount("Final total PRINTED on receipt", false, false)));
                    case "DISCOUNTS" -> {
                        var priorKinds = obs.discountKinds();
                        var kinds = questions(() -> classifyDiscounts(applied, priorKinds));
                        obs = new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), obs.earnings(), obs.unidentifiedRR(),
                                obs.walgreensCash(), obs.savings(), kinds, obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus());
                    }
                    case "PROMOTIONS", "COUPONS", "EARNINGS", "ACCOUNT", "SAVINGS" -> {
                        Draft draft = new Draft();
                        draft.retailer = metadata == null ? "Unknown" : metadata.retailer();
                        draft.earnings.addAll(obs.earnings());
                        draft.promotionLinks.addAll(obs.promotionLinks());
                        draft.worksheet = obs.worksheet();
                        draft.footerStatus = obs.footerStatus();
                        draft.unknownRR.addAll(obs.unidentifiedRR());
                        draft.account = obs.walgreensCash();
                        draft.savings.addAll(obs.savings());
                        draft.savingPercent = obs.receiptSavingValuePercent();
                        for (var line : lines) draft.items.add(new Item(new PurchaseItem(line.lineItemId(),
                                line.productName(), line.productCode(), line.regularUnitPrice(), line.saleUnitPrice(),
                                line.quantity()), List.of(), Map.of()));
                        if (action.equals("PROMOTIONS")) receiptPromotion(draft);
                        var kinds = new HashMap<>(obs.discountKinds());
                        if (action.equals("COUPONS")) {
                            applied.stream().filter(c -> c.getLineItemIds().size() > 1
                                    && kinds.get(c.getApplicationId()) == DiscountKind.MANUFACTURER)
                                    .forEach(draft.sharedCoupons::add);
                            var previousIds = draft.sharedCoupons.stream().map(CouponApplied::getApplicationId).toList();
                            editSharedCoupons(draft);
                            applied.removeIf(c -> previousIds.contains(c.getApplicationId()));
                            previousIds.forEach(kinds::remove);
                            applied.addAll(draft.sharedCoupons);
                            draft.sharedCoupons.forEach(c -> kinds.put(c.getApplicationId(), DiscountKind.MANUFACTURER));
                            clearSavings(draft);
                        }
                        if (action.equals("EARNINGS")) { editEarnings(draft); clearSavings(draft); }
                        if (action.equals("ACCOUNT")) account(draft);
                        if (action.equals("SAVINGS")) savings(draft);
                        obs = new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), draft.earnings,
                                obs.unidentifiedRR(), draft.account, draft.savings, kinds, draft.savingPercent, draft.promotionLinks, draft.worksheet, draft.footerStatus);
                    }
                    default -> { output.println("Choose a displayed section or action."); continue; }
                }
                if (action.equals("ITEM") || action.equals("TOTALS") || action.equals("DISCOUNTS")) {
                    obs = new ReceiptObservations(obs.purchaseDate(), obs.printedTime(), obs.earnings(), obs.unidentifiedRR(),
                            obs.walgreensCash(),
                            obs.savings().stream().map(value -> new SavingsCategory(value.categoryId(), value.printedLabel(),
                                    value.currency(), value.reportedAmount(), List.of(), false)).toList(),
                            obs.discountKinds(), obs.receiptSavingValuePercent(), obs.promotionLinks(), obs.worksheet(), obs.footerStatus());
                }
                obs=retainWorksheet(obs,editedPayments.stream().map(coupons.repository.PaymentSnap::paymentId).toList(),proposed.registerRewardRedemptions().stream().map(RegisterRewardRedemption::getRedemptionId).toList());
                proposed = new ReceiptSnapshot(lines, proposed.coupons(), applied, proposed.promotions(),
                        editedPayments, proposed.rewards(), proposed.registerRewardRedemptions(), totals, obs);
                metadata = editedMetadata;
                if (List.of("ITEM", "DETAILS", "COUPONS", "DISCOUNTS").contains(action)) {
                    var pending = proposed;
                    var reviewed = questions(() -> reviewCorrectionPromotions(original, pending, true));
                    if (reviewed != null) proposed = reviewed;
                }
            } catch (EditDone done) {
                output.println("Returned to correction review; completed sections retained.");
            } catch (Navigate navigation) {
                output.println("Section cancelled; proposed corrections retained.");
            } catch (IllegalArgumentException failure) {
                output.println("Correction not applied: " + failure.getMessage());
            } finally { editingSection = false; }
        }
    }

    private Map<UUID, DiscountKind> classifyDiscounts(List<CouponApplied> discounts, Map<UUID, DiscountKind> original) {
        var kinds = new HashMap<>(original);
        while (true) {
            for (int i = 0; i < discounts.size(); i++) {
                var discount = discounts.get(i);
                output.println((i + 1) + ". " + discount.getPrintedLabel() + " | " + discount.getAppliedAmount()
                        + " | " + kinds.getOrDefault(discount.getApplicationId(), DiscountKind.OTHER));
            }
            int selected = integer("Discount number to classify (0 finishes)", 0, discounts.size()) - 1;
            if (selected < 0) return Map.copyOf(kinds);
            output.println("1. Sale/promotion reduction\n2. Store coupon\n3. Manufacturer coupon\n4. Unknown/mixed");
            var kind = switch (integer("Classification (mixed amounts require an item correction to split)", 1, 4)) {
                case 1 -> DiscountKind.SALE_PROMOTION;
                case 2 -> DiscountKind.STORE_COUPON;
                case 3 -> DiscountKind.MANUFACTURER;
                default -> DiscountKind.OTHER;
            };
            kinds.put(discounts.get(selected).getApplicationId(), kind);
        }
    }

    private record CorrectionDetails(ReceiptMeta metadata, LocalDate date, String time) { }

    /** Keeps partial date/time observations without inventing missing components. */
    private void detailsFields(Draft draft) {
        draft.time = receiptTime();
        draft.date = date("Receipt date (for example 10/4/26, 26/10/4, or October 4, 2026; blank unknown)");
        draft.retailer = required("Retailer (or ONLINE for an online order)");
        if(draft.retailer.equalsIgnoreCase("ONLINE")) {
            draft.purchaseChannel=ReceiptMeta.PurchaseChannel.ONLINE;
            draft.retailer=required("Online store name");
            output.println("Online order: Register Rewards cannot be earned or used.");
        }
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

    private record ItemPrice(BigDecimal regular, BigDecimal unit) { }
    private ItemPrice itemPrice() {
        while (true) {
            String raw = text("Price: regular/entered (7.49/5.49), or a single REGULAR unit price before discounts").trim();
            try {
                String[] values = raw.split("/",-1);
                if (values.length>2) throw new NumberFormatException();
                BigDecimal regular = values.length==2 ? new BigDecimal(values[0].trim()) : null;
                BigDecimal unit = new BigDecimal(values[values.length-1].trim());
                if (unit.signum()<0 || regular!=null && regular.signum()<0) throw new NumberFormatException();
                return new ItemPrice(regular==null?unit:regular,unit);
            } catch (NumberFormatException invalid) { output.println("Enter a nonnegative amount."); }
        }
    }

    /** Records each item's discounts immediately after price and quantity. */
    private Item readItemFields(Item previous) {
        output.println("ITEM");
        output.println("For a coupon covering different product lines, omit it here and enter COUPONS at tax or EDIT > COUPONS after all items.");
        String code = null, name = null;
        BigDecimal price = null, store = null, value = null, sale = BigDecimal.ZERO;
        BigDecimal regular = previous == null ? null : previous.item().getRegularUnitPrice();
        int storeKind = 1;
        int quantity = 1, coupons = 0, step = 0;
        Integer requiredQuantity = null;
        var visited = new java.util.ArrayDeque<Integer>();
        while (step < 10) {
            boolean asked = true;
            try {
                switch (step) {
                    case 0 -> code = optional("SKU / UPC (blank unknown; preserve leading zeros)");
                    case 1 -> name = required("Item description");
                    case 2 -> {
                        var entered = itemPrice(); price = entered.unit();
                        if (entered.regular() != null) regular = entered.regular();
                    }
                    case 3 -> {
                        quantity = integer("Quantity", 1, Integer.MAX_VALUE);
                        output.println("Calculated line total: " + price.multiply(BigDecimal.valueOf(quantity)).toPlainString());
                    }
                    case 4 -> store = amount("Separately entered sale/promotion reductions and store coupons (0 if none; do not repeat reductions already in unit price)", false, false);
                    case 5 -> {
                        if (store.signum() > 0) {
                            output.println("1. Sale/promotion price reduction\n2. Store coupon\n3. Contains both (split amounts)");
                            storeKind = integer("Discount classification", 1, 3);
                        } else { storeKind = 1; asked = false; }
                    }
                    case 6 -> {
                        if (storeKind == 3 && store.signum() > 0) {
                            do {
                                sale = amount("Sale/promotion portion of that total", false, false);
                                if (sale.compareTo(store) > 0) output.println("Sale portion cannot exceed total deductions.");
                            } while (sale.compareTo(store) > 0);
                        } else { sale = storeKind == 1 ? store : BigDecimal.ZERO; asked = false; }
                    }
                    case 7 -> coupons = integer("Quantity of MQs (0 if none)", 0, 1000);
                    case 8 -> {
                        if (coupons > 0) value = amount("Value of MQs", false, true);
                        else { value = null; asked = false; }
                    }
                    case 9 -> {
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
        PurchaseItem item = new PurchaseItem(id, name, code, regular, price, quantity);
        List<CouponApplied> deductions = new ArrayList<>();
        Map<UUID, DiscountKind> kinds = new LinkedHashMap<>();
        if (sale.signum() > 0) {
            CouponApplied applied = new CouponApplied("Sale/promotion reduction", sale, List.of(id));
            deductions.add(applied); kinds.put(applied.getApplicationId(), DiscountKind.SALE_PROMOTION);
        }
        BigDecimal storeCoupon = store.subtract(sale);
        if (storeCoupon.signum() > 0) {
            CouponApplied applied = new CouponApplied("Store coupon", storeCoupon, List.of(id));
            deductions.add(applied); kinds.put(applied.getApplicationId(), DiscountKind.STORE_COUPON);
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

    /** Keeps direct tax entry available while exposing shared coupons before checkout. */
    private BigDecimal checkoutTax(Draft draft) {
        while (true) {
            String value = text("Tax printed on receipt (or COUPONS / PROMOTIONS / DETAILED BREAKDOWN)").trim();
            if(value.equalsIgnoreCase("WORKSHEET")) { draft.worksheet=questions(()->worksheetMetadata(draft.transaction(),draft.worksheet,menuText("GROUPS / USES / ORDER").toUpperCase(Locale.ROOT))); continue; }
            if (value.equalsIgnoreCase("PROMOTIONS")) {
                try { receiptPromotion(draft); } catch (Navigate navigation) { output.println("Returned to tax; receipt retained."); }
                continue;
            }
            if (value.equalsIgnoreCase("COUPONS")) {
                try { editSharedCoupons(draft); clearSavings(draft); invalidateDraftPromotionAmounts(draft); }
                catch (Navigate navigation) { output.println("Returned to tax; receipt retained."); }
                continue;
            }
            try {
                BigDecimal tax = new BigDecimal(value.startsWith("$") ? value.substring(1) : value);
                if (tax.signum() >= 0) return tax;
            } catch (NumberFormatException ignored) { }
            output.println("Enter a nonnegative tax amount, or COUPONS.");
        }
    }

    private void invalidateDraftPromotionAmounts(Draft draft) {
        for (int i = 0; i < draft.promotionLinks.size(); i++) {
            var link = draft.promotionLinks.get(i);
            if (link.reviewedEligibleAmount() == null) continue;
            draft.promotionLinks.set(i, new ReceiptPromotionLink(link.promotionId(), link.eligibleLineIds(), null,
                    link.eligibilityNote(), link.reviewedAt()));
            output.println("Online eligible amount needs review after financial edits. Use PROMOTIONS before SAVE.");
        }
    }

    /** Captures only draft facts; the receipt repository publishes receipt and progress together. */
    private void receiptPromotion(Draft draft) {
        var offers = repository.giftCardPromotions();
        if (offers.isEmpty()) {
            output.println("Create an online offer now, then BACK to continue linking this receipt.");
            giftCardPromotions();
            offers = repository.giftCardPromotions();
            if (offers.isEmpty()) return;
        }
        draftQuestions(draft, () -> receiptPromotionFields(draft));
    }
    private void receiptPromotionFields(Draft draft) {
        var offers = repository.giftCardPromotions();
        for (int i = 0; i < offers.size(); i++) output.println((i + 1) + ". " + offers.get(i).name());
        output.println("ADD — link an offer to this receipt\nUPDATE — replace this receipt's eligibility for an offer\nDELETE — remove an eligibility link from this receipt\nBACK — return to review");
        String action=required("ADD / UPDATE / DELETE / BACK, or online promotion number (0 cancels)").trim().toUpperCase(Locale.ROOT);
        if(action.equals("BACK") || action.equals("0"))return;
        if(action.equals("DELETE")) {
            if(draft.promotionLinks.isEmpty()){output.println("No promotion links to delete.");return;}
            for(int i=0;i<draft.promotionLinks.size();i++) {
                var link=draft.promotionLinks.get(i);
                output.println((i+1)+". "+offers.stream().filter(o->o.id().equals(link.promotionId())).map(GiftCardPromotion::name).findFirst().orElse("Online promotion"));
            }
            int removed=integer("Promotion link number to delete (0 cancels)",0,draft.promotionLinks.size())-1;
            if(removed>=0 && yes("Remove this receipt's eligibility and spend contribution from the promotion on SAVE?",false)) {
                draft.promotionLinks.remove(removed);output.println("Promotion link deleted from draft. SAVE applies the change.");
            }
            return;
        }
        int selected;
        if(action.equals("ADD") || action.equals("UPDATE"))selected=integer("Online promotion number (0 cancels)",0,offers.size())-1;
        else {try{selected=Integer.parseInt(action)-1;}catch(NumberFormatException invalid){output.println("Choose ADD, UPDATE, DELETE, BACK, or an offer number.");return;}}
        if(selected<0)return;
        if(selected>=offers.size()){output.println("Choose a listed offer.");return;}
        var offer = offers.get(selected);
        output.println("Choose only eligible products; linking replaces this receipt's previous selection for this offer.");
        var ids = earningItems(draft);
        if (ids == null || ids.isEmpty()) { output.println("Choose product numbers or A for eligible products; no link changed."); return; }
        var reviewed = offer.basis() == GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT
                ? amount("Reviewed eligible spend under ONLINE offer rules (not the in-store RR rule)", false, false) : null;
        String note = required("Online eligibility note (for example: Charmin qualifies; both H&S products excluded)");
        var link = new ReceiptPromotionLink(offer.id(), ids, reviewed, note, Instant.now());
        draft.promotionLinks.removeIf(previous -> previous.promotionId().equals(offer.id()));
        draft.promotionLinks.add(link);
        output.println("Online eligibility noted in this draft; receipt and progress commit together on SAVE.");
    }

    /** Each shared application has one total deduction and a stable identity across corrections. */
    private void editSharedCoupons(Draft draft) {
        draftQuestions(draft, () -> editSharedCouponsFields(draft));
    }

    private void editSharedCouponsFields(Draft draft) {
        if (draft.items.size() < 2) {
            output.println("Shared coupons require at least two product lines. Use item coupons for one line, including multiple units.");
            return;
        }
        output.println("SHARED MANUFACTURER COUPONS — enter each deduction once; do not also enter it on individual items.");
        int selected = -1;
        if (draft.sharedCoupons.isEmpty()) {
            output.println("No shared manufacturer coupons entered.");
            if (!yes("Add a shared manufacturer coupon?", false)) return;
        } else {
            String action = menuText("Shared coupons: ADD / CORRECT / DELETE / BACK").trim().toUpperCase(Locale.ROOT);
            if (action.equals("BACK")) return;
            if(action.equals("DELETE")) {
                for(int i=0;i<draft.sharedCoupons.size();i++) {
                    var coupon=draft.sharedCoupons.get(i);
                    output.println((i+1)+". "+coupon.getPrintedLabel()+" | "+dollars(coupon.getAppliedAmount()));
                }
                int index=integer("Coupon number to delete (0 cancels)",0,draft.sharedCoupons.size())-1;
                if(index<0)return;
                var coupon=draft.sharedCoupons.get(index);
                if(yes("Delete "+coupon.getPrintedLabel()+" ("+dollars(coupon.getAppliedAmount())+") from this receipt draft?",false)) {
                    draft.sharedCoupons.remove(index);draft.sharedKinds.remove(coupon.getApplicationId());
                    clearSavings(draft);invalidateDraftPromotionAmounts(draft);
                    output.println("Coupon deleted from draft. SAVE applies the change to the receipt.");
                } else output.println("Coupon retained.");
                return;
            }
            if (action.equals("CORRECT")) {
                for (int i = 0; i < draft.sharedCoupons.size(); i++) {
                    var coupon = draft.sharedCoupons.get(i);
                    output.println((i + 1) + ". " + coupon.getPrintedLabel() + " | " + coupon.getAppliedAmount());
                }
                selected = integer("Coupon number to correct", 1, draft.sharedCoupons.size()) - 1;
            } else if (!action.equals("ADD")) {
                output.println("Choose ADD, CORRECT, DELETE, or BACK.");
                return;
            }
        }
        output.println("Select the products covered by this coupon:");
        showItems(draft);
        List<UUID> ids;
        while (true) {
            String answer = text("Applies to item numbers separated by commas (BACK previous; CANCEL cancels)").trim();
            if (answer.equalsIgnoreCase("BACK")) return;
            try {
                List<UUID> chosen = new ArrayList<>();
                for (String part : answer.split(",", -1)) {
                    int index = Integer.parseInt(part.trim()) - 1;
                    if (index < 0 || index >= draft.items.size()) throw new IllegalArgumentException();
                    UUID id = draft.items.get(index).item().getLineItemId();
                    if (chosen.contains(id)) throw new IllegalArgumentException();
                    chosen.add(id);
                }
                if (chosen.size() < 2) throw new IllegalArgumentException();
                ids = List.copyOf(chosen);
                break;
            } catch (IllegalArgumentException invalid) {
                output.println("Choose at least two different existing item numbers; no duplicates.");
            }
        }
        String label = required("Coupon description");
        BigDecimal value = amount("Total coupon deduction (entered once)", false, true);
        Integer required = optionalQuantity("Products required (blank unknown)");
        UUID id = selected < 0 ? UUID.randomUUID() : draft.sharedCoupons.get(selected).getApplicationId();
        var coupon = new CouponApplied(id, label, value, ids, required);
        if (selected < 0) draft.sharedCoupons.add(coupon);
        else draft.sharedCoupons.set(selected, coupon);
        draft.sharedKinds.put(coupon.getApplicationId(),DiscountKind.MANUFACTURER);
    }

    /** Separates checkout deductions from rewards earned and printed savings. */
    private void checkout(Draft draft) {
        while (true) {
            BigDecimal tax = checkoutTax(draft);
            try { draftQuestions(draft, () -> checkoutFields(draft, tax)); return; }
            catch (Navigate navigation) {
                if (!navigation.back) throw navigation;
                output.println("Returned to tax; checkout answers are not committed.");
            }
        }
    }

    private void checkoutFields(Draft draft, BigDecimal tax) {
        draft.tax = tax;
        draft.unknownRR.clear();
        draft.knownRR.clear();
        int rewardCount;
        if(draft.purchaseChannel==ReceiptMeta.PurchaseChannel.ONLINE) {rewardCount=0;output.println("Online order: Register Rewards cannot be used; RR entry skipped.");}
        else rewardCount=integer("How many separate Register Reward coupons did you use? (0 if none)",0,1000);
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
            output.println("1. Cash\n2. Card\n3. Gift card payment\n4. Other with notes\n5. Not recorded\n0. No payment needed ($0 due)");
            String selection = text("Payment method").trim();
            // Accept the old skip response without adding an extra yes/no question.
            if (selection.equalsIgnoreCase("n") || selection.isEmpty() || selection.equals("5") || selection.equals("0")) break;
            if (selection.equalsIgnoreCase("y")) continue;
            int selected;
            try { selected = Integer.parseInt(selection); }
            catch (NumberFormatException exception) { output.println("Choose 0 through 5."); continue; }
            if (selected < 1 || selected > 4) { output.println("Choose 0 through 5."); continue; }
            PaymentType type = switch (selected) {
                case 1 -> PaymentType.CASH;
                case 2 -> PaymentType.CARD;
                case 3 -> PaymentType.GIFT_CARD;
                default -> PaymentType.OTHER;
            };
            BigDecimal paid = amount("Payment amount (0 if no payment was needed)", false, false);
            if (paid.signum() == 0) {
                output.println("No payment added. Enter the printed total next; a $0.00 total needs no payment record.");
                break;
            }
            String notes = selected == 4 ? optional("Other payment notes (blank unknown)") : null;
            String accountName = selected == 3 || selected == 4
                    ? optional("Redeemed account name (e.g. Target Gift Card or Target Circle; blank unassigned)") : null;
            draft.payments.add(new Payment(UUID.randomUUID(), type, paid, notes, accountName));
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
        return questions(() -> addEarningFields(draft, question));
    }
    private boolean addEarningFields(Draft draft, String question) {
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
                    String products = draft.deferredEarnings.contains(e.earningId()) ? "Products to be selected after item entry"
                            : e.scope() == Scope.RECEIPT ? "Whole receipt" : e.scope() == Scope.UNKNOWN ? "Products unknown"
                            : RegisterRewardLabel.products(draft.transaction(), e);
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
        output.println("\nADD OR EDIT REBATES / REWARDS");
        output.println("0. Add a new rebate or reward");
        for (int i = 0; i < draft.earnings.size(); i++) {
            Earning e = draft.earnings.get(i);
            output.println((i + 1) + ". Edit existing: " + e.provider()+" | "+Objects.toString(e.offerReference(),"No offer description"));
        }
        record EarningAction(int index, boolean remove) { }
        var action = questions(() -> {
            int index = integer("Choose 0 to add, or an existing earning number to edit (BACK returns)", 0, draft.earnings.size()) - 1;
            return new EarningAction(index, index >= 0 && yes("Remove this earning?", false));
        });
        int selected = action.index();
        if (action.remove()) { draft.earnings.remove(selected); return; }
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

    /** Captures item-time benefits with explicit current, deferred, receipt, or unknown scope. */
    private void itemEarnings(Draft draft, Item item) {
        while (addEarning(draft, "Add rebate for current item")) {
            Earning earning = readEarning(draft, item.item().getLineItemId());
            if (earning != null) {draft.earnings.add(earning);checkpointCompletedDraft(draft);}
        }
    }

    /** Resolves deferred associations once every purchased product can be selected. */
    private void resolveDeferredEarnings(Draft draft) {
        for (int i = 0; i < draft.earnings.size(); i++) {
            Earning old = draft.earnings.get(i);
            if (!draft.deferredEarnings.contains(old.earningId())) continue;
            output.println("SELECT PRODUCTS FOR EARNING: " + old.provider() + " | " + old.offerReference());
            output.println("Choose at least two product lines, or explicitly choose 0 for whole receipt / U for unknown.");
            List<UUID> lines;
            do {
                lines = earningItems(draft);
                if (lines != null && lines.size() == 1) output.println("Select at least two different product lines.");
            } while (lines != null && lines.size() == 1);
            Scope scope = lines == null ? Scope.UNKNOWN : lines.isEmpty() ? Scope.RECEIPT : Scope.MULTI_ITEM;
            draft.earnings.set(i, new Earning(old.earningId(), old.kind(), old.provider(), old.offerReference(),
                    scope, lines == null ? List.of() : lines, old.expected(), old.confirmed(), old.status(),
                    old.expirationDate(), old.instrumentId(), old.claimId(), old.evidenceIds(),
                    old.recordedLifecycle(), old.requirement(), old.valuation()));
            draft.deferredEarnings.remove(old.earningId());
        }
    }

    /** Appends receipt-wide or additional benefits; never silently clears item earnings. */
    private void earnings(Draft draft) {
        output.println("Earnings do not reduce checkout. Transfers and point conversions are not new earnings.");
        while (addEarning(draft, "Add a receipt-wide or another reward / rebate?")) {
            Earning earning = readEarning(draft, null);
            if (earning != null) {draft.earnings.add(earning);checkpointCompletedDraft(draft);}
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
            while (step < 15) {
                boolean asked = true;
                try {
                    switch (step) {
                        case 0 -> {
                            a.selection = earningKind(draft.retailer);
                            if(draft.purchaseChannel==ReceiptMeta.PurchaseChannel.ONLINE && a.selection.kind()==EarningKind.REGISTER_REWARD) {
                                output.println("Online orders cannot earn Register Rewards. Choose another reward program.");continue;
                            }
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
                        case 3 -> a.offer = optional("Offer / notes (include spend basis, e.g. P&G spend $35 at sale prices before coupons, earn $10 RR; blank unknown)");
                        case 4 -> {
                            output.println("1. Buy a quantity\n2. Spend an amount\n3. Receipt\n4. Other");
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
                            if (a.requirementType == PromotionType.SPEND) {
                                output.println("1. Sale/promotion prices before coupons");
                                output.println("2. Other / unknown basis (cannot automatically verify)");
                                a.spendBasis = integer("Qualifying spend basis", 1, 2) == 1
                                        ? ReceiptObservations.SpendBasis.SALE_BEFORE_COUPONS : ReceiptObservations.SpendBasis.UNKNOWN;
                            } else { a.spendBasis = ReceiptObservations.SpendBasis.UNKNOWN; asked = false; }
                        }
                        case 7 -> {
                            if (itemId != null) {
                                output.println("Applies to:");
                                output.println("1. Current item");
                                output.println("2. Select multiple products after all items are entered");
                                output.println("3. Whole receipt");
                                output.println("4. Unknown");
                                int selected = integer("Applies to", 1, 4);
                                a.deferredProducts = selected == 2;
                                a.lines = selected == 1 ? List.of(itemId) : List.of();
                                a.scope = selected == 1 ? Scope.ITEM : selected == 3 ? Scope.RECEIPT : Scope.UNKNOWN;
                            } else {
                                a.lines = earningItems(draft);
                                a.scope = a.lines == null ? Scope.UNKNOWN : a.lines.isEmpty() ? Scope.RECEIPT
                                        : a.lines.size() == 1 ? Scope.ITEM : Scope.MULTI_ITEM;
                                if (a.lines == null) a.lines = List.of();
                            }
                        }
                        case 8 -> a.entered = amount(a.selection.provider() == null ? "Reward / rebate amount"
                                : a.selection.provider() + (a.selection.form() == BenefitKind.POINTS
                                    ? " points earned / expected" : " amount earned / expected (USD)"), false, true);
                        case 9 -> {
                            if(a.selection.kind()==EarningKind.REGISTER_REWARD)
                                output.println("REGISTER REWARD STATUS — choose 5 if the coupon printed.");
                            a.status=earningStatus(a.selection.kind(),a.selection.form());
                        }
                        case 10 -> {
                            if (a.status == RebateStatus.APPROVED || a.status == RebateStatus.CREDITED || a.status == RebateStatus.PAID) {
                                a.confirmed = a.entered;
                                a.expected = amount("Earlier expected amount (blank if not separately recorded)", true, true);
                            } else { a.expected = a.entered; a.confirmed = null; asked = false; }
                        }
                        case 11 -> {
                            a.reusedValuation = false;
                            if (a.selection.form() == BenefitKind.POINTS) {
                                var saved = savedPointValuation(a.provider, a.entered);
                                if (saved.isPresent()) {
                                    var value = saved.orElseThrow();
                                    output.println("Saved value for " + a.entered.toPlainString() + " " + a.provider + " points: " + value.amount().toPlainString() + " USD");
                                    a.valuation = amount("USD value (Enter keeps saved value)", true, false);
                                    if (a.valuation == null) { a.valuation = value.amount(); a.basis = value.basis(); a.reusedValuation = true; }
                                } else a.valuation = amount("USD value of these points (required; saved with this earning)", false, false);
                            } else if (a.selection.form() == BenefitKind.GIFT_CARD) {
                                a.valuation = amount("Recorded USD value of THIS benefit (blank unknown; not another earning)", true, false);
                            } else { a.valuation = null; asked = false; }
                        }
                        case 12 -> {
                            if (a.reusedValuation) asked = false;
                            else if (a.valuation != null) a.basis = required("Basis for that value (for example: my spreadsheet)");
                            else { a.basis = null; asked = false; }
                        }
                        case 13 -> a.expiration = date("Expiration date (M/D/YYYY or YYYY-MM-DD, blank unknown)");
                        case 14 -> a.lifecycle = optional("Optional note (for example: expired; blank none)");
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
            Earning earning = a.build();
            if (a.deferredProducts) draft.deferredEarnings.add(earning.earningId());
            return earning;
        } catch (EarningCancelled cancelled) {
            output.println("Earning cancelled; other entries retained."); return null;
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
            String answer = text("Reward / rebate program (number/name; provider / CASH, POINTS or GIFT_CARD overrides this offer's award; redemption is separate)").trim();
            if(answer.contains("/")) {
                var parts=answer.split("/",2); var named=choices.stream().filter(c->c.provider().equalsIgnoreCase(parts[0].trim())).findFirst();
                if(named.isPresent() && named.orElseThrow().kind()==EarningKind.THIRD_PARTY_REBATE) {
                    try { var form=BenefitKind.valueOf(parts[1].trim().toUpperCase(Locale.ROOT).replace(' ','_')); return new EarningSelection(EarningKind.THIRD_PARTY_REBATE,form,false,named.orElseThrow().provider()); }
                    catch(IllegalArgumentException invalid) { output.println("Choose CASH, POINTS, GIFT_CARD, STORE_COUPON or STORE_REWARD.");continue; }
                }
                output.println("Choose a named rebate provider; RR and Walgreens Cash retain their native reward form.");continue;
            }
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
        output.println("A. All listed products\n0. Whole receipt\nU. Unknown products");
        while (true) {
            String answer = text("Applies to (item numbers separated by commas, A for all products, 0, or U; BACK previous; CANCEL cancels)").trim();
            if (answer.equalsIgnoreCase("A")) return draft.items.stream().map(item -> item.item().getLineItemId()).toList();
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
        draftQuestions(draft, () -> accountFields(draft));
    }

    private void accountFields(Draft draft) {
        if (isWalgreens(draft)) {
            if(draft.footerStatus == FooterStatus.NOT_PRINTED) {
                if(!yes("Is the Walgreens savings/rewards footer printed?", true)) return;
                draft.footerStatus = FooterStatus.PRINTED;
            }
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
        draftQuestions(draft, () -> savingsFields(draft));
    }

    private void savingsFields(Draft draft) {
        if (!isWalgreens(draft)) { detailedSavingsFields(draft); return; }
        if (!yes("Is the Walgreens savings/rewards footer printed?", true)) {
            draft.footerStatus = FooterStatus.NOT_PRINTED;
            draft.savings.removeIf(s -> List.of("myWalgreens Savings", "Mfg coupon savings", "Your total Savings").contains(s.printedLabel()));
            draft.savingPercent = null;
            draft.account = null;
            output.println("Walgreens savings/rewards footer: Not printed. Entered rewards and payments are retained.");
            return;
        }
        draft.footerStatus = FooterStatus.PRINTED;
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
        draftQuestions(draft, () -> detailedSavingsFields(draft));
    }

    private void detailedSavingsFields(Draft draft) {
        draft.savings.clear();
        while (yes("Add a printed savings category or total?", false)) {
            output.println("PRINTED RECEIPT SAVINGS — all amounts are USD");
            output.println("1. myWalgreens Savings\n2. Manufacturer coupon savings");
            output.println("3. Your total Savings\n4. Other printed savings\n5. Continue to review");
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
    private void evidence(Draft draft) {
        try { draftQuestions(draft, () -> evidenceFields(draft)); }
        catch (Navigate navigation) { output.println("Returned from attachments; receipt entries retained."); }
    }

    private void evidenceFields(Draft draft) {
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
        return questions(this::selectReceiptFields);
    }
    private Optional<ReceiptRecord> selectReceiptFields() {
        if (savedIds.isEmpty()) {
            output.println("No receipts saved this session.");
            return Optional.empty();
        }
        for (int i = 0; i < savedIds.size(); i++) {
            var receipt = repository.findById(savedIds.get(i)).orElseThrow();
            var observed=receipt.transaction().getObservations();
            output.println("#" + (i + 1) + " | " + Objects.toString(observed.purchaseDate(),"Date not recorded")
                    + " | " + (observed.printedTime()==null || observed.printedTime().isBlank()?"Time not recorded":ReceiptTimeInput.display(observed.printedTime()))
                    + " | " + (receipt.metadata()==null?"Unknown":ReceiptReport.title(receipt.metadata().retailer()))
                    + " | " + receiptNumber(receipt));
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
                .filter(receipt -> receipt.metadata() != null && selection.equals(receipt.metadata().receiptNumber())).toList();
        if (matches.size() != 1) {
            output.println("Found " + matches.size() + " matches; use a #session number to select uniquely.");
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    /** Clearly separates viewing receipt earnings from recording a separate physical coupon. */
    private void enterReward() {
        while (true) {
            output.println("\nREGISTER REWARD COUPONS\n");
            output.println("1. View RR summary and coupons\n2. Add an existing RR coupon\n3. Delete a misentered separate coupon\n4. Match RR used to coupon\n5. Back to main menu\n");
            int action;
            try { action = questions(() -> integer("RR Coupon Action", 1, 5)); }
            catch (Navigate navigation) { return; }
            if (action == 5) return;
            if (action == 1) { viewRecordedRewards(); continue; }
            try {
                if (action == 3) { deleteRecordedReward(); continue; }
                if (action == 4) { matchRRUsage(); continue; }
                var reward = questions(() -> {
                    output.println("Adds a separate physical RR coupon you already have; it does not change receipt earnings or spend a reward.");
                    output.println("If you entered this reward while recording a receipt, view it with option 1 instead.");
                    String source = optional("Coupon Source (e.g. Neutrogena; Enter or BACK cancels)");
                    if (source == null) return null;
                    RegisterReward proposed = new RegisterReward(source, amount("Register Reward Amount", false, true),
                            date("Earned Date (blank unknown)"), date("Expiration Date (blank unknown)"), null);
                    output.println("Coupon: " + ReceiptReport.title(source) + " — " + proposed.getFaceValue().setScale(2, java.math.RoundingMode.HALF_UP) + " RR");
                    return yes("Save This Separate RR Coupon?", false) ? proposed : null;
                });
                if (reward != null) { repository.issuedRewards().save(reward); output.println("RR Coupon Recorded. Saved Receipts Were Not Changed."); }
                else output.println("Cancelled — No RR Coupon Added.");
            } catch (Navigate navigation) { output.println("Cancelled — No RR Coupon Added."); }
        }
    }

    private static ReceiptObservations retainWorksheet(ReceiptObservations o,List<UUID> payments,List<UUID> knownRR) {
        var earnings=o.earnings().stream().map(Earning::earningId).collect(java.util.stream.Collectors.toSet());
        var events=new HashSet<>(payments);events.addAll(knownRR);o.unidentifiedRR().forEach(r->events.add(r.eventId()));
        var groups=new HashMap<>(o.worksheet().earningGroups());groups.keySet().retainAll(earnings);
        var uses=new HashMap<>(o.worksheet().uses());uses.keySet().retainAll(events);
        return new ReceiptObservations(o.purchaseDate(),o.printedTime(),o.earnings(),o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds(),o.receiptSavingValuePercent(),o.promotionLinks(),new ReceiptWorksheetData(groups,uses,o.worksheet().dayOrder()),o.footerStatus());
    }

    private ReceiptWorksheetData worksheetMetadata(CouponTransaction tx,ReceiptWorksheetData current,String action) {
        var groups=new HashMap<>(current.earningGroups()); var uses=new HashMap<>(current.uses());
        if(action.equals("ORDER")) return new ReceiptWorksheetData(groups,uses,integer("Transaction sequence within this purchase day",1,Integer.MAX_VALUE));
        if(action.equals("GROUPS")) {
            var earnings=tx.getObservations().earnings();
            for(int i=0;i<earnings.size();i++) output.println((i+1)+". "+earnings.get(i).provider()+" | currently "+current.group(earnings.get(i)));
            int index=integer("Earning number (0 returns)",0,earnings.size())-1;
            if(index>=0) { var selected=choice("Reporting group only; this does not redeem points",ReceiptWorksheetData.Group.class); groups.put(earnings.get(index).earningId(),selected); }
        } else if(action.equals("USES")) {
            record UseEvent(UUID id,String label,boolean points,boolean other) { }
            var events=new ArrayList<UseEvent>();
            for(var p:tx.getPayments()) if(p.getPaymentType()==PaymentType.WALGREENS_CASH||p.getPaymentType()==PaymentType.GIFT_CARD||p.getAccountName()!=null) events.add(new UseEvent(p.getPaymentId(),Objects.toString(p.getAccountName(),p.getPaymentType().name())+" | "+p.getAmount(),p.getAccountName()!=null,p.getPaymentType()==PaymentType.OTHER&&p.getAccountName()!=null));
            tx.getObservations().unidentifiedRR().forEach(r->events.add(new UseEvent(r.eventId(),"RR | "+r.amount(),false,false)));
            tx.getRegisterRewardRedemptions().forEach(r->events.add(new UseEvent(r.getRedemptionId(),"RR | "+r.getRedeemedAmount(),false,false)));
            for(int i=0;i<events.size();i++) output.println((i+1)+". "+events.get(i).label());
            int index=integer("Reward use to associate (0 returns)",0,events.size())-1;
            if(index>=0) {
                var event=events.get(index); Draft draft=new Draft();tx.getPurchaseItems().forEach(i->draft.items.add(new Item(i,List.of(),Map.of())));
                var ids=earningItems(draft); if(ids==null) ids=List.of();
                BigDecimal units=event.points()?amount("Original points consumed (blank if not points)",true,true):null;
                boolean deduction=event.other()&&yes("Was this deducted before the printed checkout total? No means a payment funding the total",false);
                uses.put(event.id(),new ReceiptWorksheetData.Use(ids,units,deduction));
            }
        } else output.println("Choose GROUPS, USES or ORDER.");
        return new ReceiptWorksheetData(groups,uses,current.dayOrder());
    }

    private void receiptViews(ReceiptRecord receipt) {
        ReceiptWorksheetReport.compact(receipt,output);ReceiptIssues.summary(receipt,output);
        while(true) {
            String action=menuText("ITEMIZED / TOTALS / ACCOUNTS / FULL / EDIT RECEIPT / ISSUES / BACK").trim().toUpperCase(Locale.ROOT);
            switch(action) {
                case "BACK", "CANCEL" -> { return; }
                case "ITEMIZED" -> ReceiptWorksheetReport.itemized(receipt,output);
                case "TOTALS" -> ReceiptWorksheetReport.compact(receipt,output);
                case "ACCOUNTS" -> balances.printTransaction(receipt,savedIds.stream().map(id->repository.findById(id).orElseThrow()).toList(),output);
                case "FULL" -> display(receipt);
                case "ISSUES" -> ReceiptIssues.print(receipt,output);
                case "EDIT RECEIPT", "EDIT", "UPDATE" -> {
                    boolean previouslyInProgress=receiptInProgress;
                    receiptInProgress=true;
                    try { correctReceipt(receipt); }
                    catch(Navigate navigation) { output.println("Returned to receipt view."); }
                    catch(IOException exception) { output.println("Correction could not be saved. Your saved receipt is unchanged."); }
                    finally { receiptInProgress=previouslyInProgress; }
                    receipt=repository.findById(receipt.transactionId()).orElse(receipt);
                    ReceiptWorksheetReport.compact(receipt,output);
                }
                default -> { queuedMainAction=action; return; }
            }
        }
    }

    private void viewBalances() {
        var records = savedIds.stream().map(id -> repository.findById(id).orElseThrow()).toList();
        var day = records.stream().map(r -> r.transaction().getObservations().purchaseDate()).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(LocalDate.now(java.time.ZoneId.of("America/Chicago")));
        printBalanceOverview(day,records);
        while (true) {
            String action=menuText("BALANCES / SAVINGS / SETTINGS / REPORTED / BACK").trim().toUpperCase(Locale.ROOT);
            if(action.equals("BACK") || action.equals("CANCEL")) return;
            if(action.equals("BALANCES")) printBalanceOverview(day,records);
            else if(action.equals("SETTINGS")) balanceSettings();
            else if(action.equals("SAVINGS")) savingsReports();
            else if(action.equals("REPORTED")) {
                var all = repository.creationHistory().stream().map(h -> h.after().transactionId())
                        .map(id -> repository.findById(id).or(() -> repository.findDeletedById(id)).orElseThrow()).toList();
                balances.printReported(all, output);
            }
            else output.println("Choose BALANCES, SAVINGS, SETTINGS, REPORTED or BACK.");
        }
    }
    private void printBalanceOverview(LocalDate day,List<ReceiptRecord> records) {
        balances.print(day, records, output);
        balances.printPeriods(day,records,output);
        SavingsReport.printCompact(ReceiptWorksheetReport.weekLabel(day)+" — "+ReceiptWorksheetReport.weekStart(day)+" through "+ReceiptWorksheetReport.weekEnd(day),ReceiptWorksheetReport.week(records,day),output);
        SavingsReport.printCompact("Month to date through "+day,SavingsReport.month(records,java.time.YearMonth.from(day)).stream().filter(r->!r.transaction().getObservations().purchaseDate().isAfter(day)).toList(),output);
    }
    private void savingsReports() {
        List<ReceiptRecord> selected = null;
        String scope = null;
        while(true) {
            String action=menuText("Savings: TRANSACTION / DAY / WEEK / MONTH / DETAILS / BACK").trim().toUpperCase(Locale.ROOT);
            if(action.equals("BACK") || action.equals("CANCEL")) return;
            try {
                var records=savedIds.stream().map(id->repository.findById(id).orElseThrow()).toList();
                long undated=records.stream().filter(r->r.transaction().getObservations().purchaseDate()==null).count();
                switch(action) {
                    case "TRANSACTION" -> {
                        var receipt=selectReceipt();if(receipt.isEmpty())continue;
                        selected=List.of(receipt.orElseThrow());scope="Transaction "+receiptNumber(receipt.orElseThrow());
                    }
                    case "DAY" -> {
                        var day=questions(()->date("Savings date (M/D/YYYY or YYYY-MM-DD)"));
                        if(day==null) { output.println("A date is required for daily savings."); continue; }
                        selected=SavingsReport.day(records,day);scope="Day "+day;
                    }
                    case "WEEK" -> {
                        var day=questions(()->date("Any date in the sales week (Sunday–Saturday)"));if(day==null)continue;
                        var start=ReceiptWorksheetReport.weekStart(day);
                        selected=ReceiptWorksheetReport.week(records,day);scope=ReceiptWorksheetReport.weekLabel(day)+" — "+start+" through "+ReceiptWorksheetReport.weekEnd(day);
                    }
                    case "MONTH" -> {
                        var month=questions(this::savingsMonth);selected=SavingsReport.month(records,month);scope="Month "+month;
                    }
                    case "DETAILS" -> {
                        if(selected==null)output.println("Choose a transaction, day, week or month first.");
                        else {
                            // Reload current revisions so a requested detail view never resurrects old activity.
                            var current=selected.stream().map(r->repository.findById(r.transactionId()).orElse(null)).filter(Objects::nonNull).toList();
                            SavingsReport.print(scope,current,output);ReceiptWorksheetReport.period(scope,current,output);
                        }
                        continue;
                    }
                    default -> {output.println("Choose TRANSACTION, DAY, WEEK, MONTH, DETAILS or BACK.");continue;}
                }
                SavingsReport.printCompact(scope,selected,output);
                if(!action.equals("TRANSACTION") && undated>0)output.println("Undated saved receipts excluded from dated reports: "+undated);
            } catch(Navigate navigation) {output.println("Returned to savings reports.");}
        }
    }
    private java.time.YearMonth savingsMonth() {
        while(true) {
            String raw=required("Month (YYYY-MM or M/YYYY)");
            try {
                if(raw.contains("/")) { if(!raw.matches("\\d{1,2}/\\d{4}")) throw new IllegalArgumentException(); var parts=raw.split("/",-1); if(parts.length!=2) throw new IllegalArgumentException(); return java.time.YearMonth.of(Integer.parseInt(parts[1]),Integer.parseInt(parts[0])); }
                return java.time.YearMonth.parse(raw);
            } catch(IllegalArgumentException | java.time.DateTimeException invalid) { output.println("Enter a month such as 2026-10 or 10/2026."); }
        }
    }

    private void restoreReceipt() throws IOException {
        var deleted = repository.findDeleted();
        if (deleted.isEmpty()) { output.println("No deleted receipts available."); return; }
        output.println("\nDELETED RECEIPTS — original contents and history retained");
        for (int i = 0; i < deleted.size(); i++) {
            var r = deleted.get(i); var o = r.transaction().getObservations();
            output.println((i + 1) + ". " + Objects.toString(o.purchaseDate(), "Date not recorded")
                    + " | " + ReceiptTimeInput.display(o.printedTime()) + " | " + receiptNumber(r)
                    + " | revision " + r.revision());
        }
        int selected = questions(() -> integer("Deleted receipt number (0 returns)", 0, deleted.size()));
        if (selected == 0) return;
        var receipt = deleted.get(selected - 1);
        display(receipt);
        String reason = questions(() -> required("Restoration reason"));
        if (!questions(() -> yes("Restore this receipt? Calculated balances will include its activity once; provider-reported observations remain unchanged", false))) return;
        var request = new ReceiptWriteRequest(UUID.randomUUID(), Instant.now());
        var context = new ReceiptChangeContext("Local owner", reason, "Local owner", request.requestedAt());
        while (true) {
            try {
                repository.restore(receipt.transactionId(), receipt.revision(), request, context);
                if (!savedIds.contains(receipt.transactionId())) savedIds.add(receipt.transactionId());
                var restored = repository.findById(receipt.transactionId()).orElseThrow();
                output.println("Receipt restored successfully: " + receiptNumber(restored) + " | revision " + restored.revision());
                displayHistory(restored);
                displayAccountCarry(receipt, restored);
                return;
            } catch (java.util.ConcurrentModificationException | IllegalArgumentException failure) {
                output.println("Restoration not saved: " + failure.getMessage()); return;
            } catch (IllegalStateException failure) {
                output.println("Restoration not saved: " + failure.getMessage());
                if (!questions(() -> yes("Retry the same restoration request?", false))) return;
            }
        }
    }

    private void lookupReceiptDuplicates() {
        var metadata = questions(() -> {
            String retailer = required("Retailer (exact recorded spelling)");
            String number = optional("Printed receipt number (blank unknown)");
            LocalDate day = date("Purchase date (blank unknown)");
            String time = day == null ? null : receiptTime();
            var purchased = day == null ? null : ReceiptTimeInput.parse(time).map(day::atTime).orElse(null);
            return new ReceiptMeta(retailer, number, purchased, optional("Store reference (blank unknown)"));
        });
        var matches = repository.findPossibleDuplicates(metadata);
        if (matches.isEmpty()) { output.println("No possible duplicates found."); return; }
        output.println("\nPOSSIBLE DUPLICATES — includes deleted receipts; no records changed");
        for (var r : matches) {
            var o = r.transaction().getObservations();
            output.println(Objects.toString(o.purchaseDate(), "Date not recorded") + " | "
                    + ReceiptTimeInput.display(o.printedTime()) + " | " + receiptNumber(r)
                    + " | " + r.status() + " | revision " + r.revision());
        }
    }

    private void historyActions() throws IOException {
        String action = menuText("HISTORY / RETRY / RESTORE / DUPLICATES / BACK").trim().toUpperCase(Locale.ROOT);
        if (action.equals("RESTORE")) { restoreReceipt(); return; }
        if (action.equals("DUPLICATES")) { lookupReceiptDuplicates(); return; }
        if (action.equals("RETRY")) {
            if (last == null) output.println("No captured save request.");
            else {
                boolean alreadySaved = repository.history().findByRequestId(last.request().requestId()).isPresent();
                var result = last.submit(repository);
                output.println(alreadySaved ? "Already Saved — No Duplicate Receipt Or History Entry Created." : "Save Completed.");
                output.println("Original result: " + writeSummary(result));
                output.println("Saved At: " + readableTimestamp(result.committedAt()));
            }
        } else if (action.equals("HISTORY")) {
            var records = repository.creationHistory().stream().map(h -> h.after().transactionId())
                    .map(id -> repository.findById(id).orElseGet(() -> repository.findDeletedById(id).orElseThrow()))
                    .sorted(java.util.Comparator
                            .comparing((ReceiptRecord r) -> r.transaction().getObservations().purchaseDate(),
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                            .thenComparing(r -> ReceiptTimeInput.parse(r.transaction().getObservations().printedTime()).orElse(null),
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                            .thenComparing(ReceiptRecord::transactionId))
                    .toList();
            if (records.isEmpty()) { output.println("No saved receipt history."); return; }
            output.println("# | Date | Time | Receipt | Out of pocket | Status");
            for (int i = 0; i < records.size(); i++) {
                var r=records.get(i); var o=r.transaction().getObservations();
                var paid=r.transaction().getPayments().stream().filter(p->p.getPaymentType()==PaymentType.CASH || p.getPaymentType()==PaymentType.CARD).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
                output.println((i+1)+" | "+Objects.toString(o.purchaseDate(),"Not recorded")+" | "+(o.printedTime()==null?"Not recorded":ReceiptTimeInput.display(o.printedTime()))+" | "+receiptNumber(r)+" | $"+paid.setScale(2,java.math.RoundingMode.HALF_UP)+" | "+r.status());
            }
            String selected = required("History receipt number or printed receipt number (0 returns)");
            if (selected.equals("0")) return;
            try { int index = Integer.parseInt(selected) - 1; if (index >= 0 && index < records.size()) { displayHistory(records.get(index)); return; } }
            catch (NumberFormatException ignored) { }
            var matches = records.stream().filter(r -> selected.equals(receiptNumber(r))).toList();
            if (matches.size() == 1) displayHistory(matches.get(0)); else output.println("Choose a listed receipt.");
        }
    }

    private void deleteReceipt() {
        var selected = selectReceipt(); if (selected.isEmpty()) return;
        var receipt = selected.orElseThrow(); display(receipt);
        output.println("Deleting removes this receipt's activity from current balances and promotion progress. Its history remains stored.");
        var request = new ReceiptWriteRequest(UUID.randomUUID(), Instant.now());
        while (true) {
            if (!menuText("Type DELETE to approve deletion, or BACK to return").equalsIgnoreCase("DELETE")) return;
            try {
                repository.softDelete(receipt.transactionId(), receipt.revision(), request,
                        new ReceiptChangeContext("Local owner", "Receipt deleted by local owner", "Local owner", request.requestedAt()));
                savedIds.remove(receipt.transactionId());
                output.println("Receipt deleted. Original contents and history preserved.");
                displayAccountCarry(receipt, repository.findDeletedById(receipt.transactionId()).orElseThrow());
                return;
            } catch (IllegalStateException failure) { output.println("Deletion not saved: " + failure.getMessage() + "; retry DELETE or BACK."); }
            catch (IllegalArgumentException | java.util.ConcurrentModificationException failure) { output.println("Deletion not saved: " + failure.getMessage()); return; }
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
        record Removal(UUID rewardId, String reason, String actor) { }
        var removal = questions(() -> {
            int selected = integer("Coupon Number To Delete (0 cancels)", 0, rewards.size());
            if (selected == 0) return null;
            RegisterReward reward = rewards.get(selected - 1);
            if (!yes("Delete " + ReceiptReport.title(reward.getSourceDescription()) + " — " + reward.getFaceValue()
                    + " RR From The Active List?", false)) return null;
            return new Removal(reward.getRewardId(), required("Reason (e.g. Entered By Mistake)"), required("Confirmed By"));
        });
        if (removal == null) return;
        repository.voidRecordedReward(removal.rewardId(), removal.reason(), removal.actor());
        output.println("RR Removed From Active List. Original Entry And Removal Audit Retained; Receipt Earnings Unchanged.");
    }

    private record RRCouponView(UUID id, boolean receiptEarning, UUID receiptId, String origin,
            String products, BigDecimal amount, String status, boolean printed, UUID instrumentId) { }
    private List<RRCouponView> rrCouponViews() {
        var result = new ArrayList<RRCouponView>(); var represented = new HashSet<UUID>();
        for (UUID id : savedIds) {
            var current = repository.findById(id).orElseThrow();
            for (var e : current.transaction().getObservations().earnings()) {
                if (e.kind() != EarningKind.REGISTER_REWARD) continue;
                var benefit = e.confirmed() == null ? e.expected() : e.confirmed();
                String products = RegisterRewardLabel.couponSummary(current.transaction(),e);
                result.add(new RRCouponView(e.earningId(), true, id, receiptNumber(current), products, benefit.amount(),
                        e.confirmed() == null ? "Expected" : e.status() == RebateStatus.PAID ? "Register Reward Printed" : ReceiptReport.title(e.status().name()),
                        e.confirmed() != null && e.status() == RebateStatus.PAID, e.instrumentId()));
                if (e.instrumentId() != null) represented.add(e.instrumentId());
            }
        }
        for (var coupon : repository.recordedRewards()) {
            if (represented.contains(coupon.getRewardId())) continue;
            result.add(new RRCouponView(coupon.getRewardId(), false, coupon.getEarnedReceiptId(), "Separate physical coupon",
                    ReceiptReport.title(coupon.getSourceDescription()), coupon.getFaceValue(), "Registered", true, coupon.getRewardId()));
        }
        // A later correction may remove an earning. Keep its confirmed usage visible from immutable history.
        var currentIds = result.stream().map(RRCouponView::id).collect(java.util.stream.Collectors.toSet());
        for (var link : repository.rrUsageLinks()) {
            if (!link.receiptEarning() || currentIds.contains(link.sourceId())) continue;
            RRCouponView historical = null;
            for (UUID receiptId : repository.creationHistory().stream().map(h -> h.after().transactionId()).toList()) {
                for (var entry : repository.history().findByReceiptId(receiptId)) {
                    var earning = entry.after().contents().observations().earnings().stream()
                            .filter(e -> e.earningId().equals(link.sourceId())).findFirst();
                    if (earning.isEmpty()) continue;
                    var e = earning.orElseThrow();
                    historical = new RRCouponView(e.earningId(), true, receiptId,
                            entry.after().metadata() == null ? "Unknown" : entry.after().metadata().receiptNumber(),
                            "Original earning preserved in receipt history", link.amount(), "Historical source — needs review", false, e.instrumentId());
                    break;
                }
                if (historical != null) break;
            }
            if (historical != null) result.add(historical);
        }
        result.sort(java.util.Comparator.comparing((RRCouponView c) -> rrEarnedDate(c), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(c -> rrEarnedTime(c), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return List.copyOf(result);
    }
    private LocalDate rrEarnedDate(RRCouponView coupon) {
        var receipt = coupon.receiptId() == null ? null : repository.findById(coupon.receiptId()).orElse(null);
        if (receipt != null) return receipt.transaction().getObservations().purchaseDate();
        return repository.recordedRewards().stream().filter(r -> r.getRewardId().equals(coupon.id())).map(RegisterReward::getEarnedDate).filter(Objects::nonNull).findFirst().orElse(null);
    }
    private java.time.LocalTime rrEarnedTime(RRCouponView coupon) {
        var receipt = coupon.receiptId() == null ? null : repository.findById(coupon.receiptId()).orElse(null);
        if (receipt == null || receipt.transaction().getObservations().printedTime() == null) return null;
        return ReceiptTimeInput.parse(receipt.transaction().getObservations().printedTime()).orElse(null);
    }
    private String receiptNumber(ReceiptRecord receipt) {
        return receipt == null || receipt.metadata() == null || receipt.metadata().receiptNumber() == null ? "Unknown" : receipt.metadata().receiptNumber();
    }
    private boolean rrIsUsed(RRCouponView coupon) {
        return repository.rrUsageLinks().stream().anyMatch(link -> link.sourceId().equals(coupon.id()))
                || coupon.instrumentId() != null && repository.redemptions().findByRewardId(coupon.instrumentId()).isPresent();
    }
    private void printRRCoupon(RRCouponView coupon, int number) {
        String products = coupon.products().isBlank() ? "Unknown" : coupon.products();
        boolean first = true;
        while (!products.isEmpty()) {
            int length = Math.min(44,products.length());
            if (length < products.length()) { int space = products.lastIndexOf(' ',length); if (space > 0) length = space; }
            output.printf("%3s | %-10s | %-8s | %-44s | %-34s | %8s | %s%n", first ? number : "",
                    first ? Objects.toString(rrEarnedDate(coupon),"Unknown") : "",
                    first ? Objects.toString(rrEarnedTime(coupon),"Unknown") : "", products.substring(0,length),
                    first ? coupon.origin() : "", first ? dollars(coupon.amount()) : "", first ? coupon.status() : "");
            products = products.substring(length).stripLeading(); first = false;
        }
        output.println();
    }

    private void printRRMonthlySummary() {
        var records = savedIds.stream().map(id -> repository.findById(id).orElseThrow()).toList();
        var months = new TreeSet<java.time.YearMonth>();
        records.forEach(r -> { var date = r.transaction().getObservations().purchaseDate(); if (date != null) months.add(java.time.YearMonth.from(date)); });
        output.println("\nREGISTER REWARDS — MONTHLY SUMMARY");
        output.printf("| %-12s | %14s | %14s | %14s | %18s |%n", "Month", "Start", "Earned", "Used", "Closing balance");
        output.println("+--------------+----------------+----------------+----------------+--------------------+");
        for (var month : months) {
            var entries = balances.movements(records).stream().filter(m -> m.provider().equals("Register Rewards") && m.date() != null && java.time.YearMonth.from(m.date()).equals(month)).toList();
            var monthEarned = entries.stream().filter(m -> m.usd() != null && m.usd().signum() > 0 && m.source().contains(" | earning ")).map(ProviderBalanceSummary.Movement::usd).reduce(BigDecimal.ZERO, BigDecimal::add);
            var monthUsed = records.stream().filter(r -> { var date = r.transaction().getObservations().purchaseDate(); return date != null && java.time.YearMonth.from(date).equals(month); })
                    .map(r -> new TransactionCalculator().calculateRegisterRewardsRedeemed(r.transaction())).reduce(BigDecimal.ZERO, BigDecimal::add);
            var close = balances.balance(month.atEndOfMonth(), "Register Rewards", records).endUsd();
            var opening = balances.balance(month.atDay(1), "Register Rewards", records).startUsd();
            output.printf("| %-12s | %14s | %14s | %14s | %18s |%n", month, opening == null ? "Opening needed" : dollars(opening), dollars(monthEarned), dollars(monthUsed), close == null ? "Opening needed" : dollars(close));
        }
        output.println("+--------------+----------------+----------------+----------------+--------------------+");
        var latestDay = records.stream().map(r -> r.transaction().getObservations().purchaseDate()).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(LocalDate.now());
        var available = balances.balance(latestDay, "Register Rewards", records).endUsd();
        output.println("\nAvailable balance as of " + latestDay + ": " + (available == null ? "unknown — enter opening in menu 6" : dollars(available)));
        output.println("\nRR USED BY RECEIPT");
        output.printf("%-12s %-34s %12s%n", "Date", "Receipt", "Total used");
        for (var r : records) output.printf("%-12s %-34s %12s%n", Objects.toString(r.transaction().getObservations().purchaseDate(), "Unknown"), receiptNumber(r), dollars(new TransactionCalculator().calculateRegisterRewardsRedeemed(r.transaction())));
        output.println();
    }

    private void viewRecordedRewards() {
        var coupons = rrCouponViews();
        printRRMonthlySummary();
        output.println("\nRR RECORDED WITH RECEIPTS");
        BigDecimal earned = coupons.stream().filter(c -> c.receiptEarning() && c.printed()).map(RRCouponView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = savedIds.stream().map(id -> repository.findById(id).orElseThrow())
                .map(r -> new TransactionCalculator().calculateRegisterRewardsRedeemed(r.transaction())).reduce(BigDecimal.ZERO, BigDecimal::add);
        output.println("Total RR earned from saved receipts: " + dollars(earned));
        output.println("Total RR used on saved receipts: " + dollars(used));
        output.println("\nACTIVE PRINTED RR — not linked as used; verify expiry and reconcile unmatched deductions");
        output.printf("  # | %-10s | %-8s | %-44s | %-34s | %8s | %s%n", "Earned date", "Time", "Offer / receipt lines", "Source receipt / coupon", "Amount", "Status");
        output.println("----+------------+----------+----------------------------------------------+------------------------------------+----------+------------------------");
        int count = 0; BigDecimal active = BigDecimal.ZERO;
        for (var coupon : coupons) if (coupon.receiptEarning() && coupon.printed() && !rrIsUsed(coupon)) {
            printRRCoupon(coupon, ++count); active = active.add(coupon.amount());
        }
        if (count == 0) output.println("No active printed receipt RR.");
        output.println("Receipt RR not linked as used (unreconciled): " + dollars(active));
        var expected = coupons.stream().filter(c -> !c.printed() && !rrIsUsed(c)).toList();
        if (!expected.isEmpty()) {
            output.println("\nEXPECTED / OTHER RR OBSERVATIONS");
            for (int i = 0; i < expected.size(); i++) printRRCoupon(expected.get(i), i + 1);
        }
        var separate = coupons.stream().filter(c -> !c.receiptEarning() && !rrIsUsed(c)).toList();
        if (!separate.isEmpty()) {
            output.println("\nSEPARATELY RECORDED PHYSICAL COUPONS");
            for (int i = 0; i < separate.size(); i++) printRRCoupon(separate.get(i), i + 1);
        }
        var usedCoupons = coupons.stream().filter(this::rrIsUsed).toList();
        if (!usedCoupons.isEmpty()) {
            output.println("\nUSED REGISTER REWARDS");
            output.printf("%-12s | %3s | %-34s | %8s | %-28s | %s%n", "Date used", "#", "Source receipt / coupon", "Amount", "Original status", "Used on receipt");
            output.println("-------------+-----+------------------------------------+----------+------------------------------+----------------------------------");
            int index = 0;
            for (var coupon : usedCoupons) {
                var link = repository.rrUsageLinks().stream().filter(l -> l.sourceId().equals(coupon.id())).findFirst();
                UUID spent = link.isPresent() ? link.orElseThrow().spentReceiptId()
                        : repository.redemptions().findByRewardId(coupon.instrumentId()).orElseThrow().getRedeemedReceiptId();
                boolean review = link.isPresent() && repository.rrUsageNeedsReview(link.orElseThrow());
                var spentReceipt = repository.findById(spent).orElseGet(() -> repository.findDeletedById(spent).orElse(null));
                var usedDate = spentReceipt == null ? null : spentReceipt.transaction().getObservations().purchaseDate();
                output.printf("%-12s | %3d | %-34s | %8s | %-28s | %s%n", Objects.toString(usedDate, "Unknown date"), ++index, coupon.origin(), dollars(coupon.amount()),
                        review ? "Needs review" : coupon.status(), receiptNumber(spentReceipt));
            }
        }
        var matched = repository.rrUsageLinks().stream().map(RRUsageLink::usageEventId).collect(java.util.stream.Collectors.toSet());
        boolean heading = false;
        for (UUID id : savedIds) {
            var receipt = repository.findById(id).orElseThrow();
            for (var rr : receipt.transaction().getObservations().unidentifiedRR()) if (!matched.contains(rr.eventId())) {
                if (!heading) { output.println("\nUNMATCHED RR USES\nReceipt                            | Amount   | Description | Notes"); heading = true; }
                output.printf("%-34s | %8s | %s | %s%n", receiptNumber(receipt), dollars(rr.amount()), rr.recordedDescription(), Objects.toString(rr.notes(), ""));
            }
        }
    }
    private void matchRRUsage() {
        output.println("\nMATCH REGISTER REWARD\nSelect the receipt where you USED the RR coupon.");
        var selected = selectReceipt(); if (selected.isEmpty()) return;
        var receipt = selected.orElseThrow();
        var matched = repository.rrUsageLinks().stream().map(RRUsageLink::usageEventId).collect(java.util.stream.Collectors.toSet());
        var uses = receipt.transaction().getObservations().unidentifiedRR().stream().filter(u -> !matched.contains(u.eventId())).toList();
        if (uses.isEmpty()) { output.println("No unmatched RR deductions on this receipt. Existing matches are already recorded."); return; }
        var match = questions(() -> {
            output.println("\nUsed on receipt: " + receiptNumber(receipt));
            output.println("Date used: " + Objects.toString(receipt.transaction().getObservations().purchaseDate(), "Unknown"));
            int index = 0;
            if (uses.size() > 1) {
                output.println("Choose the RR deduction you want to match:");
                for (int i = 0; i < uses.size(); i++) output.println((i+1) + ". " + dollars(uses.get(i).amount()) + " — " + uses.get(i).recordedDescription());
                index = integer("Deduction choice (0 returns)", 0, uses.size()) - 1;
                if (index < 0) return null;
            }
            var use = uses.get(index);
            output.println("RR used: " + dollars(use.amount()) + " — " + use.recordedDescription());
            var candidates = rrCouponViews().stream().filter(c -> c.printed() && !rrIsUsed(c)
                    && !receipt.transactionId().equals(c.receiptId()) && c.amount().compareTo(use.amount()) == 0).toList();
            if (candidates.isEmpty()) { output.println("No unused printed coupon matches this amount. If it came from a previous month, record the existing coupon first using Add an existing RR coupon."); return null; }
            output.println("\nWhich coupon did you use? Choose the source coupon, not another receipt number.");
            for (int i = 0; i < candidates.size(); i++) {
                var coupon = candidates.get(i);
                output.println((i+1) + ". " + dollars(coupon.amount()) + " — " + coupon.products());
                output.println("   Earned: " + Objects.toString(rrEarnedDate(coupon), "Unknown date")
                        + " at " + Objects.toString(rrEarnedTime(coupon), "Unknown time"));
                output.println("   Source receipt / coupon: " + coupon.origin());
            }
            output.println((candidates.size()+1) + ". None of these\n0. Back");
            int source = integer("Coupon choice", 0, candidates.size()+1) - 1;
            if (source < 0 || source == candidates.size()) return null;
            var coupon = candidates.get(source);
            output.println("\nCONFIRM MATCH");
            output.println("Earned: " + coupon.origin() + " | " + Objects.toString(rrEarnedDate(coupon), "Unknown date")
                    + " at " + Objects.toString(rrEarnedTime(coupon), "Unknown time"));
            output.println("Used:   " + receiptNumber(receipt) + " | " + Objects.toString(receipt.transaction().getObservations().purchaseDate(), "Unknown date"));
            output.println("Amount: " + dollars(use.amount()));
            if (!yes("Match this coupon to the recorded deduction?", false)) return null;
            return new RRUsageLink(coupon.id(), coupon.receiptEarning(), use.eventId(), receipt.transactionId(), use.amount(),
                    "Local owner confirmed the source coupon for this recorded RR deduction", "Local owner", Instant.now());
        });
        if (match != null) {
            repository.linkRRUsage(match);
            output.println("RR marked USED and removed from active list. Original earning, receipt deduction, and confirmation retained; no extra balance deduction.");
        } else output.println("No match saved.");
    }

    private void confirmReceiptSaved(ReceiptRecord receipt) {
        output.println("\n============================================================");
        output.println("RECEIPT SAVED SUCCESSFULLY");
        output.println("Receipt  : " + receiptNumber(receipt));
        output.println("Revision : " + receipt.revision());
        long pending=receipt.transaction().getObservations().promotionLinks().stream().filter(link->link.reviewedEligibleAmount()==null
                && repository.giftCardPromotions().stream().anyMatch(offer->offer.id().equals(link.promotionId()) && offer.basis()==GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT)).count();
        if(pending>0)output.println("Online rebate links awaiting amount review: "+pending+". Receipt saved; unresolved links do not count toward confirmed progress.");
        output.println(repository.storagePath().isPresent()?"Status   : Saved on this device":"Status   : Saved for this session only");
        output.println("============================================================\n");
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

    private void displayAccountCarry(ReceiptRecord before, ReceiptRecord after) {
        try {
            var records = savedIds.stream().map(id -> repository.findById(id).orElseThrow()).toList();
            balances.printAfterWrite(before, after, records, output);
        } catch (RuntimeException failure) {
            output.println("Receipt is committed. Balance display unavailable: " + failure.getMessage() + ". View menu 6.");
        }
    }

    /** Delegates presentation so review and retrieval use the same calculations. */
    private void display(ReceiptRecord receipt) {
        ReceiptReport.print(receipt, output);

    }

    /** Cross-receipt offer progress is separate from gift cards received or spent. */
    private void giftCardPromotions() {
        while (true) {
            var offers = repository.giftCardPromotions();
            output.println("\nGIFT-CARD PROMOTIONS\nQualifying purchases accumulate toward a future gift card.\n");
            for (int i = 0; i < offers.size(); i++) {
                var offer = offers.get(i);
                output.println((i + 1) + ". " + offer.name() + " | " + offer.status());
                printPromotionProgress(offer);
                output.println();
            }
            String action = menuText("NEW / VIEW / LINK / REVIEW / STATUS / BACK").trim().toUpperCase(Locale.ROOT);
            if (action.equals("BACK") || action.equals("CANCEL")) return;
            try {
                if (action.equals("NEW")) {
                    var offer = questions(this::collectPromotion);
                    if (offer != null) repository.saveGiftCardPromotion(offer);
                    continue;
                }
                if (!List.of("VIEW", "LINK", "REVIEW", "STATUS").contains(action)) { output.println("Choose a displayed action."); continue; }
                if (offers.isEmpty()) { output.println("Create a promotion with NEW first."); continue; }
                var offer = offers.get(questions(() -> integer("Promotion number (BACK returns)", 1, offers.size())) - 1);
                if (action.equals("VIEW")) { printGiftCardPromotion(offer); continue; }
                if (action.equals("STATUS")) {
                    var status = questions(() -> {
                        var selected = choice("Recorded promotion status (does not create a gift-card balance)", GiftCardPromotion.Status.class);
                        return yes("Confirm status change?", false) ? selected : null;
                    });
                    if (status != null) repository.saveGiftCardPromotion(offer.withStatus(status));
                    continue;
                }
                var selected = selectReceipt();
                if (selected.isEmpty()) continue;
                var receipt = selected.orElseThrow();
                output.println("Linking receipt revision " + receipt.revision() + "; select only products eligible under this offer.");
                Draft draft = new Draft();
                receipt.transaction().getPurchaseItems().forEach(item -> draft.items.add(new Item(item, List.of(), Map.of())));
                var replacement = questions(() -> {
                    List<UUID> ids = earningItems(draft);
                    if (ids == null || ids.isEmpty()) {
                        output.println("No link saved. Choose product numbers or A for all eligible products."); return null;
                    }
                    BigDecimal reviewed = offer.basis() == GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT
                            ? amount("Eligible spend for selected products under the offer rules (USD)", false, false) : null;
                    String note = required("Eligibility note / source (why these products qualify)");
                    var link = new GiftCardPromotion.Contribution(receipt.transactionId(), receipt.revision(), ids, reviewed, note);
                    var proposed = offer.withContribution(link);
                    printGiftCardPromotion(proposed);
                    return yes("Save this receipt link? Replaces any previous contribution from this receipt", false) ? proposed : null;
                });
                if (replacement != null) repository.saveGiftCardPromotion(replacement);
            } catch (Navigate navigation) { output.println("Returned to promotion menu; no pending changes saved."); }
        }
    }

    private GiftCardPromotion collectPromotion() {
        String name = required("Promotion name");
        BigDecimal threshold = amount("Required eligible spend (USD)", false, true);
        BigDecimal value = amount("Gift-card reward value (USD)", false, true);
        LocalDate start = requiredOfferDate("First qualifying purchase date");
        LocalDate end = requiredOfferDate("Last qualifying purchase date");
        output.println("1. Sale/promotion prices before coupons (recalculated from selected products)");
        output.println("2. Reviewed eligible amount (use for after-coupon or other offer rules; re-review after receipt corrections)");
        var basis = integer("Qualifying spend basis", 1, 2) == 1 ? GiftCardPromotion.Basis.SALE_BEFORE_COUPONS
                : GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT;
        String terms = required("Offer terms / eligible products / notes (copy actual offer rules)");
        var offer = new GiftCardPromotion(UUID.randomUUID(), name, threshold, value, start, end, basis,
                terms, GiftCardPromotion.Status.TRACKING, Map.of());
        printGiftCardPromotion(offer);
        return yes("Save this promotion?", false) ? offer : null;
    }

    private record PromotionProgress(BigDecimal spent, BigDecimal remaining, int needsReview) { }
    private PromotionProgress promotionProgress(GiftCardPromotion offer) {
        BigDecimal spent = BigDecimal.ZERO; int needsReview = 0;
        for (var link : offer.contributions().values()) {
            var receipt = repository.findById(link.receiptId()).orElse(null);
            var amount = promotionEligibleAmount(offer, link, receipt);
            if (amount.isPresent()) spent = spent.add(amount.orElseThrow()); else needsReview++;
        }
        return new PromotionProgress(spent, offer.threshold().subtract(spent).max(BigDecimal.ZERO), needsReview);
    }
    private String dollars(BigDecimal value) { return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private void printPromotionProgress(GiftCardPromotion offer) {
        var progress = promotionProgress(offer);
        output.println((progress.needsReview() == 0 ? "Qualifying spend: " : "Known qualifying spend (partial): ")
                + dollars(progress.spent()) + " / " + dollars(offer.threshold()));
        output.println("Still needed: " + dollars(progress.remaining()) + (progress.needsReview() == 0 ? "" : " based on reviewed contributions"));
        output.println("Potential gift card: " + dollars(offer.giftCardValue()));
        if (progress.needsReview() > 0) output.println("Contributions needing review: " + progress.needsReview());
    }

    private LocalDate requiredOfferDate(String label) {
        LocalDate value;
        do { value = date(label); if (value == null) output.println("An offer date is required."); } while (value == null);
        return value;
    }

    /** Promotion confirmations become receipt observations in the same atomic correction write. */
    private ReceiptSnapshot reviewCorrectionPromotions(ReceiptRecord original, ReceiptSnapshot pending, boolean allowQuestions) {
        var tx = pending.toTransaction();
        var proposed = new ReceiptRecord(original.transactionId(), ReceiptStatus.ACTIVE, original.revision() + 1, tx, original.metadata());
        var links = new ArrayList<>(pending.observations().promotionLinks());
        for (var offer : repository.giftCardPromotions()) {
            var previous = offer.contributions().get(original.transactionId());
            if (previous == null || links.stream().anyMatch(l -> l.promotionId().equals(offer.id())
                    && !original.transaction().getObservations().promotionLinks().contains(l))) continue;
            var reason = promotionReviewReason(previous, proposed);
            var date = tx.getObservations().purchaseDate();
            if (date == null || date.isBefore(offer.start()) || date.isAfter(offer.end())) {
                output.println("\n" + offer.name() + ": purchase date is outside offer dates or missing. Contribution remains unconfirmed; use PROMOTIONS to correct eligibility.");
                continue;
            }
            if (!tx.getPurchaseItems().stream().map(PurchaseItem::getLineItemId).toList().containsAll(previous.lineIds())) {
                output.println("\n" + offer.name() + ": eligible product removed. Contribution remains unconfirmed; use PROMOTIONS to select eligible products.");
                continue;
            }
            BigDecimal amount = previous.reviewedAmount();
            output.println("\nPromotion confirmation — " + offer.name());
            if (reason != null && offer.basis() == GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT) {
                if (!allowQuestions) {
                    output.println(reason + " Use PROMOTIONS to confirm the changed eligible amount before SAVE.");
                    return null;
                }
                output.println(reason);
                output.println("Previously recorded eligible spend: " + dollars(amount));
                amount = amount("Confirm eligible spend after this correction (USD)", false, false);
            }
            output.println("Final receipt confirmation also approves this promotion contribution" + (amount == null ? "." : ": " + dollars(amount)));
            links.removeIf(l -> l.promotionId().equals(offer.id()));
            links.add(new ReceiptPromotionLink(offer.id(), previous.lineIds(), amount, previous.note(), Instant.now()));
        }
        var o = pending.observations();
        tx.setObservations(new ReceiptObservations(o.purchaseDate(), o.printedTime(), o.earnings(), o.unidentifiedRR(), o.walgreensCash(), o.savings(), o.discountKinds(), o.receiptSavingValuePercent(), links, o.worksheet(), o.footerStatus()));
        return ReceiptSnapshot.from(tx);
    }

    private Optional<BigDecimal> promotionEligibleAmount(GiftCardPromotion offer, GiftCardPromotion.Contribution link, ReceiptRecord receipt) {
        var amount = offer.eligibleAmount(link, receipt);
        if (amount.isPresent() || receipt == null || receipt.status() != ReceiptStatus.ACTIVE
                || offer.basis() != GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT) return amount;
        var date = receipt.transaction().getObservations().purchaseDate();
        if (date == null || date.isBefore(offer.start()) || date.isAfter(offer.end())) return Optional.empty();
        return promotionReviewReason(link, receipt) == null ? Optional.ofNullable(link.reviewedAmount()) : Optional.empty();
    }

    private static PurchaseLineSnap eligibilityLine(PurchaseLineSnap line) {
        return new PurchaseLineSnap(line.lineItemId(),line.productName(),line.productCode(),null,line.saleUnitPrice(),line.quantity());
    }

    private String promotionReviewReason(GiftCardPromotion.Contribution link, ReceiptRecord receipt) {
        if(link.reviewedAmount()==null)return "Eligible amount has not been reviewed. Use REVIEW to confirm it; receipt saving is allowed.";
        if (receipt == null || receipt.status() != ReceiptStatus.ACTIVE) return "Linked receipt is missing or inactive.";
        var old = repository.history().findByRevision(link.receiptId(), link.reviewedRevision());
        if (old.isEmpty()) return "Original reviewed revision is unavailable.";
        var before = old.orElseThrow().after().contents();
        var after = ReceiptSnapshot.from(receipt.transaction());
        if (!Objects.equals(before.observations().purchaseDate(), after.observations().purchaseDate())) return "Purchase date changed; check offer dates.";
        var ids = new HashSet<>(link.lineIds());
        if (!before.purchaseItems().stream().filter(i -> ids.contains(i.lineItemId())).map(ReceiptEntryApp::eligibilityLine).toList().equals(after.purchaseItems().stream().filter(i -> ids.contains(i.lineItemId())).map(ReceiptEntryApp::eligibilityLine).toList()))
            return "Selected product details, quantity or price changed, or a product was removed.";
        var oldDiscounts = before.appliedCoupons().stream().filter(c -> c.getLineItemIds().isEmpty() || !Collections.disjoint(ids, c.getLineItemIds())).toList();
        var newDiscounts = after.appliedCoupons().stream().filter(c -> c.getLineItemIds().isEmpty() || !Collections.disjoint(ids, c.getLineItemIds())).toList();
        if (!oldDiscounts.equals(newDiscounts)) return "Discount amounts or product associations changed.";
        for (var discount : oldDiscounts) {
            var kind = before.observations().discountKinds().get(discount.getApplicationId());
            var current = after.observations().discountKinds().get(discount.getApplicationId());
            if (kind != null && kind != DiscountKind.STORE && kind != DiscountKind.OTHER && kind != current)
                return "A relevant discount changed between a sale reduction and a coupon.";
        }
        return null;
    }

    private void promotionRow(String label, String text) {
        boolean first = true;
        while (!text.isEmpty()) {
            int length = Math.min(64, text.length());
            if (length < text.length()) { int space = text.lastIndexOf(' ', length); if (space > 0) length = space; }
            output.printf("| %-20s | %-64s |%n", first ? label : "", text.substring(0, length));
            text = text.substring(length).stripLeading(); first = false;
        }
    }

    private void printGiftCardPromotion(GiftCardPromotion offer) {
        output.println("\n" + offer.name() + " — " + ReceiptReport.title(offer.status().name().toLowerCase(Locale.ROOT)));
        output.println("Purchase dates: " + offer.start() + " through " + offer.end());
        output.println("Terms: " + offer.terms());
        output.println();
        printPromotionProgress(offer);
        output.println("\nEligible purchases\n----------------------------------------");
        for (var link : offer.contributions().values().stream().sorted(java.util.Comparator.comparing(GiftCardPromotion.Contribution::receiptId)).toList()) {
            var receipt = repository.findById(link.receiptId()).orElse(null);
            var eligible = promotionEligibleAmount(offer, link, receipt);
            output.println("\n+----------------------+------------------------------------------------------------------+");
            promotionRow("Receipt", receiptNumber(receipt));
            if (receipt != null) {
                for (UUID id : link.lineIds()) {
                    var item = receipt.transaction().getPurchaseItems().stream().filter(line -> line.getLineItemId().equals(id)).findFirst();
                    if (item.isEmpty()) { output.println("  Previously selected product: missing; needs review"); continue; }
                    var line = item.orElseThrow();
                    output.println("+----------------------+------------------------------------------------------------------+");
                    promotionRow("Product", line.getProductName());
                    promotionRow("Quantity", Integer.toString(line.getQuantity()));
                    var sale = QualifyingSpend.calculate(receipt.transaction(), List.of(id));
                    promotionRow("Sale total", sale.amount() == null ? "Unconfirmed: " + sale.problem() : dollars(sale.amount()));
                    var manufacturer = receipt.transaction().getAppliedCoupons().stream()
                            .filter(c -> c.getLineItemIds().equals(List.of(id)) && receipt.transaction().getObservations().discountKinds().get(c.getApplicationId()) == ReceiptObservations.DiscountKind.MANUFACTURER)
                            .map(CouponApplied::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (manufacturer.signum() > 0) promotionRow("Manufacturer coupon", dollars(manufacturer));
                }
            }
            output.println("+----------------------+------------------------------------------------------------------+");
            promotionRow("Applied to rebate", eligible.map(this::dollars).orElse("awaiting confirmation"));
            if (eligible.isEmpty()) {
                output.println("  Previously recorded: " + (link.reviewedAmount() == null ? "calculated from products" : dollars(link.reviewedAmount())));
                output.println("  Review reason: " + Objects.toString(promotionReviewReason(link, receipt), "Offer dates or sale classifications need confirmation."));
                output.println("  Choose REVIEW to confirm eligible products and amount for this receipt.");
            }
            promotionRow("Note", link.note());
            output.println("+----------------------+------------------------------------------------------------------+");
        }
        output.println();
    }

    /** Records persistent opening observations and external movements, with source review. */
    private void balanceSettings() {
        try { balanceSettingsFields(); }
        catch (Navigate back) { output.println("Returned To Main Menu."); }
    }

    private void balanceSettingsFields() {
        LocalDate day = date("Balance date (Enter or BACK returns; EXIT closes CLI)");
        if (day == null) return;
        while (true) {
            output.println("1. Month-start balance\n2. Day-start balance\n3. Both (same entered value)\n4. View balances\n5. Opening history\n6. Opening with points/source\n7. External adjustment\n8. Activity sources\n9. Back to account balances");
            int choice = integer("Balance action", 1, 9);
            int action = choice == 9 ? 5 : choice == 5 ? 9 : choice;
            var records = savedIds.stream().map(id -> repository.findById(id).orElseThrow()).toList();
            if (action == 5) return;
            if (action == 4) { balances.print(day, records, output); continue; }
            if (action == 8) {
                balances.movements(records).stream().filter(m -> m.date() == null || !m.date().isAfter(day)).forEach(m ->
                        output.println(m.date() + " | " + m.provider() + " | points " + m.points() + " | USD " + m.usd() + " | " + m.source()));
                continue;
            }
            if (action == 9) {
                balances.openingHistory().forEach(o -> output.println(o.date() + " | " + o.provider() + " | points " + o.originalUnits()
                        + " | USD " + o.usdValue() + " | " + o.sourceNote() + " | " + o.observationId()));
                continue;
            }
            try {
                Runnable commit = questions(() -> balanceEntry(day, action, records));
                commit.run();
            } catch (Navigate navigation) { output.println("Returned to balance actions; no pending entry saved."); }
        }
    }
    private Runnable balanceEntry(LocalDate day, int action, List<ReceiptRecord> records) {
        var programs = balances.programs(records);
        for (int i = 0; i < programs.size(); i++) output.println((i + 1) + ". " + programs.get(i));
        String program = programs.get(integer("Provider", 1, programs.size()) - 1);
        if (action == 6) {
            boolean month = yes("Month-start observation? No records day start", false);
            BigDecimal points = amount("Original POINT balance (blank unknown or monetary account)", true, false, true);
            BigDecimal usd = amount("Separate USD balance / valuation (blank unknown)", true, false, true);
            String basis = required("USD valuation basis (or monetary face value)");
            String source = required("Opening source / confirmation note");
            return yes("Save this confirmed opening?", false)
                    ? () -> balances.recordOpening(day, program, points, usd, month, basis, source) : () -> { };
        }
        if (action == 7) {
            BigDecimal points = amount("Signed point change (blank if no points movement)", true, false, true);
            BigDecimal usd = amount("Signed USD change (blank unknown)", true, false, true);
            String reason = required("Adjustment reason / source (payout, expiration, conversion, correction)");
            String actor = required("Recorded by");
            return yes("Save this external movement? Do not repeat receipt activity", false)
                    ? () -> balances.recordAdjustment(day, program, points, usd, reason, actor) : () -> { };
        }
        BigDecimal value = amount("Starting USD value (use your recorded valuation for points programs)", false, false, true);
        return () -> {
            if (action == 1 || action == 3) balances.recordOpening(day.withDayOfMonth(1), program, value, true);
            if (action == 2 || action == 3) balances.recordOpening(day, program, value, false);
            output.println("Starting balance recorded for " + program + ".");
        };
    }


    /** Reads original text and routes item/earning navigation without editing recorded values. */
    private String text(String label) { return text(label,label,Map.of()); }

    private String text(String label,String visibleLabel,Map<String,String> aliases) {
        QuestionFrame frame = questionFrames.peek();
        if (frame != null) frame.currentLabel = label;
        if (frame != null && frame.cursor < frame.answers.size()) {
            String value = frame.answers.get(frame.cursor++);
            output.println(visibleLabel + ": " + value + " (retained)");
            return value;
        }
        while (true) {
            output.println(visibleLabel + ":");
            String value = input.nextLine();
            value = aliases.getOrDefault(value.trim(),value);
            if(value.trim().equalsIgnoreCase("DETAILED BREAKDOWN")) value="WORKSHEET";
            if(label.equals("Field to change") && value.trim().equalsIgnoreCase("DISCOUNT TYPE")) value="KIND";
            if(label.startsWith("Edit:") || label.startsWith("DETAILS / ITEM /"))value=switch(value.trim().toUpperCase(Locale.ROOT)) {
                case "RECEIPT DETAILS" -> "DETAILS";case "PRODUCTS" -> "ITEM";case "TAX AND PAYMENT" -> "CHECKOUT";
                case "DISCOUNTS AND STORE PROMOTIONS" -> "DISCOUNTS";case "MANUFACTURER COUPONS" -> "COUPONS";
                case "ONLINE REBATE ELIGIBILITY" -> "PROMOTIONS";case "REWARDS AND REBATES" -> "EARNINGS";
                case "PRINTED ACCOUNT BALANCES" -> "ACCOUNT";case "PRINTED SAVINGS FOOTER" -> "SAVINGS";
                case "VERIFY PRINTED SAVINGS" -> "SAVINGS LINKS";case "ATTACHED RECEIPT FILES" -> "EVIDENCE";default -> value;
            };
            String command = value.equals("\u001b") || value.trim().equals("^[") ? "BACK" : value.trim().toUpperCase(Locale.ROOT);
            if (editingSection && command.equals("DONE")) throw new EditDone();
            if (command.equals("EXIT")) {
                if ((!receiptInProgress && !readingItem && !readingEarning && (frame == null || frame.cursor == 0))
                        || navigationConfirmation(exitConfirmation())) throw new ExitCli();
                if(label.startsWith("SAVE / EDIT")) output.println("Draft kept open. Choose PAUSE to keep this draft for later, SAVE to approve it, or CANCEL to discard it. All return to main menu.");
                continue;
            }
            if (command.equals("CANCEL")) {
                String message = label.startsWith("SAVE / EDIT") || label.startsWith("DETAILS / ITEM /")
                        ? "Discard this unsaved receipt or proposed corrections and return to main menu? YES returns to main menu; NO keeps editing"
                        : "Cancel this operation and discard its uncommitted answers? Completed receipt sections are retained";
                if (!navigationConfirmation(message)) continue;
                if (readingItem) throw new ItemEntryCancelled();
                if (readingEarning) throw new EarningCancelled();
                throw new Navigate(false);
            }
            if (command.equals("BACK")) {
                if (readingEarning) throw new PreviousEarningQuestion();
                if (readingItem) throw new PreviousItemQuestion();
                throw new Navigate(true);
            }
            if (readingItem && command.equals("DONE")) throw new ItemEntryCancelled();
            if (frame != null) { frame.answers.add(value); frame.labels.add(label); frame.cursor++; }
            return value;
        }
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

    /** Reuses only an explicitly saved valuation for the same provider and exact point quantity. */
    private Optional<ReceiptObservations.RecordedValuation> savedPointValuation(String provider, BigDecimal points) {
        ReceiptObservations.RecordedValuation found = null;
        for (var id : savedIds) {
            var receipt = repository.findById(id).orElse(null); if (receipt == null) continue;
            for (var earning : receipt.transaction().getObservations().earnings()) {
                var benefit = earning.confirmed() == null ? earning.expected() : earning.confirmed();
                if (earning.status() != RebateStatus.REJECTED && earning.provider().equalsIgnoreCase(provider)
                        && benefit != null && benefit.kind() == BenefitKind.POINTS && benefit.amount().compareTo(points) == 0
                        && earning.valuation() != null && Currency.getInstance("USD").equals(earning.valuation().currency())) {
                    if (found != null && found.amount().compareTo(earning.valuation().amount()) != 0) return Optional.empty();
                    found = earning.valuation();
                }
            }
        }
        return Optional.ofNullable(found);
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
        private ReceiptObservations.SpendBasis spendBasis = ReceiptObservations.SpendBasis.UNKNOWN;
        private BigDecimal required, entered, valuation;
        private Scope scope;
        private boolean deferredProducts, reusedValuation;
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
                    new ReceiptObservations.OfferRequirement(requirementType, required, requirementCategory, spendBasis),
                    valuation == null ? null : new ReceiptObservations.RecordedValuation(valuation, Currency.getInstance("USD"), basis));
        }
    }

    /** Mutable entry state exists only before save; publication captures immutable facts. */
    private static final class Draft {
        private final UUID id;
        Draft() {this(UUID.randomUUID());}
        Draft(UUID id) {this.id=Objects.requireNonNull(id);}
        private boolean imported;
        private int entryStage=8;
        private final List<ReceiptImportDraft.ReviewIssue> importIssues=new ArrayList<>();
        private String number, time, retailer, actor;
        private ReceiptMeta.PurchaseChannel purchaseChannel=ReceiptMeta.PurchaseChannel.UNKNOWN;
        private LocalDate date;
        private BigDecimal tax, total, savingPercent;
        private final List<Item> items = new ArrayList<>();
        private final List<CouponApplied> sharedCoupons = new ArrayList<>();
        private final Map<UUID,DiscountKind> sharedKinds=new HashMap<>();
        private final List<Payment> payments = new ArrayList<>();
        private final List<RegisterRewardRedemption> knownRR = new ArrayList<>();
        private final List<UnidentifiedRR> unknownRR = new ArrayList<>();
        private final List<ReceiptPromotionLink> promotionLinks = new ArrayList<>();
        private ReceiptWorksheetData worksheet = ReceiptWorksheetData.empty();
        private FooterStatus footerStatus = FooterStatus.UNKNOWN;
        private boolean correcting;
        private final List<Earning> earnings = new ArrayList<>();
        private final java.util.Set<UUID> deferredEarnings = new HashSet<>();
        private final List<SavingsCategory> savings = new ArrayList<>();
        private final List<ReceiptEvidence> evidence = new ArrayList<>();
        private WalgreensCashAccount account;

        Draft copy() { Draft result = new Draft(id); result.restore(this); return result; }
        void restore(Draft from) {
            imported=from.imported;entryStage=from.entryStage;purchaseChannel=from.purchaseChannel;
            importIssues.clear();importIssues.addAll(from.importIssues);
            sharedKinds.clear();sharedKinds.putAll(from.sharedKinds);
            number = from.number; time = from.time; retailer = from.retailer; actor = from.actor;
            date = from.date; tax = from.tax; total = from.total; savingPercent = from.savingPercent; account = from.account;
            items.clear(); items.addAll(from.items); sharedCoupons.clear(); sharedCoupons.addAll(from.sharedCoupons);
            payments.clear(); payments.addAll(from.payments); knownRR.clear(); knownRR.addAll(from.knownRR);
            unknownRR.clear(); unknownRR.addAll(from.unknownRR); promotionLinks.clear(); promotionLinks.addAll(from.promotionLinks); worksheet=from.worksheet; footerStatus=from.footerStatus; correcting=from.correcting;
            earnings.clear(); earnings.addAll(from.earnings); savings.clear(); savings.addAll(from.savings);
            evidence.clear(); evidence.addAll(from.evidence); deferredEarnings.clear(); deferredEarnings.addAll(from.deferredEarnings);
        }

        /** Builds fresh working contents, preserving draft IDs. */
        CouponTransaction transaction() {
            CouponTransaction transaction = new CouponTransaction(new ReceiptTotals(tax, total));
            Map<UUID, DiscountKind> kinds = new HashMap<>();
            for (Item item : items) {
                transaction.addPurchaseItem(item.item());
                item.deductions().forEach(transaction::addAppliedCoupon);
                kinds.putAll(item.kinds());
            }
            sharedCoupons.forEach(coupon -> {
                transaction.addAppliedCoupon(coupon);
                kinds.put(coupon.getApplicationId(), sharedKinds.getOrDefault(coupon.getApplicationId(),DiscountKind.MANUFACTURER));
            });
            payments.forEach(transaction::addPayment);
            knownRR.forEach(transaction::addRegisterRewardRedemption);
            transaction.setObservations(retainWorksheet(new ReceiptObservations(date, time, earnings, unknownRR, account, savings, kinds, savingPercent, promotionLinks, worksheet, footerStatus),payments.stream().map(Payment::getPaymentId).toList(),knownRR.stream().map(RegisterRewardRedemption::getRedemptionId).toList()));
            return transaction;
        }

        /** No combined timestamp is invented from partial date/time observations. */
        ReceiptMeta metadata() { return new ReceiptMeta(retailer, number, date==null?null:ReceiptTimeInput.parse(time).map(date::atTime).orElse(null), null,purchaseChannel); }

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
