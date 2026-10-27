package coupons.repository;

import coupons.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.serialize;

/** Read-only, repeatable-read comparison. Never applies schema or calls a repository write. */
public final class LocalMigrationPreview {
    private LocalMigrationPreview() {}
    public record Category(String name,int found,int destination,int identical,int newCandidates,int conflicts) {}
    public record Preview(String sourceDigest,List<Category> categories,List<String> duplicates,
            List<String> warnings,List<String> evidenceHashes,List<String> financialValues,
            String importDecision,String dashboardCoverage) {
        public Preview {categories=List.copyOf(categories);duplicates=List.copyOf(duplicates);warnings=List.copyOf(warnings);
            evidenceHashes=List.copyOf(evidenceHashes);financialValues=List.copyOf(financialValues);}
    }
    public static Preview inspect(Path copy,Path protectedSource,PostgresConnectionFactory db,String expectedDatabase)throws Exception {
        var source=LocalMigrationSource.read(copy,protectedSource);
        try(var c=db.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setReadOnly(true);c.setAutoCommit(false);
            try {
                try(var p=c.prepareStatement("SELECT current_database(), current_setting('transaction_read_only')");var r=p.executeQuery()) {
                    r.next();if(expectedDatabase==null||!expectedDatabase.equals(r.getString(1)))throw new IllegalArgumentException("Explicit expected database name does not match.");
                    if(!r.getString(2).equals("on"))throw new IllegalStateException("Preview requires a read-only database transaction.");
                }
                var destination=PostgresReceiptState.read(c);
                var result=compare(source,destination,c);
                source.verifyUnchanged();return result;
            } finally {c.rollback();}
        }
    }
    private static Preview compare(LocalMigrationSource source,InMemoryStore.State d,Connection c)throws SQLException {
        var s=source.state;List<Category> rows=new ArrayList<>();List<String> warnings=new ArrayList<>(),duplicates=new ArrayList<>();
        Map<String,ReceiptRevSnap> all=new LinkedHashMap<>();
        s.receipts().values().stream().sorted(Comparator.comparing(r->r.transactionId().toString())).forEach(r->all.put("source:"+r.transactionId(),r));
        d.receipts().values().stream().sorted(Comparator.comparing(r->r.transactionId().toString())).forEach(r->all.put("database:"+r.transactionId(),r));
        var entries=new ArrayList<>(all.entrySet());
        Set<UUID> duplicateSourceIds=new HashSet<>();
        for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++) {
            var a=entries.get(i);var b=entries.get(j);
            if(a.getValue().transactionId().equals(b.getValue().transactionId()))continue;
            if(sameReceiptKey(a.getValue().metadata(),b.getValue().metadata())) {
                duplicates.add("Possible printed-receipt duplicate (including deleted): "+a.getKey()+" / "+b.getKey());
                if(a.getKey().startsWith("source:"))duplicateSourceIds.add(a.getValue().transactionId());
                if(b.getKey().startsWith("source:"))duplicateSourceIds.add(b.getValue().transactionId());
            }
        }
        add(rows,"Receipts (active and deleted)",s.receipts(),d.receipts());
        add(rows,"Receipt revisions / audit snapshots",s.history(),d.history());
        add(rows,"Receipt retry requests and original results",s.requests(),d.requests());
        add(rows,"Evidence (owner, type, exact bytes)",s.evidence(),d.evidence());
        add(rows,"Standalone RR instruments",s.rewards(),d.rewards());
        add(rows,"RR redemptions",s.redemptions(),d.redemptions());
        add(rows,"RR matching confirmations",s.rrUsageLinks(),d.rrUsageLinks());
        add(rows,"Reward removals",s.rewardVoids(),d.rewardVoids());
        add(rows,"Gift-card promotion progress",s.giftCardPromotions(),d.giftCardPromotions());
        add(rows,"Account openings",indexed(s.balances().openings(),AccountOpening::observationId),indexed(d.balances().openings(),AccountOpening::observationId));
        add(rows,"Account adjustments",indexed(s.balances().adjustments(),AccountAdjustment::eventId),indexed(d.balances().adjustments(),AccountAdjustment::eventId));
        add(rows,"Provider redemption history (original order)",sequence(s.balances().redemptionHistory()),sequence(d.balances().redemptionHistory()));
        add(rows,"Applied receipt coupons/deductions (embedded)",embedded(s,"coupons"),embedded(d,"coupons"));
        add(rows,"Receipt reward/rebate earnings (embedded)",embedded(s,"earnings"),embedded(d,"earnings"));
        add(rows,"Purchased product lines (not on-hand inventory)",embedded(s,"products"),embedded(d,"products"));
        for(var entry:Map.of("Shopping plans","shopping_plan_current","Clipped coupons","clipped_coupon_current","Inventory balances","inventory_current","Rebate claims","rebate_current","Advertised deals","deal_current").entrySet())
            rows.add(new Category(entry.getKey(),0,count(c,entry.getValue()),0,0,0));
        long nonempty=0;try(var p=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=current_schema() AND tablename <> 'schema_version'");var r=p.executeQuery()) {
            while(r.next())nonempty+=LocalToPostgresMigration.destinationRows(c,r.getString(1));
        }
        boolean replay=false;try(var p=c.prepareStatement("SELECT 1 FROM local_migrations WHERE source_digest=?")) {p.setString(1,source.digest);try(var r=p.executeQuery()){replay=r.next();}}
        for(int i=0;i<rows.size();i++) {var row=rows.get(i);if(row.conflicts()>0)warnings.add(row.name()+": "+row.conflicts()+" existing identities differ; do not overwrite.");}
        for(var r:s.receipts().values())if(d.receipts().containsKey(r.transactionId())&&!exact(r,d.receipts().get(r.transactionId())))
            warnings.add("Receipt UUID conflict: "+r.transactionId()+"; source revision="+r.revision()+", destination revision="+d.receipts().get(r.transactionId()).revision());
        Map<String,String> archive=new LinkedHashMap<>(),sourceArchive=new LinkedHashMap<>();
        for(var f:source.files)sourceArchive.put(f.path(),f.sha256());
        try(var p=c.prepareStatement("SELECT relative_path,sha256,content FROM local_migration_files WHERE source_digest=?")) {
            p.setString(1,source.digest);try(var r=p.executeQuery()){while(r.next()) {
                String actual=LocalMigrationSource.hash(r.getBytes(3));archive.put(r.getString(1),actual);
                if(!actual.equals(r.getString(2)))warnings.add("Stored archive hash mismatch: "+r.getString(1));
            }}
        }
        add(rows,"Source file archive (checkpoint and sidecars)",sourceArchive,archive);
        List<String> hashes=new ArrayList<>(),financial=new ArrayList<>();var calc=new TransactionCalculator();
        for(var receipt:s.receipts().values().stream().sorted(Comparator.comparing(r->r.transactionId().toString())).toList()) {
            String id=receipt.transactionId().toString();var tx=receipt.contents().toTransaction();
            if(receipt.metadata()==null||receipt.metadata().receiptNumber()==null)warnings.add(id+": printed receipt identity missing; duplicate lookup is incomplete.");
            if(tx.getObservations().purchaseDate()==null)warnings.add(id+": purchase date missing; dated summaries cannot fully cover this receipt.");
            int line=0;for(var item:receipt.contents().purchaseItems()) {line++;
                if(item.regularUnitPrice()==null)warnings.add(id+" line "+line+": regular price missing; savings remains partial.");
                if(item.productCode()==null)warnings.add(id+" line "+line+": SKU missing; do not infer catalog identity or inventory.");
            }
            for(var earning:receipt.contents().observations().earnings()) {
                if(earning.scope()==coupons.ReceiptObservations.Scope.UNKNOWN)warnings.add(id+": earning "+earning.earningId()+" has unknown product scope.");
                if(earning.confirmed()==null)warnings.add(id+": earning "+earning.earningId()+" is expected-only; no confirmed earned value is invented.");
            }
            for(var coupon:receipt.contents().appliedCoupons())if(!receipt.contents().observations().discountKinds().containsKey(coupon.getApplicationId()))warnings.add(id+": deduction "+coupon.getApplicationId()+" is unclassified; preserve for review.");
            if(calc.calculateReceiptDifference(tx).signum()!=0)warnings.add(id+": checkout discrepancy retained; financial values are not reconciled by rewriting them.");
            for(var coupon:receipt.contents().appliedCoupons())if(!coupon.isWholeReceipt()&&coupon.getLineItemIds().isEmpty())warnings.add(id+": coupon "+coupon.getApplicationId()+" has unknown product assignment.");
            financial.add(id+" | "+receipt.status()+" | reported due="+tx.getReceiptTotals().getFinalTotal().toPlainString()+" | calculated due="+calc.calculateExpectedCheckoutAmount(tx).toPlainString()+" | reported-minus-calculated="+calc.calculateReceiptDifference(tx).toPlainString());
        }
        Map<String,UUID> evidenceByHash=new HashMap<>();
        for(var e:d.evidence().values())evidenceByHash.put(LocalMigrationSource.hash(e.getContent()),e.getEvidenceId());
        for(var e:s.evidence().values().stream().sorted(Comparator.comparing(e->e.getEvidenceId().toString())).toList()) {
            String hash=LocalMigrationSource.hash(e.getContent());hashes.add(e.getEvidenceId()+" | receipt="+e.getTransactionId()+" | SHA-256="+hash+" | bytes="+e.getContent().length);
            UUID other=evidenceByHash.putIfAbsent(hash,e.getEvidenceId());if(other!=null&&!other.equals(e.getEvidenceId()))duplicates.add("Same evidence bytes under different IDs: "+e.getEvidenceId()+" / "+other+"; review, do not deduplicate automatically.");
        }
        var active=s.receipts().values().stream().filter(r->r.status()==ReceiptStatus.ACTIVE).map(r->new ReceiptRecord(r.transactionId(),r.status(),r.revision(),r.contents().toTransaction(),r.metadata(),r.evidenceIds())).toList();
        var savings=SavingsReport.calculate(active);warnings.addAll(savings.issues());
        warnings.add("Only receipts.bin is authoritative committed source. Other checkpoints, backups, drafts, TSV corrections and prepared imports are archive-only, not additional transactions.");
        warnings.add("No standalone shopping plans, clipped coupons, inventory balances, advertised deals or rebate claims exist in this checkpoint format. Receipt deductions and rebate earnings stay embedded; no new claim or available coupon is fabricated.");
        warnings.add("Provider-redemption commit timestamps absent from source remain NULL; original event times and append order are retained. External file references are not followed.");
        warnings.add("Eligible counts are identity-level candidates, not permission to merge. The existing lossless importer requires an empty destination, or verifies the archive for an identical prior import; it cannot merge into a populated database.");
        int conflicts=rows.stream().mapToInt(Category::conflicts).sum();
        String decision=conflicts>0||!duplicateSourceIds.isEmpty()?"BLOCKED: identity conflicts or possible receipt duplicates require review."
                :replay?"ALREADY IMPORTED: source digest exists; validate current records and archive before any action."
                :nonempty>0?"BLOCKED: destination is populated. Existing importer does not support merging; do not reset or overwrite it."
                :"ELIGIBLE FOR APPROVAL: empty destination; entire snapshot may be imported atomically after review. No import performed.";
        String coverage="Source-only projection (not a merged forecast): "+active.size()+" active receipts, "+(s.receipts().size()-active.size())+" deleted; receipts/coupons/payments/earnings/history/evidence and account openings/progress retained. "
                +"Regular+tax="+(savings.regularKnown()?savings.regularWithTax().toPlainString():"partial "+savings.regularWithTax().toPlainString())+"; due="+savings.due().toPlainString()+"; newly earned="+(savings.earningsKnown()?savings.newlyEarned().toPlainString():"partial "+savings.newlyEarned().toPlainString())
                +"; paid after rebates="+(savings.paymentsKnown()&&savings.earningsKnown()?savings.paidAfterRebates().toPlainString():"unknown/partial")+". Day/week/month summaries use original dates and existing Java calculations. Other feature screens receive no inferred records.";
        return new Preview(source.digest,rows,duplicates,warnings,hashes,financial,decision,coverage);
    }
    private static boolean sameReceiptKey(ReceiptMeta a,ReceiptMeta b) {
        return a!=null&&b!=null&&a.receiptNumber()!=null&&b.receiptNumber()!=null&&a.retailer().trim().equalsIgnoreCase(b.retailer().trim())&&a.receiptNumber().trim().equalsIgnoreCase(b.receiptNumber().trim());
    }
    private static boolean exact(Object a,Object b) {return Objects.equals(a,b)||Arrays.equals(serialize(a),serialize(b));}
    private static void add(List<Category> out,String name,Map<?,?> source,Map<?,?> dest) {
        int equal=0,conflict=0;for(var e:source.entrySet())if(dest.containsKey(e.getKey())){if(exact(e.getValue(),dest.get(e.getKey())))equal++;else conflict++;}
        out.add(new Category(name,source.size(),dest.size(),equal,source.size()-equal-conflict,conflict));
    }
    private static <T> Map<UUID,T> indexed(List<T> list,java.util.function.Function<T,UUID> key) {Map<UUID,T> out=new LinkedHashMap<>();for(var v:list)out.put(key.apply(v),v);return out;}
    private static Map<String,Object> sequence(List<?> values) {Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<values.size();i++)out.put("sequence:"+i,values.get(i));return out;}
    private static Map<String,Object> embedded(InMemoryStore.State s,String kind) {
        Map<String,Object> out=new LinkedHashMap<>();for(var r:s.receipts().values()) {
            if(kind.equals("coupons"))for(var v:r.contents().appliedCoupons())out.put(r.transactionId()+":"+v.getApplicationId(),v);
            if(kind.equals("earnings"))for(var v:r.contents().observations().earnings())out.put(r.transactionId()+":"+v.earningId(),v);
            if(kind.equals("products"))for(var v:r.contents().purchaseItems())out.put(r.transactionId()+":"+v.lineItemId(),v);
        }return out;
    }
    private static int count(Connection c,String table)throws SQLException {
        try(var p=c.createStatement();var r=p.executeQuery("SELECT count(*) FROM \""+table.replace("\"","\"\"")+"\"")){r.next();return r.getInt(1);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: LocalMigrationPreview SOURCE_COPY PROTECTED_ORIGINAL EXPECTED_DATABASE; uses COUPON_POSTGRES_* for read-only access.");
        var p=inspect(Path.of(args[0]),Path.of(args[1]),PostgresConnectionFactory.fromEnvironment(),args[2]);
        System.out.println("READ-ONLY MIGRATION PREVIEW — no writes or schema changes\nSource SHA-256: "+p.sourceDigest());
        System.out.printf("%-52s %7s %7s %7s %7s %7s%n","Category","Found","In DB","Exact","New","Conflict");
        for(var row:p.categories())System.out.printf("%-52s %7d %7d %7d %7d %7d%n",row.name(),row.found(),row.destination(),row.identical(),row.newCandidates(),row.conflicts());
        System.out.println("\n"+p.importDecision()+"\n"+p.dashboardCoverage());
        for(String line:p.duplicates())System.out.println("DUPLICATE: "+line);
        for(String line:p.warnings())System.out.println("REVIEW: "+line);
        for(String line:p.financialValues())System.out.println("FINANCIAL: "+line);
        for(String line:p.evidenceHashes())System.out.println("EVIDENCE: "+line);
    }
}
