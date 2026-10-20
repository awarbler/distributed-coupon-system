package coupons.repository;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Immutable source inventory. Reads only an explicit separate copy, never opens local CLI storage. */
final class LocalMigrationSource {
    record FileData(String path,String sha256,String modified,byte[] bytes) {}
    final Path directory;
    final InMemoryStore.State state;
    final List<FileData> files;
    final byte[] checkpoint;
    final String digest;
    final Path protectedSource;
    private LocalMigrationSource(Path directory,Path protectedSource,List<FileData> files,InMemoryStore.State state) {
        this.directory=directory;this.protectedSource=protectedSource;this.files=List.copyOf(files);this.state=state;
        checkpoint=files.stream().filter(f->f.path().equals("receipts.bin")).findFirst().orElseThrow().bytes();
        digest=digest(files);
    }
    static LocalMigrationSource read(Path copy,Path protectedSource)throws IOException {
        Path original=protectedSource.toAbsolutePath().normalize();
        if(Files.exists(original))original=original.toRealPath();
        Path selected=copy.toRealPath();
        separate(selected,original);
        Path workingOriginal=Path.of(".coupon-data").toAbsolutePath().normalize();
        if(Files.exists(workingOriginal))workingOriginal=workingOriginal.toRealPath();
        separate(selected,workingOriginal);
        if(!Files.isDirectory(selected,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Select a copied receipt directory.");
        List<FileData> files=new ArrayList<>();
        try(var paths=Files.walk(selected)) {
            for(Path p:paths.sorted().toList()) {
                if(Files.isSymbolicLink(p))throw new IllegalArgumentException("Source copy must not contain symbolic links.");
                if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Source copy contains a non-regular file.");
                try { if(((Number)Files.getAttribute(p,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue()>1)throw new IllegalArgumentException("Source copy contains hard-linked files; use independent copies."); }
                catch(UnsupportedOperationException ignored) { /* Not a Unix file system. */ }
                byte[] bytes=Files.readAllBytes(p);
                files.add(new FileData(selected.relativize(p).toString().replace(File.separatorChar,'/'),hash(bytes),Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toString(),bytes));
            }
        }
        if(files.stream().noneMatch(f->f.path().equals("receipts.bin")))throw new IllegalArgumentException("Source copy has no receipts.bin checkpoint.");
        var source=new LocalMigrationSource(selected,original,files,LocalReceiptFile.readSnapshot(selected.resolve("receipts.bin"),files.stream().filter(f->f.path().equals("receipts.bin")).findFirst().orElseThrow().bytes()));
        source.validateState();source.verifyUnchanged();return source;
    }
    private static void separate(Path selected,Path original) {
        if(selected.startsWith(original)||original.startsWith(selected))throw new IllegalArgumentException("Source must be a separate copy outside the original receipt directory.");
    }
    void verifyUnchanged()throws IOException {
        List<String> paths;
        try(var entries=Files.walk(directory)) {
            paths=entries.filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).map(p->directory.relativize(p).toString().replace(File.separatorChar,'/')).sorted().toList();
        }
        if(!paths.equals(files.stream().map(FileData::path).sorted().toList()))throw new IOException("Source copy changed during inspection.");
        for(var f:files) {
            Path path=directory.resolve(f.path());
            if(Files.isSymbolicLink(path)||!hash(Files.readAllBytes(path)).equals(f.sha256())
                    ||!Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toString().equals(f.modified()))
                throw new IOException("Source copy changed during inspection.");
        }
    }
    private void validateState() {
        // Structural relationships only. Do not reinterpret historical prices or eligibility rules.
        var s=state;
        for(var entry:s.receipts().entrySet()) {
            var r=entry.getValue();require(entry.getKey().equals(r.transactionId()),"receipt map ID");
            var history=s.history().values().stream().filter(h->h.after().transactionId().equals(r.transactionId()))
                    .sorted(Comparator.comparingLong(h->h.after().revision())).toList();
            require(history.size()==r.revision(),"missing receipt history revision");
            ReceiptRevSnap previous=null;
            for(int i=0;i<history.size();i++) {
                var h=history.get(i);require(h.after().revision()==i+1L && Objects.equals(previous,h.before()),"history continuity");
                var w=s.requests().get(h.request().requestId());
                require(w!=null&&w.result().equals(h.result()),"history request/result");
                require(s.history().get(h.historyId()).equals(h),"history ID");
                checkEvidence(h.after());if(h.before()!=null)checkEvidence(h.before());previous=h.after();
            }
            require(r.equals(previous),"current revision differs from history");checkEvidence(r);
        }
        require(s.history().size()==s.requests().size(),"unpaired request/history records");
        for(var entry:s.history().entrySet())require(entry.getKey().equals(entry.getValue().historyId())&&s.receipts().containsKey(entry.getValue().after().transactionId()),"history owner or ID");
        for(var entry:s.requests().entrySet()) {
            var w=entry.getValue();require(entry.getKey().equals(w.result().requestId()),"request map ID");
            var h=s.history().values().stream().filter(v->v.request().requestId().equals(entry.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("Missing request history."));
            InMemoryStore.WriteInput expected=switch(h.result().action()) {
                case CREATED -> new InMemoryStore.CreateInput(h.after().transactionId(),h.after().contents(),h.after().metadata(),evidenceFor(w.input()),h.request(),h.createCtx());
                case UPDATED -> new InMemoryStore.UpdateInput(h.after().transactionId(),h.before().revision(),h.after().contents(),h.after().metadata(),evidenceFor(w.input()),h.request(),h.changeCtx());
                case DELETED -> new InMemoryStore.DeleteInput(h.after().transactionId(),h.before().revision(),h.request(),h.changeCtx());
                case RESTORED -> new InMemoryStore.RestoreInput(h.after().transactionId(),h.before().revision(),h.request(),h.changeCtx());
            };
            require(w.input().equals(expected),"request payload differs from audit history");
            for(var e:evidenceFor(w.input()))require(e.equals(s.evidence().get(e.getEvidenceId())),"request evidence");
        }
        for(var e:s.evidence().entrySet())require(e.getKey().equals(e.getValue().getEvidenceId())&&s.receipts().containsKey(e.getValue().getTransactionId()),"evidence owner");
        for(var r:s.rewards().entrySet())require(r.getKey().equals(r.getValue().getRewardId())&&(r.getValue().getEarnedReceiptId()==null||s.receipts().containsKey(r.getValue().getEarnedReceiptId())),"reward owner");
        Set<UUID> used=new HashSet<>();
        for(var entry:s.redemptions().entrySet()) {
            var r=entry.getValue();var receipt=s.receipts().get(r.getRedeemedReceiptId());
            require(entry.getKey().equals(r.getRedemptionId())&&s.rewards().containsKey(r.getRewardId())&&receipt!=null
                    &&receipt.contents().registerRewardRedemptions().contains(r)&&used.add(r.getRewardId()),"redemption reference or duplicate use");
        }
        for(var r:s.receipts().values())for(var redemption:r.contents().registerRewardRedemptions())require(redemption.equals(s.redemptions().get(redemption.getRedemptionId())),"missing redemption");
        for(var v:s.rewardVoids().entrySet())require(v.getKey().equals(v.getValue().rewardId())&&s.rewards().containsKey(v.getKey())&&!used.contains(v.getKey()),"removed reward");
        Set<UUID> deductions=new HashSet<>();
        for(var e:s.rrUsageLinks().entrySet()) {
            var link=e.getValue();boolean source=link.receiptEarning()?s.history().values().stream().anyMatch(h->h.after().contents().observations().earnings().stream().anyMatch(v->v.earningId().equals(link.sourceId()))):s.rewards().containsKey(link.sourceId());
            require(e.getKey().equals(link.sourceId())&&source&&s.receipts().containsKey(link.spentReceiptId())&&deductions.add(link.usageEventId()),"RR matching reference");
            require(s.history().values().stream().filter(h->h.after().transactionId().equals(link.spentReceiptId())).anyMatch(h->h.after().contents().observations().unidentifiedRR().stream().anyMatch(u->u.eventId().equals(link.usageEventId()))),"historical RR deduction missing");
            // Stale confirmations remain reserved and are retained for review, just like local storage.
        }
        for(var e:s.giftCardPromotions().entrySet()) {
            require(e.getKey().equals(e.getValue().id()),"promotion ID");
            for(var contribution:e.getValue().contributions().values())require(s.receipts().containsKey(contribution.receiptId())&&s.history().values().stream().anyMatch(h->h.after().transactionId().equals(contribution.receiptId())&&h.after().revision()==contribution.reviewedRevision()),"promotion reviewed revision");
        }
        for(var r:s.receipts().values())for(var link:r.contents().observations().promotionLinks())require(s.giftCardPromotions().containsKey(link.promotionId()),"receipt promotion");
        require(s.balances().openings().stream().map(AccountOpening::observationId).distinct().count()==s.balances().openings().size(),"duplicate opening IDs");
        require(s.balances().adjustments().stream().map(AccountAdjustment::eventId).distinct().count()==s.balances().adjustments().size(),"duplicate adjustment IDs");
    }
    private void checkEvidence(ReceiptRevSnap r) {
        for(var id:r.evidenceIds()){var e=state.evidence().get(id);require(e!=null&&e.getTransactionId().equals(r.transactionId()),"missing historical evidence");}
    }
    private static List<ReceiptEvidence> evidenceFor(InMemoryStore.WriteInput i) {
        if(i instanceof InMemoryStore.CreateInput c)return c.evidence();if(i instanceof InMemoryStore.UpdateInput u)return u.evidence();return List.of();
    }
    private static void require(boolean condition,String field){if(!condition)throw new IllegalArgumentException("Source cannot be migrated without loss: "+field);}
    static String hash(byte[] bytes) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String digest(List<FileData> files) {
        try(var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes)) {
            for(var f:files.stream().sorted(Comparator.comparing(FileData::path)).toList()){out.writeUTF(f.path());out.writeUTF(f.sha256());}
            out.flush();return hash(bytes.toByteArray());
        }catch(IOException e){throw new IllegalStateException(e);}
    }
}
