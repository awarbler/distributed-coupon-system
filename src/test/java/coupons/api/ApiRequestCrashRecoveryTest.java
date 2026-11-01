package coupons.api;

import coupons.repository.PostgresConnectionFactory;
import coupons.repository.PostgresTestDatabase;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real child-process termination on either side of a synthetic business commit. */
class ApiRequestCrashRecoveryTest {
    @Test void crashBeforeBusinessCommitLeavesNoEffectAndBlocksBlindReplay() throws Exception { boundary(false); }
    @Test void crashAfterBusinessCommitRetainsOneEffectAndBlocksBlindReplay() throws Exception { boundary(true); }
    private void boundary(boolean committed) throws Exception {
        try(var database=new PostgresTestDatabase()) {
            String url,schema;
            try(var c=database.connections.open();var s=c.createStatement()) {
                url=c.getMetaData().getURL();schema=c.getSchema();
                s.execute("CREATE TABLE stage3_crash_probe(id text PRIMARY KEY)");
            }
            var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),Child.class.getName(),url,schema,Boolean.toString(committed))
                    .redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try {
                assertTrue(child.waitFor(15,TimeUnit.SECONDS),"Crash probe timed out");assertEquals(23,child.exitValue());
            } finally {if(child.isAlive())child.destroyForcibly();}
            var store=new PostgresApiRequestStore(database.connections);
            assertThrows(ApiRequestStore.Conflict.class,()->store.execute("stage3","crash","same",()-> {
                fail("A pending request must never blindly replay after a real process crash");return null;
            }));
            try(var c=database.connections.open();var s=c.createStatement()) {
                try(var rows=s.executeQuery("SELECT count(*) FROM stage3_crash_probe")){rows.next();assertEquals(committed?1:0,rows.getInt(1));}
                try(var rows=s.executeQuery("SELECT status_code FROM api_write_requests WHERE scope='stage3' AND request_key='crash'")) {
                    assertTrue(rows.next());assertNull(rows.getObject(1),"Outcome remains pending: deterministic resolution is a separate open requirement");
                }
            }
        }
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            var db=new PostgresConnectionFactory(args[0],System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
            try(Connection c=PostgresTestDatabase.targetForSchema(args[1]).open(db)){ /* Verify disposable server before writes. */ }
            new PostgresApiRequestStore(db).execute("stage3","crash","same",()-> {
                if(Boolean.parseBoolean(args[2])) {
                    try(var c=db.open();var s=c.createStatement()){s.executeUpdate("INSERT INTO stage3_crash_probe VALUES ('one synthetic effect')");}
                    catch(java.sql.SQLException ex){throw new IllegalStateException(ex);}
                }
                Runtime.getRuntime().halt(23);
                throw new AssertionError("Unreachable after forced process termination");
            });
        }
    }
}
