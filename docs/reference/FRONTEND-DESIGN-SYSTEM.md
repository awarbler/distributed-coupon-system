# Frontend Design System & Component Style Guide

Version 1.9 · October 23, 2026 · Working implementation guide

This is the detailed visual specification for the browser application. The SRS
states observable usability requirements; the SDD explains implementation and
component responsibilities and references this guide. This guide defines fonts,
colors, dimensions, spacing, states and responsive behavior. It does not change
financial policy or certify accessibility compliance.

## Current larger sizing

Controls are now 60px tall with 18px value text, 16px horizontal padding and 15px
modal/filter labels. The dialog maximum width is 1040px. Receipt-editor and product
card grids use one full-width column, including on desktop, so fields are wider.
Long descriptions still expand. These values supersede the initial 48px/16px design.

## Source of truth and ownership

| Location | Responsibility |
|---|---|
| `frontend/src/index.css` | Named font/color/size/spacing tokens, global typography, input/select reset and focus |
| `frontend/src/App.css` | Component layout, modal/grid/filter/table responsiveness |
| `frontend/src/components/ui.tsx` | Shared UI primitives and expanding textareas |
| `frontend/src/features/receipts/ReceiptCreate.tsx` | Create form, financial review, shared footer and product discount controls |
| `frontend/src/features/receipts/ReceiptDetail.tsx` | Saved receipt editing, lifecycle actions and RR matching |
| `frontend/src/features/receipts/rewardStatus.ts` | Reward/printed-footer form draft mapping |
| `frontend/src/pages/WorkspacePages.tsx` | Receipt/rewards/account pages and standalone inventory |
| `frontend/src/api/format.ts` | Value/date/time display and receipt browse/filter helpers |
| `frontend/tests/browser.spec.ts` | Control dimensions, text fit and responsive workflows |
| `frontend/playwright.config.ts` | Desktop WebKit and desktop/mobile Chromium projects |

Tokens are the starting point for shared styling changes. Do not add per-form
heights or browser-default selects that conflict with them. Existing historical
component CSS still contains literal colors/font sizes; this change centralizes
core controls rather than claiming every legacy rule has been tokenized.

## Typography

| Role | Specification |
|---|---|
| Font family | `Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` |
| Body | 14px, weight 400, line-height 1.5 |
| Input and select values | 18px at desktop and mobile widths |
| Field labels | Modal and receipt-filter labels: 15px; other legacy labels retain their scoped styles |
| Page heading | Existing h1: 32px/650; mobile page heading 28px |
| Section heading | Existing h2: 17px/650; panel and modal overrides documented in App.css |
| Small explanatory text | 12px baseline, with existing component-specific rules |
| Expanding description values | 18px with a 28px line-height |
| Monetary columns | Tabular numerals; amounts and headers right-aligned |

Inter is preferred only if available; no bundled or remotely downloaded font was
added. The actual rendered font may be the platform fallback. Do not claim that
Inter has been installed or loaded. Existing small table/navigation text is not
redesigned by this form-readability update.

## Color tokens

| Token | Value | Use |
|---|---|---|
| `--color-text-primary` | `#24352f` | Main foreground |
| `--color-text-field` | `#263c31` | Input/select values |
| `--color-text-muted` | `#77857c` | Explanatory text |
| `--color-page` | `#f7f8f4` | Page background |
| `--color-surface` | `#fff` | Form surface |
| `--color-border-control` | `#dce3dc` | Field borders |
| `--color-action-primary` | `#28674e` | Primary action background |
| `--color-focus` | `#d6a848` | Visible keyboard focus outline |

Other legacy component colors remain defined in App.css. Do not use color alone
to communicate financial completeness or a validation error. Contrast auditing of
the complete application remains separate from these dimension/text-fit checks.

## Form controls

| Property | Standard |
|---|---|
| Single-line height/minimum height | 60px (`--form-control-height`) |
| Value font | 18px (`--form-control-font-size`) |
| Horizontal padding | 16px (`--form-control-padding-x`) |
| Border | 1px solid control-border token |
| Radius | 9px (`--form-control-radius`) |
| Width | 100% of assigned grid track; equal-width tracks for comparable fields |
| Box sizing | Border-box |
| Select arrow reserve | 36px right padding, custom 16px chevron at 12px from the edge |
| Text alignment | Left; normal line-height and no fixed vertical padding to clip a native select value |
| Focus | Existing 3px focus-color outline with 4px offset |

Text, number, date, time and select controls share the same single-line height.
Search has a 60px outer container and a borderless inner field that fits within
its borders. Checkboxes/radio buttons keep their compact native purpose; file,
range and color controls are explicit exceptions rather than forced into a text
field. Existing textarea use cases can remain multiline.

Native selects use `appearance: none` / `-webkit-appearance: none` to prevent
Safari's short native control from overriding shared dimensions. This changes
presentation only; keyboard operation and native option selection remain intact.
Option captions must be concise enough to fit the narrow layout: e.g. Before
funding / After reward use / Unknown, with explanatory text for full semantics.
Stored enum values and financial meanings do not change with shorter captions.

## Long text and layout

- Product names and discount descriptions span the complete form row.
- They use AutoSizeTextArea, start at the 60px standard, wrap and expand on value
  or width changes. Growth is intentional; text must not be vertically clipped.
- Outside receipt-entry/editing cards, short fields use two equal `minmax(0, 1fr)` tracks when the dialog has room.
- The modal is a named inline-size container. At usable dialog width 680px or
  below, its form grids become a single column, independently of browser width.
- At viewport width 600px or below, form grids also stack. Content must not
  increase the page width or hide critical controls.
- Full-width descriptions and growing text areas are documented exceptions to
  identical field widths/heights, because showing the complete value takes priority.
- Labels remain above controls; grid rows align on the controls' lower edge.
- Spacing tokens are 8px label gap and 16px field spacing; legacy grid gap is 18px.
  Receipt cards and editor padding remain component rules in App.css.

## Interaction and states

Keep accessible labels associated with each control, including expanding text
fields. Required fields retain native required behavior. Existing read-only/busy
states disable writes; focus outlines remain visible. A reason/review/confirmation
step remains part of receipt mutations. Styling must never imply that an unknown
financial value is verified, change a discount classification, or create a save.

## Verification and maintenance

Run typecheck, lint, frontend unit/component tests and production build. Browser
receipt fixtures verify metadata, date/time, regular-price/quantity and discount
controls share the standard height; expanding descriptions have at least that
height and their scroll height fits their client height. Narrow dialogs use
single columns; wider layouts retain equal short-field widths. Exercise the
workflow in WebKit and desktop/mobile Chromium.

Receipt fixture browser tests block service workers so synthetic API routes remain
isolated in every engine. Other PWA tests keep their original worker behavior.
WebKit tests do not certify the installed Safari version or a physical iPhone.
Private/real database writes are not part of layout verification.

When changing a token or component rule, update this guide, the affected SRS
UI-002 acceptance and SDD 5.2 design when behavior changes, and relevant dimension/
text-fit regressions. Keep exact run results in VERIFICATION.md. These files remain
subject to the repository's existing docs/ Git-ignore policy.

## Revision 1.6 — account and receipt components

New account and printed-footer controls inherit tokens.css: 60px minimum input/select height and 18px value font. Receipt forms retain full-width single-column product/editor controls and expanding descriptions. Account activity tables use existing horizontal table-wrap scrolling on narrow screens. Keep account names clickable, balances before activity, date range labels explicit, receipt links readable, source reward details collapsed under View Reward and UUIDs collapsed under Technical Details. Title-case account headings/display labels while preserving brand spelling and stored provider keys. Labels use “Have you entered all payments shown on this receipt?” with Yes / Not finished / Not sure. Explicit “Delete receipt-wide discount” buttons work without submitting/validating the form.


## Revision 1.7 — receipt RR correction and matching

Use a native details/summary section labeled Printed Savings & Walgreens Cash — Show / Hide. In edit forms it follows all payment rows; saved observations remain collapsible. Put Used [promotion] and View Reward after totals and printed observations. Matching controls name the unused coupon by amount, promotion, earned receipt/date and expiry; show a reason field and explicit Match RR action. Keep 60px controls/18px values. Unresolved payment help explains the action, rather than showing a bare UUID as the primary instruction. Validation names the invalid field or relationship.


## Revision 1.8 — RR without an earning receipt

Below each unmatched RR-use selector, offer Add RR without an earning receipt even when no eligible recorded source exists. Use the shared control sizes, fixed displayed amount, description/source-note fields, optional date controls and a labeled confirmation checkbox. Explain that blank dates remain unknown and no additional financial amount is posted. Source details show Earning receipt not recorded rather than an empty/fabricated receipt link.


## Revision 1.9 — Rewards points-account display

On the Rewards pages, only MyPoints, Fetch, P&G Brand (PGBRAND), Swagbucks (SWAGBUCK) and Merryfield show points information. Other account detail pages hide native-point balances, activity columns, point-valuation text, converted point quantities and the Original Points opening field, even if older records contain native units. Non-points opening forms use Balance Basis and require a dollar amount; new requests send points:null. The mixed account list shows a points column only when an allowed account is listed, with a dash for other accounts. This is Rewards presentation policy; receipt/CLI behavior, stored units and Java financial calculations remain intact. Existing 60px control heights and 18px value text remain unchanged.


## Revision 1.10 — independent Rewards inventory

Rewards Without Receipts is a shared panel on the Rewards overview and Register Rewards detail page, with Add reward without a receipt, a labeled Register Rewards type, description/amount, optional dates and unused-coupon confirmation. Use existing 60px/18px controls and expanding description input; show inventory in a horizontally scrollable table. Unknown dates show Not recorded. Matching unknown-history standalone coupons requires a clearly labeled availability checkbox. Preserve the five-account points-only display policy.


## Revision 1.11 — visible recorded receipt calculations

Replace blank calculated-total/purchase-difference/payment-difference rows with Receipt calculation checks. Show both total interpretations in a scrollable comparison table when basis is unknown; show the selected scenario when known. Use numeric money values, explicit Provisional — recorded entries only status, difference definitions and actionable verification instructions. Never style a provisional zero as proof of completeness. Existing control/font tokens remain unchanged.


## Revision 1.12 — simplified receipt totals display

Per user request, receipt totals/review no longer displays Calculated total on recorded basis, its before/after-reward alternatives, or the Receipt calculation checks table. Saved printed observations also hide Store Coupon Savings. Store coupon input remains available in Add/Edit Receipt; existing saved observations, API calculationChecks, Java formulas, strict validation, Actual OOP, Total Saved, legacy details and history remain unchanged. This supersedes revision 1.11 UI visibility only. No control-size, font, color or layout-token changes.


## Revision 1.13 — reuse existing files and align CLI rebate display

Named font, color, control-size and spacing tokens now live directly in frontend/src/index.css; component layout remains in App.css, expanding textarea behavior in components/ui.tsx. No visual dimensions change. Shared footer/item controls are in ReceiptCreate.tsx; saved receipt edit/match in ReceiptDetail.tsx; Rewards in WorkspacePages.tsx. Label the existing CLI measure Total Paid after rebates, preserve negative values and display Cannot calculate yet with the missing input when the CLI value is unavailable. Owner development constraint: edit and reuse existing files. Discuss any proposed new application, test or documentation file with the owner before creating it. This revision consolidates the earlier added helpers/components/tests into pre-existing files and does not add source or documentation files.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.

### Receipt entry breakdown and field errors — October 23, 2026

Add Receipt now provides a Review entered breakdown button near the top, in addition to the existing calculated review action. The Java preview review lists product descriptions, regular prices, quantities, recorded extended prices and individual attached discounts; shared/receipt-wide discounts appear separately with their selected products. Existing tax, tender, reward-used, earned-benefit and savings calculations remain in the financial review. No additional browser money calculations or financial events are introduced.

Receipt-entry invalid fields receive a red border and pale red background, aria-invalid, and a descriptive error. Validation scrolls to and focuses the first invalid control; collapsed containing details are opened. Received reward confirmations explain the original unit, compare exact decimal strings without Number conversion, and show an inline error if the confirmed amount differs. Equivalent values such as 5 and 5.00 remain valid. Correct Reward amount first when the actual received amount differs. Points and their saved USD valuation remain separate. New-earning API validation identifies the offer/provider and expected amount/unit rather than displaying status-only workflow wording. Existing saved-reward status restrictions remain unchanged.

The draft information message is explanatory, not an error: the receipt is unsaved until review and Save receipt; closing discards unsaved input. It now says this in plain language. Existing files edited: frontend/src/features/receipts/ReceiptCreate.tsx; frontend/src/features/receipts/RewardFields.tsx; frontend/src/index.css; src/main/java/coupons/api/EarningInput.java. Existing regressions: ReceiptCreate.test.tsx and src/test/java/coupons/api/ReceiptInputTest.java. No new files or database-schema changes.

### Multiple Register Rewards used during receipt creation — October 23, 2026

Add Receipt → Register Rewards used now offers Add RR used and removable individual rows, each with one dollar amount and optional description. Enter $5, $5 and $4 as three separate coupons; duplicate dollar values remain distinct through stable event IDs. Blank descriptions default to Register Rewards used. The review shows each coupon and the Java-calculated RR total. These receipt-wide deductions must not be entered again as discounts or payment tenders. Existing post-save matching offers a separate source match for each use; matching does not create another deduction. Edit Receipt already supports separate RR uses and is unchanged.

Receipt preview/create accept rrUses:[{eventId,amount,recordedDescription?}] with at most 100 positive entries and unique event IDs. The legacy rrUsed/rrUsageId request remains supported; a nonzero legacy amount alongside rrUses is rejected to prevent double counting. Existing UnidentifiedRR observations store the separate uses; no schema/model migration or new financial formula is introduced. Preview tokens and stable save retries retain the individual IDs.

Existing files updated: frontend/src/features/receipts/ReceiptCreate.tsx; src/main/java/coupons/api/ReceiptInput.java; frontend/src/features/receipts/ReceiptCreate.test.tsx; src/test/java/coupons/api/ReceiptInputTest.java. No new files. Regression coverage verifies $5+$5+$4 yields $14, equal coupons have different IDs, preview/save preserve identities, and zero/duplicate-ID/mixed legacy input is rejected.

Product entry disclosure: Add Receipt product cards now use native keyboard-accessible Show / Hide disclosures, initially expanded. Each summary keeps the product number, description, quantity and regular price visible. Collapsing retains all inputs and attached discounts; validation opens a collapsed invalid product before focusing its field. Existing ReceiptCreate.tsx updated; no new files or financial changes.

### Simplified new reward entry and collapsible sections — October 23, 2026

New Fetch point earnings automatically receive the existing worksheet valuation of 1000 points = 1 USD in EarningInput when no explicit valuation is supplied. The UI hides Fetch manual valuation fields; 25 points retain the exact $0.025 saved value. This is a worksheet valuation, not a redemption or current market-rate guarantee. Existing explicit valuations and saved receipts are preserved. Other providers keep their existing valuation entry.

New reward entry records the entered amount once. Choosing Printed at register or Added to program account uses that same amount and its original USD/points unit in the existing confirmed payload. Duplicate amount/unit entry and the repeated confirmation checkbox are removed from RewardFields; EXPECTED remains unconfirmed, and backend saved-reward status protections remain unchanged. P&G promotion guidance appears under Rewards earned or expected: Other provider → P&G Brand, offer description, products and Buy/Spend requirement. Receipt-scan qualification messages explain that eligibility checks are separate from recording/valuing rewards, without misleading zero-quantity/unknown-dollar displays.

All Add Receipt sections use keyboard-accessible Show / Hide disclosures: receipt details, products, shared discounts, tax/rewards used/payments, printed footer, rewards earned/expected, and evidence. Draft values remain present while collapsed; validation opens every enclosing disclosure before focusing an invalid input. Existing files updated: ReceiptCreate.tsx, RewardFields.tsx, rewardStatus.ts, EarningInput.java, ReceiptCreate.test.tsx, ReceiptInputTest.java. No new files or database changes.

Expected rewards quick entry: RewardFields now explains that provider and amount with Expected — waiting to receive are sufficient. Product selections, promotion rules, valuation, evidence and submission details sit in a closed Optional reward details disclosure; historical copying has its own optional disclosure. ReceiptRewardForm places qualification diagnostics in Optional qualification review, and explains that expected rewards do not increase confirmed benefits. No fields or backend validation requirements were added. Existing entered optional values are preserved. Existing RewardFields.tsx, ReceiptRewardForm.tsx and browser.spec.ts updated; no new files.

Received reward entry simplification: manually entered new rewards now default to received (CREDITED for account rewards, PAID/printed for RR); Expected remains an explicit option for rewards still awaited. Copying historical terms still defaults to Expected and does not copy receipt confirmation. Updating a saved reward status uses its existing recorded amount/unit automatically without duplicate input or checkbox; the existing revision-checked API payload still carries those exact values and explicit confirmation. No historical rewards are automatically reclassified. Receipt qualification diagnostics are collapsed under Optional qualification details; unknown eligibility does not negate a recorded receipt of rewards. Existing files: rewardStatus.ts, RewardFields.tsx, EarningStatusForm.tsx, EarningStatusForm.test.tsx, ReceiptDetail.tsx. No new files or backend financial-rule changes.

Automatic P&G/Fetch point valuation: new API reward entries calculate Fetch at 1000 points = 1 USD and P&G Brand/P&G Good Everyday/PG at 250 points = 1 USD, using existing worksheet rates. Native points remain stored separately; 100 P&G points = $0.40 and 25 Fetch points = $0.025 before display rounding. Expected rewards remain excluded from confirmed savings. RewardFields hides manual conversion for both programs; rewardBody sends no hidden stale valuation for those point providers. EarningInput calculates the USD valuation and records its basis. Previously saved explicit valuations are not overwritten. No payout is generated. Existing EarningInput.java, RewardFields.tsx, rewardStatus.ts and ReceiptInputTest.java edited; no new files.

Ibotta account credit: RewardFields and EarningStatusForm label CREDITED as Added to Ibotta account, separate from Expected (waiting for credit). Typing Ibotta as the provider selects the cash benefit form. Account credit is earned value held in Ibotta; the user's $20 withdrawal minimum is displayed as context, not an external eligibility guarantee. Rewards → Ibotta has an Account Credit & Withdrawals section with the recorded period-end balance and minimum. A withdrawal is a separate transfer/payout and must not generate another earning. Existing conversion records remain unchanged; this update does not initiate withdrawals or introduce a payout command. Existing RewardFields.tsx, EarningStatusForm.tsx and WorkspacePages.tsx edited; no new files or financial-rule changes.

Decimal entry accepts a leading decimal point: .98 is equivalent to 0.98 for receipt tax, prices, discounts, RR, payments, footer observations and API decimal amounts. Exact BigDecimal/string precision is retained; currency display supplies the leading zero. Whole-number quantity rules remain unchanged; lone dots, negative nonnegative amounts and malformed decimals are rejected. Existing ReceiptCreate.tsx, ReceiptDetail.tsx, WorkspacePages.tsx, api/format.ts, ReceiptInput.java, ReceiptEarningStatusUpdate.java and existing receipt tests edited. No new files or database changes.

Rewards date browsing: Rewards & Accounts adds account-name search, Balance through date, and Month to date (current local date). The main table's Month Start, Day Start, Day Close and allowed points columns are recalculated in Java for the selected date; future activity is excluded from those balances. Clicking an account opens the selected month's first day through that date. The redundant Open Account/Choose Account dropdown is removed; names remain clickable. GET /workspace?asOf=YYYY-MM-DD returns existing workspace balance projections for the chosen date; omitted asOf preserves existing latest-receipt behavior. Search filters account rows only. Unknown openings remain unknown. Existing PostgresPlatformRepository.java, CouponApiService.java, api/client.ts, WorkspacePages.tsx and ReceiptCreate.test.tsx updated; no new files or stored-data changes.

Walgreens Cash quick option: RewardFields now offers Walgreens Cash reward type → Member rewards for purchase, alongside Other promotion / reward. Selecting it fills the offer description, sets whole-receipt scope and a purchase-based requirement without a spend/quantity threshold. The user enters the amount once; status selection and existing credit calculations remain unchanged. Available in both Add Receipt and post-save Add rebate or reward. Existing RewardFields.tsx and ReceiptRewardForm.test.tsx edited; no new files or database changes.

Receipt product lookup: existing active receipt history populates workspace.productLookup with SKU, description, most recent regular price, purchase date and source receipt. Latest revisions are deduplicated before filtering; deleted receipts are excluded. SKU leading zeros are preserved, and conflicting descriptions for one SKU remain separate selectable choices. Add Receipt suggests up to eight prefix matches as SKU is typed. Use this product fills description/SKU/last regular price and quantity 1; no historical promotions, coupons or rewards are copied. The user reviews the current price and discounts. This is a read-only product reference, not an import into on-hand inventory; historical purchases do not prove remaining stock. Existing PostgresPlatformRepository.java, types/platform.ts, App.tsx, ReceiptCreate.tsx, ReceiptCreate.test.tsx and SavingsReportTest.java updated. No new files or database tables.

Product discount descriptions: new item-attached discounts start with the product description's first word, e.g. Ven Sale Promotion. Selecting Manufacturer or Store Coupon updates the generated label; custom text remains editable. On leaving a discount description, the product prefix is supplied if missing and each word's initial letter is capitalized. Add Receipt product descriptions also capitalize word initials on blur without changing cursor position during typing. Existing historical records are not bulk rewritten. Existing api/format.ts, ReceiptCreate.tsx and ReceiptDetail.tsx updated; no new files or calculation changes.

New-entry focus: Add Product and Add Another Line place the cursor in the new product description. Adding a product discount focuses its new description; adding a shared/receipt-wide discount focuses its label. Add Receipt and Edit Receipt support this, using focus state and autofocus on newly mounted inputs, without moving focus during ordinary typing. Existing ReceiptCreate.tsx, ReceiptDetail.tsx and ReceiptCreate.test.tsx edited; no new files or financial changes.

Shared discount selector correction: Add Receipt retains the original shared-entry presentation even when only one product has been selected so far. Previously, selecting the first item reclassified the form row into the item section and hid the remaining product checkboxes. A draft-only shared marker keeps the selector available; selecting multiple items submits their stable IDs and quantities once, with wholeReceipt false. Item-created discounts remain with their item. The draft marker is not sent to the API and does not affect financial calculations. Existing ReceiptCreate.tsx and ReceiptCreate.test.tsx updated; no new files or saved-data changes.

PayPal and Venmo reward options: both appear in Common providers and the provider suggestions when adding a rebate/reward. Selecting or typing either uses a CASH/USD benefit rather than points; received is the default and the amount is entered once (e.g. 1.90). This records a new rebate paid through that provider only when saved. An existing rebate transferred from Ibotta or another account should remain a separate transfer, not another earning. Existing RewardFields.tsx and ReceiptRewardForm.test.tsx updated; no new files or personal receipt changes.

### Receipt total detail reports — October 24, 2026

Receipts period totals Regular price including tax, Actual OOP, Total amount saved and Total Paid after rebates are clickable. Each opens an existing-style modal with all active records in the selected day/month/inclusive range or all time, independent of receipt-list search/status/page. Reports support item/SKU/store/receipt/date/time/payment-type text search and date/time newest/oldest, store A–Z and item/payment-type A–Z sorting. Receipt links open source details. Regular price shows individual product regular extended values plus separate sales-tax rows. OOP shows individual cash/card tenders with period cash/card totals and matching cash/card subtotals; WC/RR/gift cards are excluded. Saved/net views show one contribution per receipt with its product descriptions. Period total remains unchanged by report search; Matching subtotal follows visible rows. Unknown contributions remain unknown.

Read-only authenticated GET /receipts/report?metric=regular|oop|saved|net&from=&to=&search=&sort= returns metric, existing Savings summary, rows, matchedTotal, matchedCash and matchedCard. Bounds are inclusive and optional; latest active revisions only. All money multiplication/aggregation uses Java BigDecimal and existing SavingsReport rules; the browser formats exact decimal strings. This introduces no saved records, schema or formula changes.

Existing files updated: src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/types/platform.ts; frontend/src/pages/WorkspacePages.tsx; frontend/src/App.css; src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files.

Register Rewards used disclosure: Add Receipt and Edit Receipt give the RR-used subsection its own initially expanded Show / Hide control, displaying the entered coupon-row count. Users can collapse all RR rows after entry and reopen them without losing amounts, descriptions or match identities. No financial or storage changes. Existing ReceiptCreate.tsx and ReceiptDetail.tsx updated; no new files.

Confirmed benefits detail: clicking Confirmed benefits opens the selected-period searchable/sortable report of all receipt earnings with native amounts/units, dollar values, confirmed/unconfirmed/rejected grouping and source receipt links. Confirmed and unconfirmed dollar subtotals are separate; rejected rewards contribute to neither. Unknown point valuations remain unknown rather than zero. New metric=benefits in GET /receipts/report returns original earning and receipt revision for existing status commands. Confirm / update status opens the existing EarningStatusForm and scrolls/focuses its control; it uses recorded amount/unit once, revision checks and stable retries. Saving refreshes report and main workspace totals. Read-only users cannot change status. Existing WorkspacePages.tsx, types/platform.ts, PostgresPlatformRepository.java, ReceiptCreate.test.tsx and SavingsReportTest.java updated; no new files or new financial formulas.

Rewards Without Receipts placement: the existing standalone RR inventory panel now appears at the bottom of the main Receipts page, after the receipt list/pagination and editing guidance. It is removed from the Rewards main balance screen; it remains accessible in Register Rewards account details. Add reward without a receipt and the reward inventory table retain their existing behavior. Existing WorkspacePages.tsx and browser.spec.ts updated; no new files, backend changes or saved-data edits.

Rewards Overview (approved definition): Total Rewards = Available Balances + Expected / Unconfirmed, shown separately on the Rewards main page for Balance through. Available sums positive recorded account closing balances after recorded spending/transfers; confirmed earnings are not added again. Expected includes outstanding active-receipt rewards dated on/before the selected day, excluding received, rejected and expired expected entries. Prior-month outstanding rewards remain included. Native points use saved/automatic USD valuations. Missing balance/valuation leaves the affected full total unknown and shows known subtotals/providers for review. Account search does not change these overview totals. GET /workspace adds rewardsOverview with available, expected, total, known subtotals and completeness context. Existing PostgresPlatformRepository.java, types/platform.ts, WorkspacePages.tsx and SavingsReportTest.java updated; no new files or personal record changes.

Walgreens-only printed footer: Printed Savings & Walgreens Cash appears only for Walgreens store names, case-insensitively with surrounding spaces and optional store-number suffixes supported. Add Receipt, Edit Receipt and saved receipt Totals use the same isWalgreensStore check. New non-Walgreens receipts omit any stale Walgreens footer draft from submission. Historical observations are preserved rather than deleted when hidden. Existing api/format.ts, ReceiptCreate.tsx, ReceiptDetail.tsx and ReceiptCreate.test.tsx updated; no new files or financial changes.


Rewards page layout: Search accounts, Balance through and Month to date share one aligned row on wider screens and stack on narrow screens. Rewards overview explanatory text has interior spacing; Total Rewards, Available Balances and Expected / Unconfirmed are centered. Individual account pages show From/Through followed by Account Balances before activity. Account Activity can expand/collapse, and each activity description expands to reveal reward connections, valuation notes and technical details while its transaction amounts remain visible. Register Rewards places Rewards Without Receipts at the bottom, after other account content. Successful receipt creation displays “Receipt saved.” Financial calculations and saved records are unchanged.


Rewards filter alignment correction: reset the Search accounts and Balance through labels’ bottom margins within rewards-account-filters. The Month to date button uses the shared form-control-height token and aligns to the bottom of the same grid row, keeping its top and bottom level with the two inputs. Narrow-screen stacking remains supported.


Receipt browsing: Search receipts stays visible. Purchase day, Month, Start date, End date, Month to date, Sort receipts, Receipt status and Clear filters are grouped in a More filters — Show / Hide disclosure, collapsed initially. Expanding reveals the existing responsive filter grid; collapsing preserves selections and their effect on receipt results and period totals.


Receipt entry product action formatting: the calculation note uses two readable lines with spacing. Add discount, Add another line for this product and Remove product appear as separate bordered buttons with a 12px gap and at least 44px height. Actions wrap on wider screens and stack at full width on narrow screens; removal uses a distinct text color. Existing input focus, duplication, discount and removal behavior remains unchanged.


CVS receipt entry and correction now provide a collapsible ExtraBucks (ECB) used section. Each coupon has a separate amount and description, plus add/remove actions. Uses apply to the whole receipt, never individual products. Existing OTHER payment records with accountName ECB and explicit CHECKOUT_DEDUCTION roles are reused, retaining stable payment IDs and notes; no new schema or endpoint is introduced. Create input accepts the existing Payment accountName field. ECB rows are excluded from the generic payment editor to prevent duplicate entry and are listed in the create review breakdown. ECB uses are separate from Register Rewards, manufacturer coupons and cash/card Actual OOP. This change records descriptions, not source-earning matching.


Manufacturer product coupons now have an optional Required purchase quantity field in Add and Edit Receipt, for terms such as buy 2. It records the coupon’s required product count, not coupon count or purchased quantity. The total deduction remains entered once and is not multiplied by the requirement. Positive whole numbers are accepted; blank means not recorded. The existing CouponApplied.requiredQuantity model and serialized field are reused, exposed in create/correction requests and edit projections, and shown on saved receipt discount details. Existing requirements are retained when omitted by API callers; explicit null clears one. No database schema or new files are needed.


CVS earned rewards: Add Receipt and the saved receipt Add rebate or reward form default to CVS ExtraBucks (ECB), stored as THIRD_PARTY_REBATE / STORE_REWARD with provider ECB so earned and used activity share the ECB account. Enter the earned USD amount and required reward name once. The CVS reward type selector includes 2% purchase earnings; record the CVS-reported amount when known, mark Expected while awaiting credit, or add it later via the saved receipt Rewards tab. No automatic 2% calculation or invented posting date is applied. New ECB entries default to whole receipt with optional associations available. CVS receipt entry, editing and totals hide unused Walgreens-specific RR/WC controls and footer; existing unusual saved entries are retained for correction. Shared financial wording uses redeemed rewards rather than Walgreens-specific terminology.


Receipt Rewards tab presentation: each earned/expected reward is a bordered, padded card with a muted program label, prominent promotion name, separate status badge and product scope, and a prominent formatted dollar amount (USD) or native points. Product association and status actions use separate spaced buttons. Qualification details remain collapsible. Expected amount is shown only when there is no confirmed amount, avoiding the repeated expected/received amount on confirmed cards. Cards stack on narrow screens; financial values and saved records are unchanged.


Member rewards without receipts: Walgreens Cash, Register Rewards, ECB and Walmart Cash individual account pages provide Add member reward without a receipt with reward date, positive USD amount and description. POST /accounts/member-rewards is an authenticated idempotent write using the existing AccountAdjustment and provider_account_adjustments ledger, with actor/time and Member reward description. It increases account activity/closing balance and overview availability; it does not create receipts, change receipt savings, or invent openings. Existing ledger beginning balances/order rules still apply. Register Rewards coupon inventory remains a separate non-earning record for matching; avoid counting the same earning again. Walgreens Cash Provider-Reported Observations is initially collapsed and supports Show / Hide without changing printed observations.


Dashboard sections Your next shopping trip, Rewards to keep track of and Recent receipts each have independent accessible Collapse / Expand buttons with aria-expanded and aria-controls. Sections start expanded, preserve content and retain navigation actions when collapsed. Confirmed benefit value explanatory text explicitly identifies receipt earnings in the selected period, separate from account closing balances. Dashboard financial formulas and personal receipt/account records are unchanged. Spreadsheet comparisons must compare the same date range and distinguish opening balances plus net account activity from receipt earnings; account balances are not substituted into receipt savings.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


ECB non-receipt member rewards now support optional expirationDate in POST /accounts/member-rewards and an ECB expiration date input. Dates before the reward date are rejected. AccountAdjustment retains the date in its existing serialized payload; its legacy eight-argument constructor and older records default to unknown expiration. No new SQL column or migration is required. Account activity displays Expires YYYY-MM-DD. The date is informational: it does not automatically debit balances or infer remaining coupon value after spending. Existing saved financial amounts are unchanged.
Changed files: WorkspacePages.tsx, CouponApiService.java, AccountAdjustment.java, ProviderBalanceSummary.java, ReceiptCreate.test.tsx and ProviderBalanceSummaryTest.java.


Receipt-earned ECB expiration: new CVS ExtraBucks/store reward entries expose Reward expiration date in the main reward form. Existing receipt reward cards display the saved expiration and offer Edit products / expiration. The existing EarningLinksForm includes an optional expiration date, prefilled from the saved earning; blank clears it. PUT /receipts/{receiptId}/earnings/{earningId}/associations accepts optional expirationDate, preserves it when omitted, and rejects dates before the recorded purchase day. Omitted product selections retain their prior values. Saving preserves earning ID, expected/confirmed amounts, valuation, status, payments and evidence, and records a receipt revision. This reuses the existing Earning.expirationDate field and existing endpoint; no schema migration or new files. Expiration entry does not automatically create an account debit.
Changed files: RewardFields.tsx, EarningLinksForm.tsx, ReceiptDetail.tsx, platform.ts, CouponApiService.java, ReceiptCreate.test.tsx and CouponApiServiceTest.java.


Dashboard whole-card collapse correction: dashboard grid items align to the start rather than stretching to the height of the adjacent expanded card. All three dashboard cards have an explicit collapsed class and hidden bodies take no layout space. A collapsed card shrinks to its header, retaining its title/subtitle, Expand control and navigation link; expanding restores the full content. Header actions wrap with clear spacing. Existing browser.spec.ts verifies actual card/header heights and independent expand/collapse at desktop, Safari and mobile viewport sizes.


Calculated Account Balances card spacing: explanatory/loading/empty text uses 23px horizontal insets and 18px bottom spacing with 1.7 line height. On screens up to 600px, horizontal insets are 18px to align with the card heading. Notices use the same insets; account tables retain their existing scrolling layout. Changed WorkspacePages.tsx and App.css; no balance calculations or saved records changed.
