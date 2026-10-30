package coupons.serialization;

import java.io.*;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Keeps the existing Java storage descriptors stable while source packages are organized by feature. */
public final class LegacyObjectStreams {
    private LegacyObjectStreams() {}
    private static final Map<String,String> CURRENT = Map.ofEntries(
            Map.entry("coupons.InventoryBalance", "coupons.inventory.InventoryBalance"),
            Map.entry("coupons.PlannedPurchase", "coupons.shopping.PlannedPurchase"),
            Map.entry("coupons.ShoppingPlan", "coupons.shopping.ShoppingPlan"),
            Map.entry("coupons.ShoppingEstimate", "coupons.shopping.ShoppingEstimate"),
            Map.entry("coupons.RegisterReward", "coupons.rewards.RegisterReward"),
            Map.entry("coupons.RegisterRewardRedemption", "coupons.rewards.RegisterRewardRedemption"),
            Map.entry("coupons.RegisterRewardLabel", "coupons.rewards.RegisterRewardLabel"),
            Map.entry("coupons.Reward", "coupons.rewards.Reward"),
            Map.entry("coupons.RewardType", "coupons.rewards.RewardType"),
            Map.entry("coupons.RewardSpendCheck", "coupons.rewards.RewardSpendCheck"),
            Map.entry("coupons.ProviderBalanceSummary", "coupons.rewards.ProviderBalanceSummary"),
            Map.entry("coupons.SpreadsheetBalanceImport", "coupons.rewards.SpreadsheetBalanceImport"),
            Map.entry("coupons.EarningDetails", "coupons.rewards.EarningDetails"),
            Map.entry("coupons.AdvertisedDeal", "coupons.promotions.AdvertisedDeal"),
            Map.entry("coupons.BenefitKind", "coupons.promotions.BenefitKind"),
            Map.entry("coupons.ClippedCoupon", "coupons.promotions.ClippedCoupon"),
            Map.entry("coupons.Coupon", "coupons.promotions.Coupon"),
            Map.entry("coupons.CouponApplied", "coupons.promotions.CouponApplied"),
            Map.entry("coupons.CouponStatus", "coupons.promotions.CouponStatus"),
            Map.entry("coupons.CouponSystem", "coupons.distributed.CouponSystem"),
            Map.entry("coupons.CouponTerms", "coupons.promotions.CouponTerms"),
            Map.entry("coupons.CouponType", "coupons.promotions.CouponType"),
            Map.entry("coupons.DealTerms", "coupons.promotions.DealTerms"),
            Map.entry("coupons.Promotion", "coupons.promotions.Promotion"),
            Map.entry("coupons.PromotionQualification", "coupons.promotions.PromotionQualification"),
            Map.entry("coupons.PromotionType", "coupons.promotions.PromotionType"),
            Map.entry("coupons.QualifyingSpend", "coupons.promotions.QualifyingSpend"),
            Map.entry("coupons.RebateBenefit", "coupons.promotions.RebateBenefit"),
            Map.entry("coupons.RebateClaim", "coupons.promotions.RebateClaim"),
            Map.entry("coupons.RebateStatus", "coupons.promotions.RebateStatus"),
            Map.entry("coupons.SourceArtifact", "coupons.promotions.SourceArtifact"),
            Map.entry("coupons.CouponTransaction", "coupons.receipts.CouponTransaction"),
            Map.entry("coupons.PurchaseItem", "coupons.receipts.PurchaseItem"),
            Map.entry("coupons.Payment", "coupons.receipts.Payment"),
            Map.entry("coupons.PaymentType", "coupons.receipts.PaymentType"),
            Map.entry("coupons.ReceiptTotals", "coupons.receipts.ReceiptTotals"),
            Map.entry("coupons.ReceiptTimeInput", "coupons.receipts.ReceiptTimeInput"),
            Map.entry("coupons.ReceiptFileFormat", "coupons.receipts.ReceiptFileFormat"),
            Map.entry("coupons.ReceiptImportDraft", "coupons.receipts.ReceiptImportDraft"),
            Map.entry("coupons.ReceiptImportReader", "coupons.receipts.ReceiptImportReader"),
            Map.entry("coupons.ReceiptObservations", "coupons.receipts.ReceiptObservations"),
            Map.entry("coupons.ReceiptPromotionLink", "coupons.receipts.ReceiptPromotionLink"),
            Map.entry("coupons.ReceiptWorksheetData", "coupons.receipts.ReceiptWorksheetData"),
            Map.entry("coupons.ReceiptWorksheetReport", "coupons.receipts.ReceiptWorksheetReport"),
            Map.entry("coupons.ReceiptIssues", "coupons.receipts.ReceiptIssues"),
            Map.entry("coupons.ReceiptReport", "coupons.receipts.ReceiptReport"),
            Map.entry("coupons.SavingsReport", "coupons.receipts.SavingsReport"),
            Map.entry("coupons.TransactionCalculator", "coupons.receipts.TransactionCalculator"),
            Map.entry("coupons.LocalPdfReceiptReader", "coupons.receipts.LocalPdfReceiptReader"),
            Map.entry("coupons.LocalReceiptOcr", "coupons.receipts.LocalReceiptOcr"),
            Map.entry("coupons.WalgreensImageImport", "coupons.receipts.WalgreensImageImport"),
            Map.entry("coupons.ReceiptEntryApp", "coupons.cli.ReceiptEntryApp"),
            Map.entry("coupons.CouponTrackerApp", "coupons.cli.CouponTrackerApp"),
            Map.entry("coupons.ReceiptDateInput", "coupons.receipts.ReceiptDateInput"),
            Map.entry("coupons.ReceiptDraftFile", "coupons.cli.ReceiptDraftFile"),
            Map.entry("coupons.PreparedImportList", "coupons.cli.PreparedImportList"),
            Map.entry("coupons.DistributedEvent", "coupons.distributed.DistributedEvent"),
            Map.entry("coupons.DistributedProcess", "coupons.distributed.DistributedProcess"),
            Map.entry("coupons.Message", "coupons.distributed.Message")
    );
    private static final Map<String,String> LEGACY = reverse();

    private static Map<String,String> reverse() {
        var values = new HashMap<String,String>();
        CURRENT.forEach((oldName,newName)->values.put(newName,oldName));
        return Map.copyOf(values);
    }

    public static ObjectInputStream input(InputStream source) throws IOException { return new Input(source); }
    public static ObjectOutputStream output(OutputStream destination) throws IOException { return new Output(destination); }

    private static String rename(String name, Map<String,String> names) {
        int start = name.lastIndexOf('[') + 1;
        boolean signature = name.endsWith(";") && name.charAt(start)=='L';
        if (signature) start++;
        int end = signature ? name.length()-1 : name.length();
        String binary = name.substring(start,end).replace('/','.');
        int nested = binary.indexOf('$');
        String root = nested<0 ? binary : binary.substring(0,nested);
        String replacement = names.get(root);
        if (replacement==null) return name;
        replacement += nested<0 ? "" : binary.substring(nested);
        if (name.indexOf('/')>=0) replacement=replacement.replace('.','/');
        return name.substring(0,start)+replacement+name.substring(end);
    }

    private static final class Input extends ObjectInputStream {
        Input(InputStream source) throws IOException { super(source); }
        @Override protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException,ClassNotFoundException {
            String name=rename(descriptor.getName(),CURRENT);
            if (!name.equals(descriptor.getName())) {
                return Class.forName(name,false,LegacyObjectStreams.class.getClassLoader());
            }
            return super.resolveClass(descriptor);
        }
    }

    private static final class Output extends ObjectOutputStream {
        Output(OutputStream destination) throws IOException { super(destination); }
        @Override protected void writeClassDescriptor(ObjectStreamClass descriptor) throws IOException {
            String name=rename(descriptor.getName(),LEGACY);
            boolean changed=!name.equals(descriptor.getName());
            for (var field:descriptor.getFields()) {
                String signature=field.getTypeString();
                if (signature!=null&&!rename(signature,LEGACY).equals(signature)) changed=true;
            }
            if (!changed) { super.writeClassDescriptor(descriptor); return; }
            Class<?> type=descriptor.forClass();
            if (Externalizable.class.isAssignableFrom(type)||hasCustomWriter(type))
                throw new InvalidClassException(type.getName(),"Custom serialization requires an explicit compatibility review");
            // The affected domain contracts are ordinary Serializable classes, records, enums or arrays.
            writeUTF(name);
            writeLong(type.isArray()?legacyArrayUid(name,type):descriptor.getSerialVersionUID());
            writeByte(type.isEnum()?SC_SERIALIZABLE|SC_ENUM:SC_SERIALIZABLE);
            writeShort(descriptor.getFields().length);
            for (var field:descriptor.getFields()) {
                writeByte(field.getTypeCode()); writeUTF(field.getName());
                if (!field.isPrimitive()) writeObject(rename(field.getTypeString(),LEGACY).intern());
            }
        }
    }

    private static boolean hasCustomWriter(Class<?> type) {
        try { return Modifier.isPrivate(type.getDeclaredMethod("writeObject",ObjectOutputStream.class).getModifiers()); }
        catch (NoSuchMethodException absent) { return false; }
    }

    /** Arrays have no declared members; their standard serial UID hashes only binary name and modifiers. */
    private static long legacyArrayUid(String name,Class<?> array) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try (var data=new DataOutputStream(bytes)) {
            data.writeUTF(name);
            data.writeInt(array.getModifiers()&(Modifier.PUBLIC|Modifier.FINAL|Modifier.INTERFACE|Modifier.ABSTRACT));
        }
        try {
            byte[] digest=MessageDigest.getInstance("SHA-1").digest(bytes.toByteArray());
            long uid=0;
            for (int i=7;i>=0;i--) uid=(uid<<8)|(digest[i]&0xff);
            return uid;
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
}
