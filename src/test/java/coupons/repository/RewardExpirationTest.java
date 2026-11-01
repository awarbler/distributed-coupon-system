package coupons.repository;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewardExpirationTest {
    @Test void remindersRespectCorrectionsRemovalAndExpiration() {
        var day=LocalDate.of(2026,11,1);
        var id=UUID.randomUUID();
        var earning=new AccountAdjustment(id,day,"ECB",null,new BigDecimal("5"),"Member reward: Weekly bonus","test",Instant.now(),day.plusDays(1));
        var correction=new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,new BigDecimal("-2"),"Member reward correction: "+id,"test",Instant.now());
        var balances=new AccountBalanceState(List.of(),List.of(earning,correction));
        var state=new InMemoryStore.State(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),balances);
        var rows=PostgresPlatformRepository.expiringRewards(state,List.of(),day);
        assertEquals(1,rows.size());
        assertEquals(new BigDecimal("3"),((Map<?,?>)rows.getFirst()).get("amount"));
        assertTrue(PostgresPlatformRepository.expiringRewards(state,List.of(),day.plusDays(2)).isEmpty());
        var deletion=new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,new BigDecimal("-5"),"Member reward correction: "+id,"test",Instant.now());
        var deleted=new InMemoryStore.State(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),new AccountBalanceState(List.of(),List.of(earning,deletion)));
        assertTrue(PostgresPlatformRepository.expiringRewards(deleted,List.of(),day).isEmpty());
        var unavailable=new AccountAdjustment(UUID.randomUUID(),day,"ECB",null,new BigDecimal("-5"),"Reward unavailable: EXPIRED | "+id+" | Expired","test",Instant.now());
        var removed=new InMemoryStore.State(Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),new AccountBalanceState(List.of(),List.of(earning,unavailable)));
        assertTrue(PostgresPlatformRepository.expiringRewards(removed,List.of(),day).isEmpty());
    }
}
