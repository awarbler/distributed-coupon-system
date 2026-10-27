package coupons.repository;

import coupons.*;
import coupons.ReceiptObservations.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Only generated source copies and a private schema in a disposable test database. */
class LocalToPostgresMigrationTest {
    @TempDir Path temp;
    static final Instant NOW=Instant.parse("2026-10-04T14:53:00.123456789Z");
    static final LocalDate DAY=LocalDate.of(2026,10,4);
    PostgresConnectionFactory admin,db;String schema,database;
    Path copy,original;
    InMemoryStore.State source;
    UUID id=UUID.randomUUID(),other=UUID.randomUUID(),promotion=UUID.randomUUID(),line;
    RegisterReward redeemed,matched,removed;
    ReceiptWriteRequest create=request(),update=request(),delete=request(),restore=request();
    ReceiptCreateCtx creator=new ReceiptCreateCtx("original-owner");
    ReceiptChangeContext approval=new ReceiptChangeContext("original-owner","original correction","original-owner",NOW);
    ReceiptMeta metadata=new ReceiptMeta("Walgreens","0000123",LocalDateTime.of(2026,10,4,9,53,0,123456789),"0001");
    List<ReceiptEvidence> createEvidence;
    static BigDecimal n(String s){return new BigDecimal(s);}
    static ReceiptWriteRequest request(){return new ReceiptWriteRequest(UUID.randomUUID(),NOW);}
    @BeforeEach void setup()throws Exception {
        String url=System.getenv("COUPON_POSTGRES_TEST_URL");
        Assumptions.assumeTrue(url!=null&&System.getenv("COUPON_POSTGRES_TEST_USER")!=null&&System.getenv("COUPON_POSTGRES_TEST_PASSWORD")!=null);
        admin=new PostgresConnectionFactory(url,System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        String candidate="migration_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=PostgresTestDatabase.targetForSchema(candidate).open(admin)) {
            try(var r=c.createStatement().executeQuery("SELECT current_database()")) {
                r.next(); database=r.getString(1);
                assertEquals("distributed_coupons_test", database, "Refusing a non-test database before schema changes");
            }
            schema=candidate;
            c.createStatement().execute("CREATE SCHEMA "+schema);
        }
        db=new PostgresConnectionFactory(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
        try(var c=PostgresTestDatabase.targetForSchema(schema).open(db)){PostgresTestDatabase.targetForSchema(schema).verifySchema(c);PostgresSchema.apply(c);}
        fixture();
    }
    @AfterEach void cleanup()throws Exception {if(admin!=null&&schema!=null)try(var c=PostgresTestDatabase.targetForSchema(schema).open(admin)){c.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");}}
    void fixture()throws Exception {
        original=Files.createDirectory(temp.resolve("protected-original"));Files.writeString(original.resolve("sentinel"),"do not touch");
        copy=Files.createDirectory(temp.resolve("independent-copy"));
        var store=new InMemoryStore();var repo=new InMemoryTransactionRepository(store,Clock.fixed(NOW,ZoneOffset.UTC));
        redeemed=new RegisterReward("Prior RR",n("2.000"),DAY.minusDays(1),null,null);
        matched=new RegisterReward("Matched RR",n("3.00"),DAY.minusDays(1),null,null);
        removed=new RegisterReward("Misentered",n("1.00"),null,null,null);
        for(var r:List.of(redeemed,matched,removed))repo.issuedRewards().save(r);
        repo.voidRecordedReward(removed.getRewardId(),"original removal","original-owner");
        repo.saveGiftCardPromotion(new GiftCardPromotion(promotion,"Original P&G",n("75.000"),n("25.00"),DAY,DAY.plusDays(27),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,"original terms",GiftCardPromotion.Status.TRACKING,Map.of()));
        var tx=new CouponTransaction(new ReceiptTotals(n("0.2700"),n("8.2800")));
        var item=new PurchaseItem("Charmin","000012345",n("10.0000"),n("10.000"),1);tx.addPurchaseItem(item);line=item.getLineItemId();
        tx.addRegisterRewardRedemption(new RegisterRewardRedemption(redeemed.getRewardId(),id,n("2.000"),DAY));
        tx.addPayment(new Payment(PaymentType.CASH,n("8.2800")));
        tx.setObservations(new ReceiptObservations(DAY,"9:53 AM",List.of(),List.of(),new WalgreensCashAccount(n("100.000"),n("0.00"),n("0.0300"),n("100.0300")),List.of(),Map.of(),null,List.of(new ReceiptPromotionLink(promotion,List.of(line),n("10.000"),"original eligibility",NOW))));
        createEvidence=List.of(new ReceiptEvidence(UUID.randomUUID(),id,"image/jpeg",new byte[]{0,1,2,-1}),new ReceiptEvidence(UUID.randomUUID(),id,"text/plain",new byte[]{4,5}));
        repo.save(id,tx,metadata,createEvidence,create,creator);
        repo.update(id,1,tx,new ReceiptMeta("Walgreens","0000123",metadata.purchasedAt(),"corrected"),List.of(new ReceiptEvidence(UUID.randomUUID(),id,"application/pdf",new byte[]{6,7})),update,approval);
        repo.softDelete(id,2,delete,approval);repo.restore(id,3,restore,approval);
        var spend=new CouponTransaction(new ReceiptTotals(n("0.00"),n("0.00")));
        spend.addPurchaseItem(new PurchaseItem("Soap",null,n("3.00"),n("3.00"),1));
        var deduction=new UnidentifiedRR(UUID.randomUUID(),n("3.00"),"Previously used");
        spend.setObservations(new ReceiptObservations(DAY,"10:00 AM",List.of(),List.of(deduction),null,List.of(),Map.of()));
        repo.save(other,spend,metadata,List.of(),request(),creator);
        repo.linkRRUsage(new RRUsageLink(matched.getRewardId(),false,deduction.eventId(),other,n("3.00"),"original reason","original confirmer",NOW));
        repo.softDelete(other,1,request(),approval); // reservation must survive deletion
        repo.recordAccountOpening(new AccountOpening(UUID.randomUUID(),DAY.withDayOfMonth(1),"Fetch",n("33660"),n("33.5900"),true,"original valuation","original statement",NOW));
        repo.recordAccountOpening(new AccountOpening(UUID.randomUUID(),DAY,"Fetch",n("33735"),n("33.6700"),false,"original valuation","day opening",NOW.plusNanos(1)));
        repo.recordAccountAdjustment(new AccountAdjustment(UUID.randomUUID(),DAY,"Fetch",n("25"),n("0.0250"),"original adjustment","original actor",NOW.plusNanos(2)));
        var transfer=new ProviderRedemption(UUID.randomUUID(),"Fetch",DAY,LocalTime.of(10,1,0,123456789),n("1000"),n("1.0000"),ProviderRedemption.Destination.GIFT_CARD,"Gift Cards",n("1.0000"),ProviderRedemption.Status.REQUESTED,null,null,List.of(),"original-ref","original note");
        repo.recordProviderRedemption(transfer,null);repo.recordProviderRedemption(transfer.complete(ProviderRedemption.Status.RECEIVED,DAY,LocalTime.of(10,2,0,987654321)),null);
        source=store.state;
        writeSource(source);
        Files.createDirectories(copy.resolve("imports"));Files.writeString(copy.resolve("imports/uncompleted.properties"),"unfinished, not a committed receipt");
        Files.write(copy.resolve("unfinished.draft"),new byte[]{8,9,10});
    }
    void writeSource(InMemoryStore.State state)throws Exception {try(var file=new LocalReceiptFile(copy.resolve("receipts.bin"))){file.save(state);}}
    long count(String table)throws Exception {try(var c=db.open();var r=c.createStatement().executeQuery("SELECT count(*) FROM "+table)){r.next();return r.getLong(1);}}
    LocalToPostgresMigration.Report migrate()throws Exception{return LocalToPostgresMigration.migrateCopy(copy,original,db,database);}
    @Test void preservesEveryRecordOriginalEvidenceFinancialValuesAndArchiveBytes()throws Exception {
        byte[] checkpoint=Files.readAllBytes(copy.resolve("receipts.bin"));
        var report=migrate();assertFalse(report.alreadyImported());assertEquals(2,report.receipts());assertEquals(6,report.historicalRevisions());assertEquals(3,report.evidence());assertEquals(1,report.rrMatches());assertEquals(1,report.rewardRemovals());assertEquals(2,report.providerHistoryEntries());assertTrue(report.archiveOnlyFiles().contains("unfinished.draft"));
        try(var c=db.open()){PostgresReceiptState.compare(source,PostgresReceiptState.read(c));}
        assertEquals(report.sourceDigest(),LocalToPostgresMigration.validateCopy(copy,original,db,database).sourceDigest());
        var pg=new PostgresTransactionRepository(db);
        assertEquals(source.receipts().get(id),ReceiptRevSnap.from(pg.findById(id).orElseThrow()));
        assertEquals(source.receipts().get(other),ReceiptRevSnap.from(pg.findDeletedById(other).orElseThrow()));
        for(var h:source.history().values())assertEquals(h,pg.findHistoryById(h.historyId()).orElseThrow());
        var migrated=pg.findById(id).orElseThrow().transaction();
        assertEquals(n("8.2800"),migrated.getReceiptTotals().getFinalTotal());
        assertEquals(0,n("8.27").compareTo(new TransactionCalculator().calculateExpectedCheckoutAmount(migrated)));
        assertEquals(0,n("0.01").compareTo(new TransactionCalculator().calculateReceiptDifference(migrated)));
        var accounts=new PostgresProviderAccountRepository(db);assertEquals(source.balances(),accounts.balanceState("Fetch"));
        assertArrayEquals(checkpoint,Files.readAllBytes(copy.resolve("receipts.bin")));assertEquals("do not touch",Files.readString(original.resolve("sentinel")));
        try(var c=db.open();var r=c.createStatement().executeQuery("SELECT recorded_at FROM provider_redemptions_history")){while(r.next())assertNull(r.getObject(1));}
    }
    @Test void preservesLateRetriesAndRejectsConflictingReuseWithoutAnyNewHistory()throws Exception {
        migrate();var pg=new PostgresTransactionRepository(db);
        for(var h:source.history().values()) {
            var w=source.requests().get(h.request().requestId());ReceiptWriteResult result;
            if(w.input() instanceof InMemoryStore.CreateInput i)result=pg.save(i.transactionId(),i.contents().toTransaction(),i.metadata(),i.evidence(),i.request(),i.context());
            else if(w.input() instanceof InMemoryStore.UpdateInput i)result=pg.update(i.transactionId(),i.expectedRevision(),i.contents().toTransaction(),i.metadata(),i.evidence(),i.request(),i.context());
            else if(w.input() instanceof InMemoryStore.DeleteInput i)result=pg.softDelete(i.transactionId(),i.expectedRevision(),i.request(),i.context());
            else {var i=(InMemoryStore.RestoreInput)w.input();result=pg.restore(i.transactionId(),i.expectedRevision(),i.request(),i.context());}
            assertEquals(w.result(),result);
        }
        var input=(InMemoryStore.CreateInput)source.requests().get(create.requestId()).input();
        assertThrows(IllegalArgumentException.class,()->pg.save(id,input.contents().toTransaction(),null,input.evidence(),create,creator));
        assertEquals(6,count("receipt_history"));
        var after=pg.update(id,4,pg.findById(id).orElseThrow().transaction(),metadata,List.of(),request(),approval);
        assertEquals(5,after.revision());assertTrue(migrate().alreadyImported());assertEquals(7,count("receipt_history"));
        assertThrows(IllegalStateException.class,()->LocalToPostgresMigration.validateCopy(copy,original,db,database));
    }
    @Test void preservesConsumedAndRemovedRewardReservationsAfterDeleteAndRestore()throws Exception {
        migrate();var pg=new PostgresTransactionRepository(db);var extras=new PostgresReceiptSupplementRepository(db);
        assertEquals(source.rrUsageLinks().values().stream().toList(),extras.rrUsageLinks());assertEquals(source.rewardVoids().values().stream().toList(),extras.rewardRemovalHistory());
        for(var reward:List.of(redeemed,matched,removed)) {
            UUID next=UUID.randomUUID();var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.ZERO));tx.addRegisterRewardRedemption(new RegisterRewardRedemption(reward.getRewardId(),next,reward.getFaceValue(),DAY));
            assertThrows(IllegalArgumentException.class,()->pg.save(next,tx,null,List.of(),request(),creator));
        }
        pg.restore(other,2,request(),approval);assertFalse(extras.rrUsageNeedsReview(extras.rrUsageLinks().get(0)));
        pg.softDelete(other,3,request(),approval);assertEquals(1,extras.rrUsageLinks().size());
        assertEquals(source.rewardVoids().get(removed.getRewardId()),extras.voidRecordedReward(removed.getRewardId(),"ignored retry","owner"));
    }
    @Test void promotionProgressPersistsAndReceiptChangesAreAtomic()throws Exception {
        migrate();var pg=new PostgresTransactionRepository(db);var extras=new PostgresReceiptSupplementRepository(db);
        assertEquals(source.giftCardPromotions().get(promotion),extras.giftCardPromotions().get(0));
        UUID second=UUID.randomUUID();extras.saveGiftCardPromotion(new GiftCardPromotion(second,"Second",n("50"),n("15"),DAY,DAY.plusDays(27),GiftCardPromotion.Basis.REVIEWED_ELIGIBLE_AMOUNT,"terms",GiftCardPromotion.Status.TRACKING,Map.of()));
        var tx=pg.findById(id).orElseThrow().transaction();var o=tx.getObservations();
        tx.setObservations(new ReceiptObservations(o.purchaseDate(),o.printedTime(),o.earnings(),o.unidentifiedRR(),o.walgreensCash(),o.savings(),o.discountKinds(),o.receiptSavingValuePercent(),List.of(new ReceiptPromotionLink(second,List.of(line),n("9.00"),"reviewed",NOW)),o.worksheet(),o.footerStatus()));
        pg.update(id,4,tx,metadata,List.of(),request(),approval);
        var programs=extras.giftCardPromotions();assertTrue(programs.stream().filter(p->p.id().equals(promotion)).findFirst().orElseThrow().contributions().isEmpty());
        assertEquals(n("9.00"),programs.stream().filter(p->p.id().equals(second)).findFirst().orElseThrow().contributions().get(id).reviewedAmount());
        var bad=pg.findById(id).orElseThrow().transaction();var b=bad.getObservations();
        bad.setObservations(new ReceiptObservations(b.purchaseDate(),b.printedTime(),b.earnings(),b.unidentifiedRR(),b.walgreensCash(),b.savings(),b.discountKinds(),null,List.of(new ReceiptPromotionLink(UUID.randomUUID(),List.of(line),n("9"),"missing offer",NOW))));
        assertThrows(IllegalArgumentException.class,()->pg.update(id,5,bad,metadata,List.of(),request(),approval));assertEquals(programs,extras.giftCardPromotions());assertEquals(5,pg.findById(id).orElseThrow().revision());
    }
    @Test void rejectsOriginalSymlinkAndHardlinkedCopiesWithoutDatabaseWrites()throws Exception {
        assertDoesNotThrow(()->LocalToPostgresMigration.main(new String[]{"--source-copy",copy.toString(),"--protected-source",original.toString()}));
        assertEquals(0,count("receipt_current"));
        assertThrows(IllegalArgumentException.class,()->LocalToPostgresMigration.inspectCopy(original,original));
        Path alias=temp.resolve("alias");Files.createSymbolicLink(alias,original);
        assertThrows(IllegalArgumentException.class,()->LocalToPostgresMigration.inspectCopy(alias,original));
        Files.createLink(copy.resolve("hard-link"),copy.resolve("receipts.bin"));
        assertThrows(IllegalArgumentException.class,()->LocalToPostgresMigration.inspectCopy(copy,original));assertEquals(0,count("receipt_current"));
    }
    @Test void rejectsIncompleteHistoryWrongDatabaseAndNonemptyDestination()throws Exception {
        assertThrows(IllegalArgumentException.class,()->LocalToPostgresMigration.migrateCopy(copy,original,db,"different_database"));
        var history=new HashMap<>(source.history());history.remove(history.keySet().iterator().next());
        writeSource(new InMemoryStore.State(source.receipts(),source.rewards(),source.redemptions(),source.evidence(),history,source.requests(),source.rewardVoids(),source.giftCardPromotions(),source.balances(),source.rrUsageLinks()));
        assertThrows(IllegalArgumentException.class,()->migrate());assertEquals(0,count("receipt_current"));
        writeSource(source);new PostgresShoppingPlanRepository(db).save(new ShoppingPlan(UUID.randomUUID(),"Existing",null,null,List.of(),1),"owner");
        assertThrows(IllegalStateException.class,()->migrate());assertEquals(0,count("receipt_current"));assertEquals(1,count("shopping_plan_current"));
    }
    @Test void lateWriteFailureAndEvidenceMismatchRollBackEverything()throws Exception {
        try(var c=db.open()) {
            c.createStatement().execute("CREATE FUNCTION reject_archive() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected archive failure'; END $$");
            c.createStatement().execute("CREATE TRIGGER reject_archive BEFORE INSERT ON local_migration_files FOR EACH ROW EXECUTE FUNCTION reject_archive()");
        }
        assertThrows(SQLException.class,()->migrate());assertEquals(0,count("receipt_current"));assertEquals(0,count("local_migrations"));assertEquals(0,count("provider_account_openings"));
        try(var c=db.open()) {
            c.createStatement().execute("DROP TRIGGER reject_archive ON local_migration_files");
            c.createStatement().execute("CREATE FUNCTION corrupt_evidence() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.content=decode('00','hex'); RETURN NEW; END $$");
            c.createStatement().execute("CREATE TRIGGER corrupt_evidence BEFORE INSERT ON receipt_evidence FOR EACH ROW EXECUTE FUNCTION corrupt_evidence()");
        }
        assertThrows(IllegalStateException.class,()->migrate());assertEquals(0,count("receipt_current"));assertEquals(0,count("receipt_evidence"));assertEquals(0,count("rr_usage_links"));
        try(var c=db.open()) {
            c.createStatement().execute("DROP TRIGGER corrupt_evidence ON receipt_evidence");
            c.createStatement().execute("CREATE FUNCTION corrupt_projection() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.status='CORRUPTED'; RETURN NEW; END $$");
            c.createStatement().execute("CREATE TRIGGER corrupt_projection BEFORE INSERT OR UPDATE ON provider_redemptions_current FOR EACH ROW EXECUTE FUNCTION corrupt_projection()");
        }
        assertThrows(IllegalStateException.class,()->migrate());assertEquals(0,count("receipt_current"));assertEquals(0,count("provider_redemptions_current"));
    }
    @Test void concurrentMigrationRetriesImportExactlyOnce()throws Exception {
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<LocalToPostgresMigration.Report> work=()->{start.await();return migrate();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            var one=a.get(20,java.util.concurrent.TimeUnit.SECONDS);var two=b.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(one.alreadyImported(),two.alreadyImported());assertEquals(one.sourceDigest(),two.sourceDigest());
            assertEquals(2,count("receipt_current"));assertEquals(6,count("receipt_history"));assertEquals(1,count("local_migrations"));
        }
    }
    @Test void v5UpgradeAndImportRollBackTogetherAndCanBeRetried()throws Exception {
        // Recreate the user's schema level using only this isolated test schema.
        try(var c=db.open()) {
            c.createStatement().execute("DROP TABLE local_migration_files,local_migrations,reward_removals,rr_usage_links,gift_card_promotions");
            for(String table:List.of("provider_account_openings","provider_account_adjustments","provider_redemptions_history"))c.createStatement().execute("ALTER TABLE "+table+" DROP COLUMN source_sequence");
            c.createStatement().execute("DELETE FROM schema_version WHERE version=6");
            c.createStatement().execute("CREATE FUNCTION fail_evidence() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
            c.createStatement().execute("CREATE TRIGGER fail_evidence BEFORE INSERT ON receipt_evidence FOR EACH ROW EXECUTE FUNCTION fail_evidence()");
        }
        assertThrows(SQLException.class,()->migrate());assertEquals(0,count("receipt_current"));
        try(var c=db.open();var r=c.createStatement().executeQuery("SELECT to_regclass('local_migrations'), (SELECT count(*) FROM schema_version WHERE version=6)")){r.next();assertNull(r.getObject(1));assertEquals(0,r.getLong(2));}
        try(var c=db.open()){c.createStatement().execute("DROP TRIGGER fail_evidence ON receipt_evidence");}
        assertEquals(2,migrate().receipts());assertEquals(6,count("receipt_history"));
        assertDoesNotThrow(()->LocalToPostgresMigration.validateCopy(copy,original,db,database));
    }


    @Test void previewCountsSourceWithoutWritingDestinationOrCopy()throws Exception {
        byte[] before=Files.readAllBytes(copy.resolve("receipts.bin"));
        var preview=LocalMigrationPreview.inspect(copy,original,db,database);
        var receipts=preview.categories().stream().filter(r->r.name().equals("Receipts (active and deleted)")).findFirst().orElseThrow();
        assertEquals(2,receipts.found());assertEquals(0,receipts.destination());assertEquals(2,receipts.newCandidates());
        assertEquals(0,count("receipt_current"));assertEquals(0,count("local_migrations"));assertEquals(0,count("local_migration_files"));
        assertArrayEquals(before,Files.readAllBytes(copy.resolve("receipts.bin")));
        assertEquals("do not touch",Files.readString(original.resolve("sentinel")));
        assertTrue(preview.duplicates().stream().anyMatch(v->v.contains("printed-receipt")));
        assertTrue(preview.importDecision().startsWith("BLOCKED"));
        assertTrue(preview.financialValues().stream().anyMatch(v->v.contains("reported due=8.2800")&&v.contains("calculated due=8.27")));
        assertEquals(3,preview.evidenceHashes().size());
        for(String category:List.of("Shopping plans","Clipped coupons","Inventory balances","Rebate claims"))
            assertEquals(0,preview.categories().stream().filter(v->v.name().equals(category)).findFirst().orElseThrow().found());
    }
    @Test void previewRecognizesExactMigrationAndDoesNotDuplicateIt()throws Exception {
        migrate();long history=count("receipt_history");
        var preview=LocalMigrationPreview.inspect(copy,original,db,database);
        for(var row:preview.categories())assertEquals(0,row.conflicts(),row.name());
        var receipts=preview.categories().getFirst();assertEquals(2,receipts.identical());assertEquals(0,receipts.newCandidates());
        assertEquals(history,count("receipt_history"));assertEquals(1,count("local_migrations"));
    }
    @Test void previewReportsNewerRevisionAsConflictWithoutOverwritingIt()throws Exception {
        migrate();var pg=new PostgresTransactionRepository(db);
        pg.update(id,4,pg.findById(id).orElseThrow().transaction(),new ReceiptMeta("Walgreens","changed",metadata.purchasedAt(),null),List.of(),request(),approval);
        var preview=LocalMigrationPreview.inspect(copy,original,db,database);
        assertEquals(1,preview.categories().getFirst().conflicts());assertTrue(preview.importDecision().startsWith("BLOCKED"));
        assertEquals(5,pg.findById(id).orElseThrow().revision());assertEquals(7,count("receipt_history"));
    }
    @Test void previewRefusesWrongDatabaseAndOriginalSource()throws Exception {
        assertThrows(IllegalArgumentException.class,()->LocalMigrationPreview.inspect(copy,original,db,"not_the_selected_database"));
        assertThrows(IllegalArgumentException.class,()->LocalMigrationPreview.inspect(original,original,db,database));
        assertEquals(0,count("receipt_current"));
    }

    void distinguishCurrentReceiptMetadata()throws Exception {
        var store=new InMemoryStore(null,source);var repo=new InMemoryTransactionRepository(store,Clock.fixed(NOW,ZoneOffset.UTC));
        repo.update(id,4,source.receipts().get(id).contents().toTransaction(),new ReceiptMeta("Walgreens","unique-source-receipt",metadata.purchasedAt(),null),List.of(),request(),approval);
        source=store.state;writeSource(source);
    }
    @Test void enabledInventorySettingIsUserStateAndCannotBeOverwritten() throws Exception {
        distinguishCurrentReceiptMetadata();
        try (var c = db.open()) {
            c.createStatement().executeUpdate("UPDATE inventory_receipt_settings SET enabled=true WHERE singleton=true");
        }
        assertTrue(LocalMigrationPreview.inspect(copy, original, db, database).importDecision().contains("destination is populated"));
        assertThrows(IllegalStateException.class, () -> migrate());
        assertEquals(0, count("receipt_current"));
        try (var c = db.open(); var rows = c.createStatement().executeQuery("SELECT enabled FROM inventory_receipt_settings")) {
            assertTrue(rows.next()); assertTrue(rows.getBoolean(1));
        }
    }

    @Test void previewDistinguishesEmptyDestinationFromUnsupportedMerge()throws Exception {
        distinguishCurrentReceiptMetadata();
        assertTrue(LocalMigrationPreview.inspect(copy,original,db,database).importDecision().startsWith("ELIGIBLE FOR APPROVAL"));
        new PostgresShoppingPlanRepository(db).save(new ShoppingPlan(UUID.randomUUID(),"Existing unrelated plan",null,DAY,List.of(),1),"test-owner");
        var preview=LocalMigrationPreview.inspect(copy,original,db,database);
        assertTrue(preview.importDecision().contains("destination is populated"));
        assertEquals(1,preview.categories().stream().filter(r->r.name().equals("Shopping plans")).findFirst().orElseThrow().destination());
        assertEquals(0,count("receipt_current"));assertEquals(1,count("shopping_plan_current"));
    }
    @Test void previewDetectsEvidenceByteConflictWithoutRepairingIt()throws Exception {
        migrate();UUID evidence=source.evidence().keySet().iterator().next();byte[] altered={90,91,92};
        try(var c=db.open();var p=c.prepareStatement("UPDATE receipt_evidence SET content=? WHERE evidence_id=?")){p.setBytes(1,altered);p.setObject(2,evidence);p.executeUpdate();}
        var preview=LocalMigrationPreview.inspect(copy,original,db,database);
        assertEquals(1,preview.categories().stream().filter(r->r.name().startsWith("Evidence (")).findFirst().orElseThrow().conflicts());
        assertTrue(preview.importDecision().startsWith("BLOCKED"));
        try(var c=db.open();var p=c.prepareStatement("SELECT content FROM receipt_evidence WHERE evidence_id=?")){p.setObject(1,evidence);try(var r=p.executeQuery()){r.next();assertArrayEquals(altered,r.getBytes(1));}}
    }
}
