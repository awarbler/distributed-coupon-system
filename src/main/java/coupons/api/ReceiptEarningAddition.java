package coupons.api;

import coupons.*;
import coupons.ReceiptObservations.*;
import coupons.repository.*;
import java.time.Instant;
import java.util.*;
import static coupons.api.Json.*;

/** Appends an earning to the existing aggregate and commits through the existing validated receipt repository. */
record ReceiptEarningAddition(CouponTransaction transaction,List<ReceiptEvidence> evidence,Earning earning) {
    static ReceiptEarningAddition read(ReceiptRecord record,Map<String,Object> f) {
        known(f,"earning","evidence");if(!(f.get("earning") instanceof Map<?,?>))throw new IllegalArgumentException("Earning is required.");
        @SuppressWarnings("unchecked") var fields=(Map<String,Object>)f.get("earning");
        var added=EarningInput.read(fields);var tx=record.transaction();var obs=tx.getObservations();
        var earnings=new ArrayList<>(obs.earnings());
        if(added.kind()==EarningKind.WALGREENS_CASH)earnings.removeIf(e->e.earningId().equals(ReceiptInput.printedCashId(record.transactionId())));
        ReceiptEarningRules.additional(earnings,added);earnings.add(added);
        tx.setObservations(new ReceiptObservations(obs.purchaseDate(),obs.printedTime(),earnings,obs.unidentifiedRR(),obs.walgreensCash(),obs.savings(),obs.discountKinds(),obs.receiptSavingValuePercent(),obs.promotionLinks(),obs.worksheet(),obs.footerStatus()));
        var evidence=ReceiptInput.evidence(f,record.transactionId());var ids=new ArrayList<>(record.evidenceIds());evidence.forEach(e->ids.add(e.getEvidenceId()));
        ReceiptValidator.validate(new ReceiptRecord(record.transactionId(),record.status(),record.revision(),tx,record.metadata(),ids));
        return new ReceiptEarningAddition(tx,evidence,added);
    }
    ReceiptWriteResult save(TransactionRepository repository,ReceiptRecord old,String actor) {
        var now=Instant.now();return repository.update(old.transactionId(),old.revision(),transaction,old.metadata(),evidence,new ReceiptWriteRequest(UUID.randomUUID(),now),new ReceiptChangeContext(actor,"Added receipt earning "+earning.earningId(),actor,now));
    }
}
