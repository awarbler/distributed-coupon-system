package coupons.cli;

import coupons.receipts.ReceiptImportDraft;

import coupons.serialization.LegacyObjectStreams;

import coupons.repository.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Unapproved review checkpoints, separate from financial receipts and account activity. */
final class ReceiptDraftFile {
    record Checkpoint(UUID id,ReceiptSnapshot snapshot,ReceiptMeta metadata,String actor,
            boolean imported,List<ReceiptImportDraft.ReviewIssue> issues,List<ReceiptEvidence> evidence,
            int entryStage,boolean partial,boolean taxKnown,boolean totalKnown,Set<UUID> deferredEarnings) implements Serializable {
        Checkpoint(UUID id,ReceiptSnapshot snapshot,ReceiptMeta metadata,String actor,boolean imported,List<ReceiptImportDraft.ReviewIssue> issues,List<ReceiptEvidence> evidence) {
            this(id,snapshot,metadata,actor,imported,issues,evidence,8,false,true,true,Set.of());
        }
        Checkpoint {issues=List.copyOf(issues);evidence=List.copyOf(evidence);deferredEarnings=deferredEarnings==null?Set.of():Set.copyOf(deferredEarnings);}
    }
    private static final String VERSION="receipt-review-draft-v1";
    static Path folder(Path receipts){return receipts.toAbsolutePath().resolveSibling(receipts.getFileName()+".drafts");}
    static void write(Path folder,Checkpoint draft) throws IOException {
        Files.createDirectories(folder);Path temporary=Files.createTempFile(folder,"draft-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE);
                    var out=LegacyObjectStreams.output(Channels.newOutputStream(channel))) {
                out.writeUTF(VERSION);out.writeObject(draft);out.flush();channel.force(true);
            }
            Files.move(temporary,folder.resolve(draft.id()+".draft"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }
    static Checkpoint read(Path path) throws IOException {
        try(var in=LegacyObjectStreams.input(Files.newInputStream(path))) {
            in.setObjectInputFilter(info->{
                if(info.depth()>100 || info.references()>2_000_000 || info.arrayLength()>100_000_000)return ObjectInputFilter.Status.REJECTED;
                Class<?> type=info.serialClass();if(type==null)return ObjectInputFilter.Status.UNDECIDED;
                while(type.isArray())type=type.getComponentType();String name=type.getName();
                return type.isPrimitive() || name.startsWith("coupons.") || name.startsWith("java.lang.") || name.startsWith("java.util.") || name.startsWith("java.math.") || name.startsWith("java.time.")?ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED;
            });
            if(!VERSION.equals(in.readUTF()))throw new IOException("Unsupported draft format.");
            if(!(in.readObject() instanceof Checkpoint checkpoint))throw new IOException("Invalid draft.");
            return checkpoint;
        } catch(ClassNotFoundException | RuntimeException invalid){throw new IOException("Draft could not be reopened; original retained.");}
    }
    static void remove(Path folder,UUID id) throws IOException {Files.deleteIfExists(folder.resolve(id+".draft"));}
}
