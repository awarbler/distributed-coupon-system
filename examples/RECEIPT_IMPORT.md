# Import a receipt photo or PDF

1. Take clear photos of one receipt, from top to bottom.
2. Copy the photos to your Mac.
3. Start the CLI and choose **9. Import**.
4. Type **FILES**, then choose **BROWSE**.
5. Select one receipt’s PDF or photos and confirm. PDF pages are read in order.
6. Review the receipt. Use **EDIT** to correct it and **REBATES** to add rewards.
7. Choose **SAVE**. Wait for **RECEIPT SAVED SUCCESSFULLY**.

To finish later, choose **PAUSE**; reopen it with **10. Resume**.

---

## Reference — only if needed

## Recovery and limitations

Extracted photos, text, TSV word positions/confidence, and a prepared manifest
are retained locally before review. If reading is interrupted, existing per-image
extractions can be reused on retry. No account activity is created by these files.
IMPORT > LIST reopens an extracted draft; 10 (Resume) reopens a checkpoint including completed
review edits and rebate entries. Completed entry sections, products, earnings,
and review field edits are checkpointed, not every keystroke. If interrupted before
checkout, Resume returns to the saved entry stage without inventing missing tax or
payment values. Deferred product associations are retained. Exiting preserves local
checkpoints; CANCEL explicitly discards the active draft. Unsubmitted corrections
to already-saved receipts remain session-only. Main menu 11 exits the program.

Repeated checkout sections in overlapping photos are not counted again. Ambiguous
product overlap is flagged and retained for review rather than silently deleting
what might be a legitimate repeated purchase. Confirm product counts and totals.
OCR can misread faded digits; the reader leaves missing values unresolved and the
existing calculator flags inconsistencies. Verify the original images before SAVE.

An earlier receipt does not need to be saved before entering a later one. Balances
use the dated receipts currently saved and recalculate when earlier activity is
added. An entry already lost before checkpoints existed cannot be reconstructed
unless its photos or prepared extraction remain available.

## Import an existing prepared extraction

1. Choose **9 / IMPORT**, then LIST to select a prepared `.properties` extraction,
   or enter its path.
2. Missing required fields are asked; existing fields open for review.
3. Use EDIT and REBATES, then SAVE. The original images remain attached.

`receipt-import.properties` is an example and references a placeholder image. It is
not a real receipt, and no sample import is saved automatically. Do not replace live
receipt storage with an import file.

## Fields

`format=receipt-import-v1` is required. Repeated or unknown keys fail instead of
silently dropping data. `*.count` declares each numbered group; optional groups
can be omitted. `image.count` and `item.count` must be positive.

- `image.N.path`: existing PNG/JPEG/PDF/WebP/HEIC file. Original bytes are preserved.
  Reading an extension here does not perform OCR or verify the image's interpretation.
- `receipt.id`: optional UUID. Without it, the same source image set has a stable
  import identity. Reimporting an already saved identity does not add another receipt;
  use UPDATE to correct it. Retain a supplied identity on retries.
- `receipt.retailer`, `receipt.number`, `receipt.date`, `receipt.time`, `receipt.tax`,
  `receipt.total`, `receipt.channel` (`IN_STORE`, `ONLINE`, `UNKNOWN`), `receipt.footer` (`PRINTED`, `NOT_PRINTED`, `UNKNOWN`). Dates use
  existing strict receipt-date rules; ISO YYYY-MM-DD is recommended.
- `item.N.description`, `sku`, `quantity`, `regularUnitPrice`, `enteredUnitPrice`.
  Missing retailer, description, quantity, entered price, tax or printed total are
  collected before normal receipt review. Missing regular price stays unknown.
  Entered prices already reduced by a sale must not repeat that sale as a deduction.
  For an explicit MyWalgreens savings breakdown, use the regular price as
  `enteredUnitPrice` and record the reduction as a `SALE_PROMOTION` coupon linked
  to that item. Example: 12.99 entered, 6.50 reduction, resulting price 6.49.
  In review, EDIT > ITEM includes Store coupons and promos and Manufacturer
  coupons (quantity, value per coupon, and required product quantity).
  EDIT > DISCOUNTS > ADD can add a sale reduction, IVC/store coupon, manufacturer
  coupon, or unknown/mixed discount and select its products. Existing deductions
  remain available through SELECT, LABEL, AMOUNT, REQUIRED, and DISCOUNT TYPE.
- `coupon.N.description`, `amount`, `kind` (`MANUFACTURER`, `STORE_COUPON`,
  `SALE_PROMOTION`, or an existing legacy classification), `lines` (e.g. `1,2`),
  optional `requiredQuantity`. One shared coupon is one deduction with several links.
  For a whole-receipt deduction, set `coupon.N.scope=RECEIPT` and leave `lines`
  empty. Empty lines without that scope remain unknown for older imports. This
  scope applies to store promotions, online store coupons, and manufacturer coupons.
- `rrUse.N.amount`, optional `description`, `notes`: actual RR deducted, not earnings.
  Instrument sources remain unmatched until explicitly matched through the RR menu.
- `payment.N.type` (`CASH`, `CARD`, `CREDIT_CARD`, `DEBIT_CARD`, `GIFT_CARD`,
  `WALGREENS_CASH`, `OTHER`), `amount`, optional `notes`, `account`. A zero payment
  creates no payment event. Use rrUse for RR deductions. Nonzero checkout without
  a recorded tender is flagged rather than assuming cash payment.
- `earning.N.kind` (`REGISTER_REWARD`, `WALGREENS_CASH`, `THIRD_PARTY_REBATE`),
  `provider`, `amount`, `status`, `scope`, optional `lines`, `offer`, `note`, `expires`.
  `scope` can be `ITEM`, `MULTI_ITEM`, `RECEIPT`, `UNKNOWN`; linked lines determine
  ITEM/MULTI_ITEM when scope is absent. Absent links alone never imply whole receipt.
  Native RR/WC default to their recorded reward form. Third-party earnings require
  an explicit `form` (`CASH`, `GIFT_CARD`, `POINTS`, `STORE_REWARD`, `STORE_COUPON`).
  Optional `currency` defaults to USD for monetary forms; `program` records the
  original points/gift-card program. Points never become dollar amounts automatically.
- `earning.N.status`: existing `RebateStatus` name. Missing status defaults to EXPECTED,
  never credited. APPROVED/CREDITED/PAID entries carry confirmed values; other entries
  remain unconfirmed. Match the status to your actual receipt/app observation.
- `earning.N.usdValue`, `valuationBasis`: optional saved valuation; basis is required
  with a value. Missing point valuation is flagged, not calculated from provider names.
- `earning.N.requirementType` (`BUY`, `SPEND`), `requirementAmount`, `spendBasis`
  (`SALE_BEFORE_COUPONS`, `UNKNOWN`): recorded reward terms, not inferred from notes.
- `footer.myWalgreensSavings`, `manufacturerSavings`, `totalSavings`, `percent`,
  `start`, `earned`, `used`, `end`: only supply actual printed values. NOT_PRINTED
  cannot also have printed footer values. Printed WC aggregates do not duplicate earnings.
- `review.N.field`, optional `source`, `reason`: extraction uncertainties to review.
  Original warnings stay in attached evidence, even after correcting the draft.

Blank/absent optional values mean unknown, not zero. Invalid numeric values or links
fail with their field name; replace unreadable required values with blanks so the
CLI can ask for verification. Do not use zero as an unreadable-value placeholder.

The same printed number/retailer can trigger a possible-duplicate warning even if
the source images differ. The user must explicitly approve a separate save. This
is not automatic merging, authentication, or external claim submission. Online
rebate eligibility remains available through existing EDIT > PROMOTIONS.

From the main menu choose **9. Import a receipt file** (or type IMPORT).
At the file prompt type LIST to select a prepared `.properties` file by number
from `.coupon-data/imports`, or paste a file path. The selected receipt opens
for review: EDIT corrects fields, REBATES adds earnings, and SAVE approves it.
Use main menu 3 to update a receipt already saved, or retrieve it and choose EDIT.
For raw photos choose PHOTOS instead of entering a .properties file.

SAVE is your approval; old extraction notes no longer demand a second approval.
Current issues are recomputed after edits. Online orders cannot earn or use RR;
contradictory entries must be corrected before persistence.

### PDF setup

Build once with `mvn package`. Start with:
```bash
java -cp "target/classes:target/dependency/*" coupons.ReceiptEntryApp
```
PDF text is read locally; scanned pages use local Tesseract. Originals are retained. Maximum 30 pages per PDF; unlock password-protected files first.
