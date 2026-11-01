package coupons.repository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import coupons.receipts.*;
import coupons.shopping.ShoppingPlan;

class WorkspaceSnapshotTest {
    @Test void concurrentAtomicPurchaseCannotSplitReceiptAndPlanViews() throws Exception {
        try(var database=new PostgresTestDatabase()) {
            var db=database.connections;
            UUID planId=UUID.randomUUID(),receiptId=UUID.randomUUID();
            var plans=new PostgresShoppingPlanRepository(db);
            plans.save(new ShoppingPlan(planId,"Synthetic concurrency fixture",null,null,List.of(),1),"test");
            var fired=new AtomicBoolean();
            String url;
            try(var c=db.open()){url=c.getMetaData().getURL();}
            final String delegateUrl=url;
            Driver driver=new Driver() {
                public boolean acceptsURL(String u){return u.startsWith("jdbc:stage3:");}
                public Connection connect(String u,Properties properties)throws SQLException {
                    if(!acceptsURL(u))return null;
                    var actual=DriverManager.getConnection(delegateUrl,properties);
                    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)-> {
                        Object value=invoke(actual,method,args);
                        if(method.getName().equals("prepareStatement")&&((String)args[0]).contains("FROM receipt_current")) {
                            var statement=(PreparedStatement)value;
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)-> {
                                Object result=invoke(statement,m,a);
                                if(m.getName().equals("executeQuery")&&fired.compareAndSet(false,true)) {
                                    var tx=new CouponTransaction(new ReceiptTotals(BigDecimal.ZERO,BigDecimal.TEN));
                                    tx.addPurchaseItem(new PurchaseItem("Synthetic concurrent product",null,null,BigDecimal.TEN,1));
                                    new PostgresTransactionRepository(db).saveWithPlan(receiptId,tx,null,List.of(),new ReceiptWriteRequest(UUID.randomUUID(),Instant.now()),new ReceiptCreateCtx("test"),List.of(),planId,1);
                                }
                                return result;
                            });
                        }
                        return value;
                    });
                }
                public DriverPropertyInfo[] getPropertyInfo(String u,Properties p){return new DriverPropertyInfo[0];}
                public int getMajorVersion(){return 1;} public int getMinorVersion(){return 0;}
                public boolean jdbcCompliant(){return false;} public Logger getParentLogger(){return Logger.getGlobal();}
            };
            DriverManager.registerDriver(driver);
            try {
                var intercepted=new PostgresConnectionFactory("jdbc:stage3:test",System.getenv("COUPON_POSTGRES_TEST_USER"),System.getenv("COUPON_POSTGRES_TEST_PASSWORD"));
                var result=new PostgresPlatformRepository(intercepted).workspace();
                assertTrue(fired.get(),"The concurrent write must actually execute");
                assertTrue(((List<?>)result.get("receipts")).isEmpty());
                @SuppressWarnings("unchecked") var row=(Map<String,Object>)((List<?>)result.get("plans")).getFirst();
                var plan=(ShoppingPlan)row.get("plan");
                assertNull(plan.purchasedReceiptId(),"A workspace without the new receipt must retain its prior unpurchased plan");
                assertEquals(1,plan.revision());
                assertEquals(receiptId,plans.findById(planId).orElseThrow().purchasedReceiptId(),"Writer committed independently");
            } finally {DriverManager.deregisterDriver(driver);}
        }
    }
    private static Object invoke(Object target,java.lang.reflect.Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException ex){throw ex.getCause();}
    }
}
