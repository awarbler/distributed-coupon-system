package coupons.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static coupons.repository.PostgresRepositorySupport.*;
import static coupons.repository.PostgresReceiptState.execute;

/** Explicit, all-or-nothing import from a separate local copy into an empty database/schema. */
public final class LocalToPostgresMigration {
    private LocalToPostgresMigration() {}
    private record FileInfo(String path,String sha256,String modified) implements java.io.Serializable {}
    public record Report(String sourceDigest,int receipts,int historicalRevisions,int evidence,
            int rewards,int redemptions,int promotionPrograms,int rrMatches,int rewardRemovals,
            int accountOpenings,int accountAdjustments,int providerHistoryEntries,int archivedFiles,
            List<String> archiveOnlyFiles,boolean alreadyImported) {
        public Report {archiveOnlyFiles=List.copyOf(archiveOnlyFiles);}
    }
    public static Report inspectCopy(Path copy,Path protectedSource)throws IOException {
        return report(LocalMigrationSource.read(copy,protectedSource),false);
    }
    public static Report migrateCopy(Path copy,Path protectedSource,PostgresConnectionFactory target,
            String expectedDatabase)throws IOException,SQLException {
        return migrateCopy(copy, protectedSource, target, expectedDatabase, null);
    }
    /** Experimental import into verified disposable storage only. */
    public static Report migrateCopyWithFinancials(Path copy, Path protectedSource, PostgresConnectionFactory target,
            DisposablePostgresTarget disposable) throws Exception {
        ReceiptFinancialProjection.install(target, disposable);
        return migrateCopy(copy, protectedSource, target, "distributed_coupons_test", disposable);
    }
    private static Report migrateCopy(Path copy,Path protectedSource,PostgresConnectionFactory target,
            String expectedDatabase, DisposablePostgresTarget disposable)throws IOException,SQLException {
        var source=LocalMigrationSource.read(copy,protectedSource);
        Objects.requireNonNull(target);
        if(expectedDatabase==null||expectedDatabase.isBlank())throw new IllegalArgumentException("Explicit expected database name is required.");
        try(Connection c=disposable == null ? target.open() : disposable.open(target)) {
            c.setAutoCommit(false);
            try {
                checkDatabase(c,expectedDatabase);
                if (disposable != null) ReceiptFinancialProjection.verifyInstalled(c, disposable);
                PostgresReceiptState.lock(c);
                PostgresSchema.apply(c); // PostgreSQL DDL and import roll back together on failure.
                // Exclude concurrent API, ledger, plan and coupon writes, not only receipt writes.
                lockTables(c);
                if(alreadyImported(c,source)) {
                    validateArchive(c,source);source.verifyUnchanged();
                    ReceiptFinancialProjection.appendAll(c, disposable);
                    c.commit();return report(source,true);
                }
                requireEmpty(c, disposable);
                PostgresReceiptState.insert(c,source.state);
                execute(c,"INSERT INTO local_migrations(source_digest,checkpoint,manifest) VALUES (?,?,?)",source.digest,source.checkpoint,serialize(manifest(source)));
                for(var file:source.files)execute(c,"INSERT INTO local_migration_files(source_digest,relative_path,sha256,content) VALUES (?,?,?,?)",source.digest,file.path(),file.sha256(),file.bytes());
                validateRecords(c,source);validateArchive(c,source);source.verifyUnchanged();
                ReceiptFinancialProjection.appendAll(c, disposable);
                c.commit();return report(source,false);
            }catch(Exception e) {
                try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}
                if(e instanceof IOException io)throw io;if(e instanceof SQLException sql)throw sql;
                if(e instanceof RuntimeException runtime)throw runtime;throw new IllegalStateException(e);
            }
        }
    }
    /** No writes. Compares current database records and original file archive against the copy. */
    public static Report validateCopy(Path copy,Path protectedSource,PostgresConnectionFactory target,
            String expectedDatabase)throws IOException,SQLException {
        var source=LocalMigrationSource.read(copy,protectedSource);
        try(Connection c=target.open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);c.setAutoCommit(false);
            try {checkDatabase(c,expectedDatabase);validateRecords(c,source);validateArchive(c,source);source.verifyUnchanged();c.rollback();return report(source,true);}
            finally{c.rollback();}
        }
    }
    private static void checkDatabase(Connection c,String expected)throws SQLException {
        if(expected==null||expected.isBlank())throw new IllegalArgumentException("Expected database name is required.");
        try(var p=c.prepareStatement("SELECT current_database()");var r=p.executeQuery()) {
            r.next();if(!expected.equals(r.getString(1)))throw new IllegalArgumentException("Connected database does not match the explicitly selected destination.");
        }
    }
    private static List<String> tables(Connection c)throws SQLException {
        List<String> tables=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=current_schema() ORDER BY tablename");var r=p.executeQuery()){while(r.next())tables.add(r.getString(1));}return tables;
    }
    private static String identifier(String name){return "\""+name.replace("\"","\"\"")+"\"";}
    private static void lockTables(Connection c)throws SQLException {
        List<String> tables=tables(c);if(tables.isEmpty())throw new IllegalStateException("No migration schema is selected.");
        try(var p=c.createStatement()){p.execute("LOCK TABLE "+String.join(",",tables.stream().map(LocalToPostgresMigration::identifier).toList())+" IN EXCLUSIVE MODE");}
    }
    /** Count user state while allowing only the exact schema-created defaults. */
    static long destinationRows(Connection c, String table) throws SQLException {
        if (table.equals("schema_version")) return 0;
        String filter = table.equals("inventory_receipt_settings")
                ? " WHERE NOT (singleton = true AND enabled = false)"
                : table.equals("receipt_financial_projection_config")
                ? " WHERE NOT (singleton = true AND marker = 'APPLICATION' AND projection_version = 1)" : "";
        try (var statement = c.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM " + identifier(table) + filter)) {
            rows.next();
            return rows.getLong(1);
        }
    }
    private static void requireEmpty(Connection c, DisposablePostgresTarget disposable)throws SQLException {
        for(String table:tables(c)) {
            if (disposable != null && table.equals("receipt_financial_projection_config")) continue;
            if(destinationRows(c, table) > 0) {
                throw new IllegalStateException("Destination must be empty; refusing to merge or overwrite table " + table + ".");
            }
        }
    }
    private static boolean alreadyImported(Connection c,LocalMigrationSource source)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM local_migrations WHERE source_digest=?")){p.setString(1,source.digest);try(var r=p.executeQuery()){return r.next();}}
    }
    private static List<FileInfo> manifest(LocalMigrationSource s){return s.files.stream().map(f->new FileInfo(f.path(),f.sha256(),f.modified())).toList();}
    private static void validateRecords(Connection c,LocalMigrationSource source)throws SQLException {
        PostgresReceiptState.compare(source.state,PostgresReceiptState.read(c));
        PostgresReceiptState.validateProjections(c,source.state);
        for(var e:source.state.evidence().values()) {
            try(var p=c.prepareStatement("SELECT content FROM receipt_evidence WHERE evidence_id=?")) {
                p.setObject(1,e.getEvidenceId());try(var r=p.executeQuery()){if(!r.next()||!LocalMigrationSource.hash(e.getContent()).equals(LocalMigrationSource.hash(r.getBytes(1))))throw new IllegalStateException("Evidence hash validation failed.");}
            }
        }
    }
    private static void validateArchive(Connection c,LocalMigrationSource source)throws SQLException {
        try(var p=c.prepareStatement("SELECT checkpoint,manifest FROM local_migrations WHERE source_digest=?")) {
            p.setString(1,source.digest);try(var r=p.executeQuery()){
                if(!r.next()||!Arrays.equals(source.checkpoint,r.getBytes(1))||!manifest(source).equals(deserialize(r.getBytes(2),List.class)))throw new IllegalStateException("Source checkpoint/manifest archive differs.");
            }
        }
        Map<String,LocalMigrationSource.FileData> files=new HashMap<>();source.files.forEach(f->files.put(f.path(),f));
        try(var p=c.prepareStatement("SELECT relative_path,sha256,content FROM local_migration_files WHERE source_digest=?")) {
            p.setString(1,source.digest);try(var r=p.executeQuery()) {
                while(r.next()) {
                    var file=files.remove(r.getString(1));
                    if(file==null||!file.sha256().equals(r.getString(2))||!file.sha256().equals(LocalMigrationSource.hash(r.getBytes(3)))||!Arrays.equals(file.bytes(),r.getBytes(3)))throw new IllegalStateException("Source-file archive hash validation failed.");
                }
            }
        }
        if(!files.isEmpty())throw new IllegalStateException("Missing archived source files.");
    }
    private static Report report(LocalMigrationSource source,boolean replay) {
        var s=source.state;
        return new Report(source.digest,s.receipts().size(),s.history().size(),s.evidence().size(),s.rewards().size(),s.redemptions().size(),s.giftCardPromotions().size(),s.rrUsageLinks().size(),s.rewardVoids().size(),s.balances().openings().size(),s.balances().adjustments().size(),s.balances().redemptionHistory().size(),source.files.size(),source.files.stream().map(LocalMigrationSource.FileData::path).filter(p->!p.equals("receipts.bin")).toList(),replay);
    }
    /** Default is an offline inspection. Database access needs explicit execute/validate and migration-only configuration. */
    public static void main(String[] args)throws Exception {
        Map<String,String> flags=new HashMap<>();boolean execute=false,validate=false;
        for(int i=0;i<args.length;i++) {
            if(args[i].equals("--execute")){execute=true;continue;}if(args[i].equals("--validate")){validate=true;continue;}
            if(!Set.of("--source-copy","--protected-source","--expected-database").contains(args[i])||i+1>=args.length)throw new IllegalArgumentException("Use --source-copy PATH --protected-source ORIGINAL [--expected-database NAME --execute|--validate]. Default: offline inspection.");
            if(flags.put(args[i],args[++i])!=null)throw new IllegalArgumentException("Duplicate option.");
        }
        if(execute&&validate)throw new IllegalArgumentException("Select either execute or validate.");
        if(!flags.containsKey("--source-copy")||!flags.containsKey("--protected-source"))throw new IllegalArgumentException("Both source-copy and protected-source are required.");
        Path source=Path.of(flags.get("--source-copy")),original=Path.of(flags.get("--protected-source"));
        Report result;
        if(execute||validate) {
            var target=new PostgresConnectionFactory(System.getenv("COUPON_MIGRATION_URL"),System.getenv("COUPON_MIGRATION_USER"),System.getenv("COUPON_MIGRATION_PASSWORD"));
            result=execute?migrateCopy(source,original,target,flags.get("--expected-database")):validateCopy(source,original,target,flags.get("--expected-database"));
        } else result=inspectCopy(source,original);
        System.out.println(result);
        System.out.println("Files other than receipts.bin are preserved verbatim as archives; unfinished drafts/import manifests are not converted into committed receipts.");
        System.out.println("Provider-redemption commit timestamps absent from the local format remain NULL; their original event dates/times and sequence are preserved.");
    }
}
