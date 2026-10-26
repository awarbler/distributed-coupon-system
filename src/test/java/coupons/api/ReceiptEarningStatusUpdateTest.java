package coupons.api;
import coupons.*;
import coupons.ReceiptObservations.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptEarningStatusUpdateTest {
    Earning expected(EarningKind kind,BenefitKind form) {
        return new Earning(UUID.randomUUID(),kind,"Test program","Recorded offer",Scope.RECEIPT,List.of(),new RebateBenefit(form,new BigDecimal("25.000"),form==BenefitKind.POINTS?null:Currency.getInstance("USD"),"Test program"),null,RebateStatus.EXPECTED,null,null,null,List.of(),"Original note",null,new RecordedValuation(new BigDecimal("0.025000"),Currency.getInstance("USD"),"Original basis"));
    }
    @Test void confirmsOriginalPointsAndValuationWithoutRoundingOrChangingIdentity() {
        var old=expected(EarningKind.THIRD_PARTY_REBATE,BenefitKind.POINTS);
        var updated=ReceiptEarningStatusUpdate.apply(old,RebateStatus.CREDITED,"25","points",true,null);
        assertEquals(old.earningId(),updated.earningId());assertSame(old.expected(),updated.expected());assertSame(old.expected(),updated.confirmed());assertSame(old.valuation(),updated.valuation());
        assertEquals(new BigDecimal("25.000"),updated.confirmed().amount());assertEquals("Original note",updated.recordedLifecycle());
    }
    @Test void requiresExplicitAmountAndUnitAndDoesNotAllowAmountCorrections() {
        var old=expected(EarningKind.THIRD_PARTY_REBATE,BenefitKind.POINTS);
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(old,RebateStatus.CREDITED,"25","points",false,null));
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(old,RebateStatus.CREDITED,null,"points",true,null));
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(old,RebateStatus.CREDITED,"25","USD",true,null));
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(old,RebateStatus.CREDITED,"26","points",true,null));
    }
    @Test void rejectsWithoutErasingExpectedValueOrOriginalNotes() {
        var old=expected(EarningKind.REGISTER_REWARD,BenefitKind.STORE_COUPON);
        var updated=ReceiptEarningStatusUpdate.apply(old,RebateStatus.REJECTED,null,null,false,"Coupon not issued");
        assertSame(old.expected(),updated.expected());assertNull(updated.confirmed());assertEquals("Original note\nRejection reason: Coupon not issued",updated.recordedLifecycle());
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(old,RebateStatus.EXPECTED,null,null,false,"not applicable"));
    }
    @Test void printedRewardsAreNotAccountCreditsOrRedemptionsAndLegacyChoicesAreNotAssigned() {
        var rr=expected(EarningKind.REGISTER_REWARD,BenefitKind.STORE_COUPON);
        var printed=ReceiptEarningStatusUpdate.apply(rr,RebateStatus.PAID,"25","USD",true,null);
        assertEquals(EarningKind.REGISTER_REWARD,printed.kind());assertEquals(BenefitKind.STORE_COUPON,printed.confirmed().kind());assertNull(printed.instrumentId());
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(rr,RebateStatus.CREDITED,"25","USD",true,null));
        for(var status:List.of(RebateStatus.APPROVED,RebateStatus.SUBMITTED,RebateStatus.PENDING))assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(rr,status,null,null,false,null));
        var cash=expected(EarningKind.THIRD_PARTY_REBATE,BenefitKind.CASH);
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.apply(cash,RebateStatus.PAID,"25","USD",true,null));
    }
    @Test void correctsAmountsWithoutChangingStatusIdentityOrOriginalExpectedValue() {
        var original=expected(EarningKind.THIRD_PARTY_REBATE,BenefitKind.POINTS);
        var received=ReceiptEarningStatusUpdate.apply(original,RebateStatus.CREDITED,"25","points",true,null);
        var corrected=ReceiptEarningStatusUpdate.correctAmount(received,"50","points");
        assertEquals(received.earningId(),corrected.earningId());assertEquals(received.status(),corrected.status());assertSame(original.expected(),corrected.expected());assertEquals(new BigDecimal("50"),corrected.confirmed().amount());
        assertEquals(0,new BigDecimal("0.05").compareTo(corrected.valuation().amount()));
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.correctAmount(received,"0","points"));
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.correctAmount(received,"50","USD"));
        assertEquals(new BigDecimal("50"),ReceiptEarningStatusUpdate.correctAmount(original,"50","points").expected().amount());
    }
    @Test void followUpDoesNotChangeFinancialStatusOrAmounts() {
        var original=expected(EarningKind.THIRD_PARTY_REBATE,BenefitKind.POINTS);
        var updated=ReceiptEarningStatusUpdate.followUp(original,"FOLLOW_UP","2026-10-26","claim-1","2026-11-01","Check provider");
        assertEquals(original.earningId(),updated.earningId());assertSame(original.expected(),updated.expected());assertSame(original.confirmed(),updated.confirmed());assertEquals(original.status(),updated.status());assertSame(original.valuation(),updated.valuation());
        assertEquals(java.time.LocalDate.of(2026,11,1),updated.details().followUpOn());
        var received=ReceiptEarningStatusUpdate.apply(updated,RebateStatus.CREDITED,"25","points",true,null);
        assertEquals(updated.details(),received.details());
        assertThrows(IllegalArgumentException.class,()->ReceiptEarningStatusUpdate.followUp(original,"CREDITED",null,null,null,null));
    }
}
