# Using Detailed breakdown and receipt reports

This guide describes the current CLI. Detailed breakdown stays in the Edit menu.
You can type the command names below instead of their displayed numbers.

## Start the CLI

From the project folder:

```bash
mvn clean package
java -cp target/classes coupons.ReceiptEntryApp
```

Restart after a code update to load the rebuilt version. SAVE approves a receipt;
PAUSE keeps a new receipt draft for later without affecting balances. Choose 10 (Resume) at
the main menu to resume. Saved-receipt correction proposals remain session-only.

## What Detailed breakdown does

It provides three advanced settings for an existing receipt:

| Option | Purpose | Example |
| --- | --- | --- |
| GROUPS | Choose the reporting category for an earning already entered. | Report an actual gift-card award under GIFT_CARD. |
| USES | Associate an existing reward/payment use with product lines and, when applicable, record original points consumed. | Associate an already-entered $5 WC use with selected items. |
| ORDER | Set this transaction's sequence within its purchase day. | Enter 2 for the second transaction of that day. |

These settings do not create new earnings, coupons, payments, or redemptions.
They describe existing financial events. The menu currently processes one selected
setting, then returns to receipt review; reopen it to change another setting.

## Edit an unsaved receipt

1. From receipt review choose **EDIT**.
2. Choose **Detailed breakdown**, or type `DETAILED BREAKDOWN`.
3. Choose **GROUPS**, **USES**, or **ORDER**.
4. Complete the prompts described below.
5. Review the receipt and choose **SAVE** to persist your changes.

`WORKSHEET` remains an accepted command for Detailed breakdown.

## Edit a saved receipt

1. At the main menu choose **3. Update a receipt**.
2. Select the saved receipt using its printed number or listed session number.
3. Choose **Detailed breakdown**.
4. Change the setting, review the proposed correction, and choose **SAVE**.
5. Supply the correction reason if requested.

You can also retrieve a receipt and choose **Edit this receipt** (or type EDIT).
After saving or cancelling the correction, you return to that receipt's view.
An unsaved edit does not change your saved balances or reports.

## GROUPS: reporting categories

Select an earning from the displayed list, then select its reporting group:

| Group | Use for |
| --- | --- |
| CASH | Cash rebate value earned. |
| GIFT_CARD | Gift-card value earned. |
| STORE_REWARD | Store rewards such as Register Rewards or Walgreens Cash. |
| POINTS_VALUE | The saved dollar valuation of points earned. |

Choose according to the specific award, rather than assuming a provider always
issues one kind of benefit. Normally the default follows the benefit entered.

**Fetch example:** points earned remain points with their saved dollar valuation.
Changing their reporting group does not redeem them or create a gift card.
Actual point redemption is separate activity.

To correct the earning's amount, status, offer, products, or valuation, use
**EDIT > EARNINGS** instead. GROUPS only changes its reporting category.

In EARNINGS, choose **ADD** to open the add/edit form. The next list explains
each choice: **0 adds a new rebate or reward**; **1, 2, etc. edit the existing
earning shown on that line**. BACK returns without making that selection.
For a single-field correction, SELECT the existing earning and then choose
AMOUNT, STATUS, OFFER, PRODUCTS, EXPIRY, or VALUE. SAVE approves your changes.

Enter `0` at the earning-number prompt to return without selecting an earning.

## USES: associate rewards already used

1. First enter the actual use through **CHECKOUT**, such as RR used or WC used.
2. Open **Detailed breakdown > USES**.
3. Select the existing use from the list. Enter `0` to return.
4. Select its product lines, or `0` for the whole receipt.
5. For a named account, enter original points consumed if this was a points
   redemption; otherwise leave that field blank.
6. A named OTHER payment may ask whether it reduced the printed checkout total.
   Answer Yes only if it was deducted before that total; No means it funded payment.
7. Return to review and SAVE.

For a reward used across the receipt, choose Whole receipt rather than assigning
its full amount to each product. This setting stores the association; it does not
ask you to split the dollar amount among the selected lines.

To correct the amount actually used, use **CHECKOUT**, not USES. To identify which
physical RR coupon was spent, use **main menu 5 > Match RR used to coupon**.

## ORDER: transaction sequence

Enter a positive whole number: 1 for the first transaction, 2 for the second, etc.
Use this when you want an explicit sequence within a purchase day. It does not
change the receipt's purchase date, printed time, or financial amounts.

## View one receipt

Choose **main menu 2. Retrieve a receipt**, then select the receipt.

| Command | What you see |
| --- | --- |
| TOTALS | Compact transaction totals and savings. |
| ITEMIZED | Item-level prices, discounts, earnings, and savings. |
| ACCOUNTS | Account balances around this transaction, based on recorded activity. |
| FULL | Full receipt display, including coupons, payments, and rewards. |
| EDIT | Edit this receipt directly; SAVE persists the reviewed correction. |
| BACK | Return to the main menu. |

These are viewing actions; you do not need to SAVE after running a report.

## Run transaction, day, week, and month reports

1. Choose **main menu 6. Account balances / Savings reports**. Daily balances,
   week-to-date and month-to-date account tables, and weekly/monthly savings
   summaries appear automatically through the latest recorded purchase date.
2. Choose **SAVINGS** to request a different reporting period.
3. Choose a period:

| Command | What to enter |
| --- | --- |
| TRANSACTION | Select the saved receipt. |
| DAY | Purchase date, for example `10/8/2026`. |
| WEEK | Any date within the desired Sunday–Saturday sales week. |
| MONTH | Follow the month prompt, for example `2026-10`. |

The compact savings summary appears first. Choose **DETAILS** afterward to see
the expanded totals and provider breakdown for the selected transaction or period.
Choose a new period to change the selection, or BACK to leave the reports menu.

Reports use current saved receipt revisions. Unsaved drafts are not included in
saved-period reports. Receipts without purchase dates are excluded from dated
reports. Missing regular prices or dollar valuations can make totals partial;
correct those facts in the receipt rather than entering a reporting category to
stand in for a missing amount.

## Which editor should I use?

### Store promotions and online checkout codes

These are discounts, not newly earned rebates. In **EDIT > DISCOUNTS > ADD**,
choose Sale/promotion reduction for a reduction in sale prices, or Store coupon /
IVC / online coupon code for a checkout code applied after sale prices. Enter the
total deduction once. At the product prompt, **0 means Whole receipt**, A selects
all currently listed products, and U means the products are unknown.

To correct an existing discount, use **SELECT**, choose its description, then
**DISCOUNT TYPE** to reclassify it or **PRODUCTS > 0** to mark it Whole receipt. Finish with
DONE and SAVE. Do not ADD it again. Its identity and original history are retained.
Older empty associations remain unknown until you explicitly choose Whole receipt.

Receipt-wide sale reductions are deducted once when all products are selected for
a spend check. A check for only some products needs an allocation review. Store
coupon codes are excluded from a spend basis explicitly defined as before coupons.

| What you want to change | Editor |
| --- | --- |
| Description, SKU, regular price, entered price, or quantity | ITEM |
| Add sale reductions, IVC/store coupons, or manufacturer discounts | DISCOUNTS > ADD |
| Manufacturer coupon quantity and value for an item | ITEM > MANUFACTURER COUPONS |
| Tax, RR/WC used, payment, or printed total | CHECKOUT |
| Reward earned, status, product association, or point valuation | EARNINGS |
| Reporting category, use association, or daily sequence | Detailed breakdown |
| Printed savings footer | SAVINGS within Edit |
| Savings reports | Main menu 6 > SAVINGS |

**DONE** returns from field editing to review. **BACK** returns to the previous
question or menu. **SAVE** persists the reviewed receipt or correction.

Every receipt has a **RECEIPT ISSUES** section showing current concerns and affected
fields. Choose **ISSUES** for the correction path. Corrected concerns disappear
from that list automatically; original extraction/recovery notes remain in the
source evidence and do not trigger repeated approval questions. **SAVE is approval**
for non-blocking review concerns. Blocking issues must be corrected first.

## Online orders and Register Rewards

Use **EDIT > Receipt details > Order type** to choose In store, Online, or Unknown.
During new manual entry, type **ONLINE** at the retailer prompt, then enter the
store name. The configured rule is that online orders cannot earn or use Register
Rewards. RR entry is skipped for online checkout and new RR earnings are refused.
If existing data contradicts the rule, it appears as a blocking issue; it is not
silently deleted. Correct the earning or use before saving. Unknown historical
order types remain unknown; the program does not infer them from a receipt number.

## Add, update, or delete receipt entries

In **Edit**, choose **Products**, **Manufacturer coupons**, **Discounts and store promotions**, or **Rewards and rebates**, then choose **Add**, **Update**, or **Delete**. Update selects an entry; choose the field to change afterward. Delete requires confirmation. Choose **Done**, then **SAVE** to apply changes.

In **Online rebate eligibility**, use **ADD**, **UPDATE**, or **DELETE** for this receipt’s promotion link. Deleting a link removes this receipt’s progress when saved; the offer and earlier receipt history remain. Reassign or remove a product’s linked coupons, rewards, and promotions before deleting the product.
