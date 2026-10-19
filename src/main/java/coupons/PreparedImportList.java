package coupons;

import coupons.repository.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

/** List visibility is separate from original evidence and saved financial records. */
final class PreparedImportList {
    private PreparedImportList() {}
    static boolean visible(Path manifest, InMemoryTransactionRepository repository) {
        if(Files.exists(marker(manifest)))return false;
        try {
            var fields=new Properties();
            try(var reader=Files.newBufferedReader(manifest)){fields.load(reader);}
            String number=fields.getProperty("receipt.number"), date=fields.getProperty("receipt.date");
            if(number==null || number.isBlank())return true;
            return repository.creationHistory().stream().noneMatch(event->{
                var snapshot=event.after();
                var r=repository.findById(snapshot.transactionId()).or(()->repository.findDeletedById(snapshot.transactionId()));
                return r.isPresent() && r.get().metadata()!=null && number.equals(r.get().metadata().receiptNumber())
                        && Objects.equals(date,Objects.toString(r.get().transaction().getObservations().purchaseDate(),null));
            });
        } catch(IOException | IllegalArgumentException invalid){return true;}
    }
    static void remove(Path manifest) throws IOException {
        Files.writeString(marker(manifest),"Removed from import list by local owner. Original extraction and evidence retained.\n");
    }
    private static Path marker(Path manifest){return manifest.resolveSibling(manifest.getFileName()+".hidden");}
}
