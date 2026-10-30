package coupons.receipts;

import coupons.promotions.CouponApplied;

import coupons.receipts.ReceiptObservations.*;
import coupons.repository.ReceiptEvidence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Unapproved extraction data. Missing required fields are resolved before constructing financial records. */
public record ReceiptImportDraft(UUID importId, String retailer, String receiptNumber,
        LocalDate purchaseDate, String printedTime, BigDecimal tax, BigDecimal printedTotal,
        List<ImportedItem> items, List<ImportedDiscount> discounts, List<Payment> payments,
        List<UnidentifiedRR> rrUses, List<Earning> earnings, WalgreensCashAccount account,
        List<SavingsCategory> savings, BigDecimal savingPercent, FooterStatus footerStatus,
        List<ReceiptEvidence> evidence, List<ReviewIssue> issues, coupons.repository.ReceiptMeta.PurchaseChannel purchaseChannel) {
    public record ImportedItem(UUID lineId,String description,String sku,Integer quantity,
            BigDecimal regularUnitPrice,BigDecimal enteredUnitPrice) { }
    public record ImportedDiscount(CouponApplied application,DiscountKind kind) { }
    public record ReviewIssue(String field,String sourceText,String explanation) implements java.io.Serializable { }
    public ReceiptImportDraft {
        Objects.requireNonNull(importId);
        items=List.copyOf(items);discounts=List.copyOf(discounts);payments=List.copyOf(payments);
        rrUses=List.copyOf(rrUses);earnings=List.copyOf(earnings);savings=List.copyOf(savings);
        evidence=List.copyOf(evidence);issues=List.copyOf(issues);
    }
}
