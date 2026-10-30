package coupons.rewards;

import coupons.promotions.BenefitKind;
import coupons.receipts.ReceiptObservations;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Explicit reconciliation of user-provided opening and point-value observations, never a startup reset. */
public final class SpreadsheetBalanceImport {
    private SpreadsheetBalanceImport() { }
    public record Row(String type, LocalDate date, String provider, BigDecimal points, BigDecimal usd, String receipt) { }
    public record Plan(List<AccountOpening> openings, Map<UUID,ReceiptSnapshot> receipts, Map<UUID,Long> revisions) {
        public Plan { openings=List.copyOf(openings); receipts=Map.copyOf(receipts); revisions=Map.copyOf(revisions); }
        public boolean empty() { return openings.isEmpty() && receipts.isEmpty(); }
    }
    public static List<Row> read(Path source) throws IOException {
        var rows = new ArrayList<Row>();
        for (String line : Files.readAllLines(source)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            var columns = line.split("\t",-1);
            if (columns.length != 6) throw new IllegalArgumentException("Import requires type, date, provider, points, USD, receipt number.");
            var row = new Row(columns[0],LocalDate.parse(columns[1]),columns[2],columns[3].isBlank()?null:new BigDecimal(columns[3]),usdValue(columns[4]),columns[5]);
            if (!Set.of("OPENING","EARNING","REGULAR_PRICE").contains(row.type()) || row.provider().isBlank() || row.usd().signum()<0 || row.points()!=null && row.points().signum()<0)
                throw new IllegalArgumentException("Invalid opening or earning observation.");
            rows.add(row);
        }
        return List.copyOf(rows);
    }
    private static BigDecimal usdValue(String expression) {
        var parts=expression.trim().split("/",-1);
        if(parts.length==1) return new BigDecimal(parts[0]);
        if(parts.length==2) return new BigDecimal(parts[0].trim()).divide(new BigDecimal(parts[1].trim()),java.math.MathContext.DECIMAL128);
        throw new IllegalArgumentException("USD value must be a decimal or numerator/denominator.");
    }
    private static boolean same(BigDecimal a,BigDecimal b) { return a==null ? b==null : b!=null && a.compareTo(b)==0; }
    public static Plan plan(InMemoryTransactionRepository repo, List<Row> rows, String source) {
        var openings = new ArrayList<AccountOpening>(); var receipts = new LinkedHashMap<UUID,ReceiptSnapshot>(); var revisions = new HashMap<UUID,Long>();
        var active = repo.creationHistory().stream().map(h -> repo.findById(h.after().transactionId()).orElse(null)).filter(Objects::nonNull).toList();
        for (var row : rows) {
            if (row.type().equals("OPENING")) {
                if (row.date().getDayOfMonth()!=1) throw new IllegalArgumentException("Monthly opening date must be the first.");
                var existing = repo.accountBalances().openings().stream().filter(o -> o.monthStart() && o.date().equals(row.date()) && o.provider().equals(row.provider())).reduce((a,b)->b);
                if (existing.isPresent() && same(existing.orElseThrow().usdValue(),row.usd()) && same(existing.orElseThrow().originalUnits(),row.points())) continue;
                openings.add(new AccountOpening(UUID.randomUUID(),row.date(),row.provider(),row.points(),row.usd(),true,"User spreadsheet: units and USD recorded separately",source,Instant.now()));
            } else {
                var matches = active.stream().filter(r -> r.metadata()!=null && row.receipt().equals(r.metadata().receiptNumber()) && row.date().equals(r.transaction().getObservations().purchaseDate())).toList();
                if (matches.size()!=1) throw new IllegalArgumentException("Expected one saved receipt for " + row.receipt());
                var receipt = matches.get(0); var snapshot = receipts.getOrDefault(receipt.transactionId(),ReceiptSnapshot.from(receipt.transaction()));
                if (row.type().equals("REGULAR_PRICE")) {
                    var lineId=UUID.fromString(row.provider()); var lines=new ArrayList<>(snapshot.purchaseItems()); int selected=-1;
                    for(int i=0;i<lines.size();i++) if(lines.get(i).lineItemId().equals(lineId)) selected=i;
                    if(selected<0) throw new IllegalArgumentException("Receipt line not found on " + row.receipt());
                    var line=lines.get(selected);
                    if(same(line.regularUnitPrice(),row.usd())) continue;
                    lines.set(selected,new PurchaseLineSnap(line.lineItemId(),line.productName(),line.productCode(),row.usd(),line.saleUnitPrice(),line.quantity()));
                    receipts.put(receipt.transactionId(),new ReceiptSnapshot(lines,snapshot.coupons(),snapshot.appliedCoupons(),snapshot.promotions(),snapshot.payments(),snapshot.rewards(),snapshot.registerRewardRedemptions(),snapshot.receiptTotals(),snapshot.observations()));
                    revisions.put(receipt.transactionId(),receipt.revision());
                    continue;
                }
                var o = snapshot.observations(); var earnings = new ArrayList<>(o.earnings()); int match = -1;
                for (int i=0;i<earnings.size();i++) {
                    var e=earnings.get(i); var benefit=e.confirmed()==null?e.expected():e.confirmed();
                    if (e.provider().equals(row.provider()) && benefit.kind()==BenefitKind.POINTS && same(benefit.amount(),row.points())) {
                        if (match>=0) throw new IllegalArgumentException("Ambiguous point earning on " + row.receipt()); match=i;
                    }
                }
                if (match<0) throw new IllegalArgumentException("Point earning not found on " + row.receipt());
                var e=earnings.get(match);
                if (e.valuation()!=null && Currency.getInstance("USD").equals(e.valuation().currency()) && same(e.valuation().amount(),row.usd())) continue;
                earnings.set(match,new Earning(e.earningId(),e.kind(),e.provider(),e.offerReference(),e.scope(),e.lineItemIds(),e.expected(),e.confirmed(),e.status(),e.expirationDate(),e.instrumentId(),e.claimId(),e.evidenceIds(),e.recordedLifecycle(),e.requirement(),new RecordedValuation(row.usd(),Currency.getInstance("USD"),source)));
                var tx=snapshot.toTransaction();
                tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),earnings,o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds(),o.receiptSavingValuePercent(),o.promotionLinks(), o.worksheet(),o.footerStatus()));
                receipts.put(receipt.transactionId(),ReceiptSnapshot.from(tx)); revisions.put(receipt.transactionId(),receipt.revision());
            }
        }
        return new Plan(openings,receipts,revisions);
    }
    public static void apply(InMemoryTransactionRepository repo,Plan plan,String source) {
        // Check all expected receipt revisions before publishing any part of this reconciliation.
        plan.revisions().forEach((id,revision)-> { if(repo.findById(id).orElseThrow().revision()!=revision) throw new ConcurrentModificationException("Receipt changed; rebuild the import plan."); });
        for(var entry:plan.receipts().entrySet()) {
            var receipt=repo.findById(entry.getKey()).orElseThrow();
            repo.update(receipt.transactionId(),plan.revisions().get(receipt.transactionId()),entry.getValue().toTransaction(),receipt.metadata(),List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptChangeContext("Local owner",source,"Local owner",Instant.now()));
        }
        plan.openings().forEach(repo::recordAccountOpening);
    }
    public static void main(String[] args) throws Exception {
        if (args.length<2 || args.length>3 || args.length==3 && !args[2].equals("--apply")) throw new IllegalArgumentException("Usage: SpreadsheetBalanceImport receipts.bin observations.tsv [--apply]");
        var file=Path.of(args[0]); var source=Path.of(args[1]);
        try(var repo=InMemoryTransactionRepository.openLocal(file)) {
            var plan=plan(repo,read(source),"User spreadsheet reconciliation: " + source.getFileName());
            System.out.println("Opening corrections: " + plan.openings().size() + "; receipt corrections: " + plan.receipts().size());
            plan.openings().forEach(o->System.out.println(o.date()+" "+o.provider()+" | units "+o.originalUnits()+" | USD "+o.usdValue()));
            plan.receipts().forEach((id,s)->System.out.println("Receipt " + repo.findById(id).orElseThrow().metadata().receiptNumber()+" | expected revision " + plan.revisions().get(id)));
            if(args.length==3 && !plan.empty()) {
                var backup=file.resolveSibling(file.getFileName()+".before-spreadsheet-"+Instant.now().toEpochMilli()+".bak");
                Files.copy(file,backup); System.out.println("Backup: " + backup);
                apply(repo,plan,"User-approved spreadsheet values: " + source.getFileName());
                System.out.println("Saved. Reapplying the same observations makes no additional earnings or corrections.");
            }
        }
    }
}
