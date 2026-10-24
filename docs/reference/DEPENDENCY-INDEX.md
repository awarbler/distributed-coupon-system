# Source import dependency index

## Revision history

| Version | Date | Change | Verification |
|---|---|---|---|
| 1.0 | 2026-10-21 | Original snapshot | Static inspection |
| 1.2 | 2026-10-22 | Receipt evidence/reconciliation, distinct purchase/scan and partial benefit values | See VERIFICATION.md |


Inspected: October 21, 2026 (America/Chicago). Base Git commit: `fa3b8debc070cb5de990cbe9eb420fe1e56b7c06`. This reference describes the working tree, including pre-existing uncommitted checkout changes; it does not prove what is deployed online. No application tests were run for this documentation-only task.

Imports show declared dependencies, not runtime invocation. Same-package Java references and dynamic dependencies do not require imports and are not fully represented here. The reviewed workflow connections are in SYSTEM-OVERVIEW.md.

| Source | Import |
|---|---|
| [frontend/playwright.config.ts:1](../../frontend/playwright.config.ts#L1) | `import { defineConfig, devices } from '@playwright/test'` |
| [frontend/src/App.test.tsx:1](../../frontend/src/App.test.tsx#L1) | `import { render, screen, within } from '@testing-library/react'` |
| [frontend/src/App.test.tsx:2](../../frontend/src/App.test.tsx#L2) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/App.test.tsx:3](../../frontend/src/App.test.tsx#L3) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/App.test.tsx:4](../../frontend/src/App.test.tsx#L4) | `import App from './App'` |
| [frontend/src/App.test.tsx:5](../../frontend/src/App.test.tsx#L5) | `import { ApiClient, ApiError } from './api/client'` |
| [frontend/src/App.test.tsx:6](../../frontend/src/App.test.tsx#L6) | `import { workspace } from './api/fixtures.test-support'` |
| [frontend/src/App.test.tsx:7](../../frontend/src/App.test.tsx#L7) | `import { EditDialog } from './features/shopping/EditDialog'` |
| [frontend/src/App.tsx:1](../../frontend/src/App.tsx#L1) | `import { useCallback, useEffect, useState } from 'react'` |
| [frontend/src/App.tsx:2](../../frontend/src/App.tsx#L2) | `import { ClipboardList, Coins, Download, LayoutDashboard, Leaf, LogOut, Menu, Package, ReceiptText, RefreshCw, ShieldCheck, ShoppingBag, Ticket, Wallet, X } from 'lucide-react'` |
| [frontend/src/App.tsx:3](../../frontend/src/App.tsx#L3) | `import type { FormEvent } from 'react'` |
| [frontend/src/App.tsx:4](../../frontend/src/App.tsx#L4) | `import { ApiClient } from './api/client'` |
| [frontend/src/App.tsx:5](../../frontend/src/App.tsx#L5) | `import { Loading, Modal, Notice } from './components/ui'` |
| [frontend/src/App.tsx:6](../../frontend/src/App.tsx#L6) | `import { EditDialog } from './features/shopping/EditDialog'` |
| [frontend/src/App.tsx:7](../../frontend/src/App.tsx#L7) | `import type { Edit } from './features/shopping/EditDialog'` |
| [frontend/src/App.tsx:8](../../frontend/src/App.tsx#L8) | `import { ReceiptCreate } from './features/receipts/ReceiptCreate'` |
| [frontend/src/App.tsx:9](../../frontend/src/App.tsx#L9) | `import { ReceiptDetail } from './features/receipts/ReceiptDetail'` |
| [frontend/src/App.tsx:10](../../frontend/src/App.tsx#L10) | `import { WorkspacePages } from './pages/WorkspacePages'` |
| [frontend/src/App.tsx:11](../../frontend/src/App.tsx#L11) | `import type { Page } from './pages/WorkspacePages'` |
| [frontend/src/App.tsx:12](../../frontend/src/App.tsx#L12) | `import type { Workspace } from './types/platform'` |
| [frontend/src/App.tsx:13](../../frontend/src/App.tsx#L13) | `import './App.css'` |
| [frontend/src/App.tsx:14](../../frontend/src/App.tsx#L14) | `import type { Plan } from './types/platform'` |
| [frontend/src/App.tsx:15](../../frontend/src/App.tsx#L15) | `import { CheckoutForm } from './features/shopping/CheckoutForm'` |
| [frontend/src/api/client.test.ts:1](../../frontend/src/api/client.test.ts#L1) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/api/client.test.ts:2](../../frontend/src/api/client.test.ts#L2) | `import { ApiClient, ApiError, decode } from './client'` |
| [frontend/src/api/client.test.ts:3](../../frontend/src/api/client.test.ts#L3) | `import { decimal, integer, money } from './format'` |
| [frontend/src/api/client.test.ts:4](../../frontend/src/api/client.test.ts#L4) | `import { workspace } from './fixtures.test-support'` |
| [frontend/src/api/client.ts:1](../../frontend/src/api/client.ts#L1) | `import { parse } from 'lossless-json'` |
| [frontend/src/api/client.ts:2](../../frontend/src/api/client.ts#L2) | `import type {Workspace,Receipt,History,Totals,PlanCheckout,} from '../types/platform'` |
| [frontend/src/api/fixtures.test-support.ts:2](../../frontend/src/api/fixtures.test-support.ts#L2) | `import type { Savings, Workspace } from '../types/platform'` |
| [frontend/src/api/format.ts:1](../../frontend/src/api/format.ts#L1) | `import type { Decimal } from '../types/platform'` |
| [frontend/src/components/EvidenceField.tsx:1](../../frontend/src/components/EvidenceField.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/components/ui.tsx:1](../../frontend/src/components/ui.tsx#L1) | `import { useEffect, useRef } from 'react'` |
| [frontend/src/components/ui.tsx:2](../../frontend/src/components/ui.tsx#L2) | `import type { ReactNode } from 'react'` |
| [frontend/src/components/ui.tsx:3](../../frontend/src/components/ui.tsx#L3) | `import { ArrowRight, CircleAlert, Inbox, X } from 'lucide-react'` |
| [frontend/src/features/receipts/EarningLinksForm.tsx:1](../../frontend/src/features/receipts/EarningLinksForm.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/receipts/EarningLinksForm.tsx:2](../../frontend/src/features/receipts/EarningLinksForm.tsx#L2) | `import { ApiClient } from '../../api/client'` |
| [frontend/src/features/receipts/EarningLinksForm.tsx:3](../../frontend/src/features/receipts/EarningLinksForm.tsx#L3) | `import { integer } from '../../api/format'` |
| [frontend/src/features/receipts/EarningLinksForm.tsx:4](../../frontend/src/features/receipts/EarningLinksForm.tsx#L4) | `import { ReceiptLineSelector } from './ReceiptLineSelector'` |
| [frontend/src/features/receipts/EarningLinksForm.tsx:5](../../frontend/src/features/receipts/EarningLinksForm.tsx#L5) | `import type { Earning,Receipt } from '../../types/platform'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:1](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L1) | `import { render, screen } from '@testing-library/react'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:2](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L2) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:3](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L3) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:4](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L4) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:5](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L5) | `import type { Earning } from '../../types/platform'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:6](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L6) | `import { EarningStatusForm } from './EarningStatusForm'` |
| [frontend/src/features/receipts/EarningStatusForm.test.tsx:7](../../frontend/src/features/receipts/EarningStatusForm.test.tsx#L7) | `import { rewardChoices, rewardStatusLabel } from './rewardStatus'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:1](../../frontend/src/features/receipts/EarningStatusForm.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:2](../../frontend/src/features/receipts/EarningStatusForm.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:3](../../frontend/src/features/receipts/EarningStatusForm.tsx#L3) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:4](../../frontend/src/features/receipts/EarningStatusForm.tsx#L4) | `import type { Earning } from '../../types/platform'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:5](../../frontend/src/features/receipts/EarningStatusForm.tsx#L5) | `import { Notice } from '../../components/ui'` |
| [frontend/src/features/receipts/EarningStatusForm.tsx:6](../../frontend/src/features/receipts/EarningStatusForm.tsx#L6) | `import { rewardChoices, rewardStatusLabel } from './rewardStatus'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:1](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L1) | `import { render, screen, within, waitFor } from '@testing-library/react'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:2](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L2) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:3](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L3) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:4](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L4) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:5](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L5) | `import { workspace } from '../../api/fixtures.test-support'` |
| [frontend/src/features/receipts/ReceiptCreate.test.tsx:6](../../frontend/src/features/receipts/ReceiptCreate.test.tsx#L6) | `import { ReceiptCreate } from './ReceiptCreate'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:1](../../frontend/src/features/receipts/ReceiptCreate.tsx#L1) | `import { useCallback, useState } from 'react'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:2](../../frontend/src/features/receipts/ReceiptCreate.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:3](../../frontend/src/features/receipts/ReceiptCreate.tsx#L3) | `import { Plus, Trash2 } from 'lucide-react'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:4](../../frontend/src/features/receipts/ReceiptCreate.tsx#L4) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:5](../../frontend/src/features/receipts/ReceiptCreate.tsx#L5) | `import { integer, money } from '../../api/format'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:6](../../frontend/src/features/receipts/ReceiptCreate.tsx#L6) | `import { Modal, Notice } from '../../components/ui'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:7](../../frontend/src/features/receipts/ReceiptCreate.tsx#L7) | `import type { Totals, RewardOffer } from '../../types/platform'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:8](../../frontend/src/features/receipts/ReceiptCreate.tsx#L8) | `import { RewardFields } from './RewardFields'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:9](../../frontend/src/features/receipts/ReceiptCreate.tsx#L9) | `import { newReward, rewardBody } from './rewardStatus'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:10](../../frontend/src/features/receipts/ReceiptCreate.tsx#L10) | `import type { RewardDraft } from './rewardStatus'` |
| [frontend/src/features/receipts/ReceiptCreate.tsx:11](../../frontend/src/features/receipts/ReceiptCreate.tsx#L11) | `import { ReceiptLineSelector } from './ReceiptLineSelector'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:1](../../frontend/src/features/receipts/ReceiptDetail.tsx#L1) | `import { useEffect, useState } from 'react'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:2](../../frontend/src/features/receipts/ReceiptDetail.tsx#L2) | `import { Download, Image as ImageIcon } from 'lucide-react'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:3](../../frontend/src/features/receipts/ReceiptDetail.tsx#L3) | `import type { ApiClient } from '../../api/client'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:4](../../frontend/src/features/receipts/ReceiptDetail.tsx#L4) | `import { date, label, money, shortId } from '../../api/format'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:5](../../frontend/src/features/receipts/ReceiptDetail.tsx#L5) | `import { Badge, Loading, Modal, Notice, Unavailable } from '../../components/ui'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:6](../../frontend/src/features/receipts/ReceiptDetail.tsx#L6) | `import { rewardStatusLabel } from './rewardStatus'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:7](../../frontend/src/features/receipts/ReceiptDetail.tsx#L7) | `import { EarningLinksForm } from './EarningLinksForm'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:8](../../frontend/src/features/receipts/ReceiptDetail.tsx#L8) | `import { ReceiptRewardForm } from './ReceiptRewardForm'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:9](../../frontend/src/features/receipts/ReceiptDetail.tsx#L9) | `import { EarningStatusForm } from './EarningStatusForm'` |
| [frontend/src/features/receipts/ReceiptDetail.tsx:10](../../frontend/src/features/receipts/ReceiptDetail.tsx#L10) | `import type { History, Receipt, Totals, RewardOffer } from '../../types/platform'` |
| [frontend/src/features/receipts/ReceiptLineSelector.tsx:1](../../frontend/src/features/receipts/ReceiptLineSelector.tsx#L1) | `import { money } from '../../api/format'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:1](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L1) | `import { render, screen, waitFor } from '@testing-library/react'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:2](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L2) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:3](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L3) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:4](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L4) | `import { ApiClient } from '../../api/client'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:5](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L5) | `import { ReceiptRewardForm } from './ReceiptRewardForm'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:6](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L6) | `import type { Receipt } from '../../types/platform'` |
| [frontend/src/features/receipts/ReceiptRewardForm.test.tsx:7](../../frontend/src/features/receipts/ReceiptRewardForm.test.tsx#L7) | `import { savings } from '../../api/fixtures.test-support'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:1](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:2](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:3](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L3) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:4](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L4) | `import { Notice } from '../../components/ui'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:5](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L5) | `import { RewardFields } from './RewardFields'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:6](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L6) | `import { newReward, rewardBody } from './rewardStatus'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:7](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L7) | `import type { Receipt, Totals, RewardOffer } from '../../types/platform'` |
| [frontend/src/features/receipts/ReceiptRewardForm.tsx:8](../../frontend/src/features/receipts/ReceiptRewardForm.tsx#L8) | `import { money } from '../../api/format'` |
| [frontend/src/features/receipts/RewardFields.tsx:1](../../frontend/src/features/receipts/RewardFields.tsx#L1) | `import type { RewardOffer } from '../../types/platform'` |
| [frontend/src/features/receipts/RewardFields.tsx:2](../../frontend/src/features/receipts/RewardFields.tsx#L2) | `import { EvidenceField } from '../../components/EvidenceField'` |
| [frontend/src/features/receipts/RewardFields.tsx:3](../../frontend/src/features/receipts/RewardFields.tsx#L3) | `import { rewardChoices } from './rewardStatus'` |
| [frontend/src/features/receipts/RewardFields.tsx:4](../../frontend/src/features/receipts/RewardFields.tsx#L4) | `import { ReceiptLineSelector } from './ReceiptLineSelector'` |
| [frontend/src/features/receipts/RewardFields.tsx:5](../../frontend/src/features/receipts/RewardFields.tsx#L5) | `import type { SelectableLine } from './ReceiptLineSelector'` |
| [frontend/src/features/receipts/RewardFields.tsx:6](../../frontend/src/features/receipts/RewardFields.tsx#L6) | `import { newReward } from './rewardStatus'` |
| [frontend/src/features/receipts/RewardFields.tsx:7](../../frontend/src/features/receipts/RewardFields.tsx#L7) | `import type { RewardDraft } from './rewardStatus'` |
| [frontend/src/features/receipts/rewardStatus.ts:1](../../frontend/src/features/receipts/rewardStatus.ts#L1) | `import { integer } from '../../api/format'` |
| [frontend/src/features/receipts/rewardStatus.ts:2](../../frontend/src/features/receipts/rewardStatus.ts#L2) | `import type { Earning } from '../../types/platform'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:2](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L2) | `import { render, screen, waitFor } from '@testing-library/react'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:3](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L3) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:4](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L4) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:5](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L5) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:6](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L6) | `import type { PlanCheckout } from '../../types/platform'` |
| [frontend/src/features/shopping/CheckoutForm.test.tsx:7](../../frontend/src/features/shopping/CheckoutForm.test.tsx#L7) | `import { CheckoutForm } from './CheckoutForm'` |
| [frontend/src/features/shopping/CheckoutForm.tsx:1](../../frontend/src/features/shopping/CheckoutForm.tsx#L1) | `import { useEffect, useState } from 'react'` |
| [frontend/src/features/shopping/CheckoutForm.tsx:2](../../frontend/src/features/shopping/CheckoutForm.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/shopping/CheckoutForm.tsx:3](../../frontend/src/features/shopping/CheckoutForm.tsx#L3) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/shopping/CheckoutForm.tsx:4](../../frontend/src/features/shopping/CheckoutForm.tsx#L4) | `import type { PlanCheckout } from '../../types/platform'` |
| [frontend/src/features/shopping/CheckoutForm.tsx:5](../../frontend/src/features/shopping/CheckoutForm.tsx#L5) | `import { Modal } from '../../components/ui'` |
| [frontend/src/features/shopping/CouponForm.tsx:1](../../frontend/src/features/shopping/CouponForm.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/shopping/CouponForm.tsx:2](../../frontend/src/features/shopping/CouponForm.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/shopping/CouponForm.tsx:3](../../frontend/src/features/shopping/CouponForm.tsx#L3) | `import { EvidenceField } from '../../components/EvidenceField'` |
| [frontend/src/features/shopping/CouponForm.tsx:4](../../frontend/src/features/shopping/CouponForm.tsx#L4) | `import type { Upload } from '../../components/EvidenceField'` |
| [frontend/src/features/shopping/CouponForm.tsx:5](../../frontend/src/features/shopping/CouponForm.tsx#L5) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/shopping/CouponForm.tsx:6](../../frontend/src/features/shopping/CouponForm.tsx#L6) | `import { integer } from '../../api/format'` |
| [frontend/src/features/shopping/CouponForm.tsx:7](../../frontend/src/features/shopping/CouponForm.tsx#L7) | `import type { Coupon } from '../../types/platform'` |
| [frontend/src/features/shopping/DealForm.tsx:1](../../frontend/src/features/shopping/DealForm.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/shopping/DealForm.tsx:2](../../frontend/src/features/shopping/DealForm.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/shopping/DealForm.tsx:3](../../frontend/src/features/shopping/DealForm.tsx#L3) | `import { EvidenceField } from '../../components/EvidenceField'` |
| [frontend/src/features/shopping/DealForm.tsx:4](../../frontend/src/features/shopping/DealForm.tsx#L4) | `import type { Upload } from '../../components/EvidenceField'` |
| [frontend/src/features/shopping/DealForm.tsx:5](../../frontend/src/features/shopping/DealForm.tsx#L5) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/shopping/DealForm.tsx:6](../../frontend/src/features/shopping/DealForm.tsx#L6) | `import { integer } from '../../api/format'` |
| [frontend/src/features/shopping/DealForm.tsx:7](../../frontend/src/features/shopping/DealForm.tsx#L7) | `import type { Deal } from '../../types/platform'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:2](../../frontend/src/features/shopping/EditDialog.test.tsx#L2) | `import { render, screen } from '@testing-library/react'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:3](../../frontend/src/features/shopping/EditDialog.test.tsx#L3) | `import userEvent from '@testing-library/user-event'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:4](../../frontend/src/features/shopping/EditDialog.test.tsx#L4) | `import { describe, expect, it, vi } from 'vitest'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:5](../../frontend/src/features/shopping/EditDialog.test.tsx#L5) | `import { EditDialog } from './EditDialog'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:6](../../frontend/src/features/shopping/EditDialog.test.tsx#L6) | `import { ApiClient } from '../../api/client'` |
| [frontend/src/features/shopping/EditDialog.test.tsx:7](../../frontend/src/features/shopping/EditDialog.test.tsx#L7) | `import { workspace } from '../../api/fixtures.test-support'` |
| [frontend/src/features/shopping/EditDialog.tsx:1](../../frontend/src/features/shopping/EditDialog.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/features/shopping/EditDialog.tsx:2](../../frontend/src/features/shopping/EditDialog.tsx#L2) | `import type { FormEvent } from 'react'` |
| [frontend/src/features/shopping/EditDialog.tsx:3](../../frontend/src/features/shopping/EditDialog.tsx#L3) | `import { ApiClient, ApiError } from '../../api/client'` |
| [frontend/src/features/shopping/EditDialog.tsx:4](../../frontend/src/features/shopping/EditDialog.tsx#L4) | `import { CouponForm } from './CouponForm'` |
| [frontend/src/features/shopping/EditDialog.tsx:5](../../frontend/src/features/shopping/EditDialog.tsx#L5) | `import { DealForm } from './DealForm'` |
| [frontend/src/features/shopping/EditDialog.tsx:6](../../frontend/src/features/shopping/EditDialog.tsx#L6) | `import { integer } from '../../api/format'` |
| [frontend/src/features/shopping/EditDialog.tsx:7](../../frontend/src/features/shopping/EditDialog.tsx#L7) | `import { Modal, Notice } from '../../components/ui'` |
| [frontend/src/features/shopping/EditDialog.tsx:8](../../frontend/src/features/shopping/EditDialog.tsx#L8) | `import type { Deal, Coupon, Plan, PlanRow, Purchase, Workspace } from '../../types/platform'` |
| [frontend/src/main.tsx:1](../../frontend/src/main.tsx#L1) | `import { StrictMode } from 'react'` |
| [frontend/src/main.tsx:2](../../frontend/src/main.tsx#L2) | `import { createRoot } from 'react-dom/client'` |
| [frontend/src/main.tsx:3](../../frontend/src/main.tsx#L3) | `import './index.css'` |
| [frontend/src/main.tsx:4](../../frontend/src/main.tsx#L4) | `import App from './App.tsx'` |
| [frontend/src/pages/WorkspacePages.tsx:1](../../frontend/src/pages/WorkspacePages.tsx#L1) | `import { useState } from 'react'` |
| [frontend/src/pages/WorkspacePages.tsx:2](../../frontend/src/pages/WorkspacePages.tsx#L2) | `import { ArrowUpRight, CalendarDays, Check, ClipboardList, Coins, Plus, Search, ShoppingBasket, Ticket, Wallet } from 'lucide-react'` |
| [frontend/src/pages/WorkspacePages.tsx:3](../../frontend/src/pages/WorkspacePages.tsx#L3) | `import { date, decimal, label, money, shortId } from '../api/format'` |
| [frontend/src/pages/WorkspacePages.tsx:4](../../frontend/src/pages/WorkspacePages.tsx#L4) | `import { rewardStatusLabel } from '../features/receipts/rewardStatus'` |
| [frontend/src/pages/WorkspacePages.tsx:5](../../frontend/src/pages/WorkspacePages.tsx#L5) | `import { Badge, Empty, LinkButton, Notice, PageHeader, Panel, Unavailable } from '../components/ui'` |
| [frontend/src/pages/WorkspacePages.tsx:6](../../frontend/src/pages/WorkspacePages.tsx#L6) | `import type { Edit } from '../features/shopping/EditDialog'` |
| [frontend/src/pages/WorkspacePages.tsx:7](../../frontend/src/pages/WorkspacePages.tsx#L7) | `import type { Plan, PlanRow, Workspace } from '../types/platform'` |
| [frontend/src/test-setup.ts:1](../../frontend/src/test-setup.ts#L1) | `import '@testing-library/jest-dom/vitest'` |
| [frontend/src/test-setup.ts:2](../../frontend/src/test-setup.ts#L2) | `import { cleanup } from '@testing-library/react'` |
| [frontend/src/test-setup.ts:3](../../frontend/src/test-setup.ts#L3) | `import { afterEach, vi } from 'vitest'` |
| [frontend/tests/browser.spec.ts:1](../../frontend/tests/browser.spec.ts#L1) | `import { test, expect } from '@playwright/test'` |
| [frontend/tests/browser.spec.ts:2](../../frontend/tests/browser.spec.ts#L2) | `import { workspace } from '../src/api/fixtures.test-support'` |
| [frontend/vite.config.ts:1](../../frontend/vite.config.ts#L1) | `import react from '@vitejs/plugin-react'` |
| [frontend/vite.config.ts:2](../../frontend/vite.config.ts#L2) | `import { defineConfig } from 'vitest/config'` |
| [frontend/vite.config.ts:3](../../frontend/vite.config.ts#L3) | `import type { Plugin } from 'vite'` |
| [frontend/vite.config.ts:4](../../frontend/vite.config.ts#L4) | `import { createHash } from 'node:crypto'` |
| [src/main/java/coupons/AdvertisedDeal.java:3](../../src/main/java/coupons/AdvertisedDeal.java#L3) | `import java.time.LocalDate;` |
| [src/main/java/coupons/AdvertisedDeal.java:4](../../src/main/java/coupons/AdvertisedDeal.java#L4) | `import java.util.HashSet;` |
| [src/main/java/coupons/AdvertisedDeal.java:5](../../src/main/java/coupons/AdvertisedDeal.java#L5) | `import java.util.List;` |
| [src/main/java/coupons/AdvertisedDeal.java:6](../../src/main/java/coupons/AdvertisedDeal.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/AdvertisedDeal.java:7](../../src/main/java/coupons/AdvertisedDeal.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/ClippedCoupon.java:3](../../src/main/java/coupons/ClippedCoupon.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ClippedCoupon.java:4](../../src/main/java/coupons/ClippedCoupon.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ClippedCoupon.java:5](../../src/main/java/coupons/ClippedCoupon.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/ClippedCoupon.java:6](../../src/main/java/coupons/ClippedCoupon.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/Coupon.java:2](../../src/main/java/coupons/Coupon.java#L2) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/Coupon.java:3](../../src/main/java/coupons/Coupon.java#L3) | `import java.time.LocalDate; // Represent the coupon's stated calendar expiration date.` |
| [src/main/java/coupons/Coupon.java:4](../../src/main/java/coupons/Coupon.java#L4) | `import java.util.Objects; // Provide null checking for required object values.` |
| [src/main/java/coupons/CouponApplied.java:3](../../src/main/java/coupons/CouponApplied.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/CouponApplied.java:4](../../src/main/java/coupons/CouponApplied.java#L4) | `import java.util.HashSet;` |
| [src/main/java/coupons/CouponApplied.java:5](../../src/main/java/coupons/CouponApplied.java#L5) | `import java.util.List;` |
| [src/main/java/coupons/CouponApplied.java:6](../../src/main/java/coupons/CouponApplied.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/CouponApplied.java:7](../../src/main/java/coupons/CouponApplied.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/CouponSystem.java:3](../../src/main/java/coupons/CouponSystem.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/CouponSystem.java:4](../../src/main/java/coupons/CouponSystem.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/CouponSystem.java:5](../../src/main/java/coupons/CouponSystem.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/CouponSystem.java:7](../../src/main/java/coupons/CouponSystem.java#L7) | `import coupons.repository.ReceiptRecord;` |
| [src/main/java/coupons/CouponSystem.java:8](../../src/main/java/coupons/CouponSystem.java#L8) | `import coupons.repository.ReceiptStatus;` |
| [src/main/java/coupons/CouponTerms.java:3](../../src/main/java/coupons/CouponTerms.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/CouponTerms.java:4](../../src/main/java/coupons/CouponTerms.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/CouponTerms.java:5](../../src/main/java/coupons/CouponTerms.java#L5) | `import java.time.LocalDate;` |
| [src/main/java/coupons/CouponTerms.java:6](../../src/main/java/coupons/CouponTerms.java#L6) | `import java.util.List;` |
| [src/main/java/coupons/CouponTransaction.java:3](../../src/main/java/coupons/CouponTransaction.java#L3) | `import java.util.ArrayList;` |
| [src/main/java/coupons/CouponTransaction.java:4](../../src/main/java/coupons/CouponTransaction.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/CouponTransaction.java:5](../../src/main/java/coupons/CouponTransaction.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/DealTerms.java:3](../../src/main/java/coupons/DealTerms.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/DealTerms.java:4](../../src/main/java/coupons/DealTerms.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/DealTerms.java:5](../../src/main/java/coupons/DealTerms.java#L5) | `import java.util.List;` |
| [src/main/java/coupons/DistributedProcess.java:3](../../src/main/java/coupons/DistributedProcess.java#L3) | `import java.util.ArrayList;` |
| [src/main/java/coupons/DistributedProcess.java:4](../../src/main/java/coupons/DistributedProcess.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/EarningDetails.java:3](../../src/main/java/coupons/EarningDetails.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/EarningDetails.java:4](../../src/main/java/coupons/EarningDetails.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/EarningDetails.java:5](../../src/main/java/coupons/EarningDetails.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/InventoryBalance.java:3](../../src/main/java/coupons/InventoryBalance.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/InventoryBalance.java:4](../../src/main/java/coupons/InventoryBalance.java#L4) | `import java.util.UUID;` |
| [src/main/java/coupons/LocalPdfReceiptReader.java:3](../../src/main/java/coupons/LocalPdfReceiptReader.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/LocalPdfReceiptReader.java:4](../../src/main/java/coupons/LocalPdfReceiptReader.java#L4) | `import java.nio.file.*;` |
| [src/main/java/coupons/LocalReceiptOcr.java:3](../../src/main/java/coupons/LocalReceiptOcr.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/LocalReceiptOcr.java:4](../../src/main/java/coupons/LocalReceiptOcr.java#L4) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/LocalReceiptOcr.java:5](../../src/main/java/coupons/LocalReceiptOcr.java#L5) | `import java.nio.file.*;` |
| [src/main/java/coupons/LocalReceiptOcr.java:6](../../src/main/java/coupons/LocalReceiptOcr.java#L6) | `import java.util.concurrent.TimeUnit;` |
| [src/main/java/coupons/Payment.java:2](../../src/main/java/coupons/Payment.java#L2) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/Payment.java:3](../../src/main/java/coupons/Payment.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/Payment.java:4](../../src/main/java/coupons/Payment.java#L4) | `import java.util.UUID; // Provide null checking for required object values.` |
| [src/main/java/coupons/PlannedPurchase.java:3](../../src/main/java/coupons/PlannedPurchase.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/PlannedPurchase.java:4](../../src/main/java/coupons/PlannedPurchase.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/PlannedPurchase.java:5](../../src/main/java/coupons/PlannedPurchase.java#L5) | `import java.util.HashSet;` |
| [src/main/java/coupons/PlannedPurchase.java:6](../../src/main/java/coupons/PlannedPurchase.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/PlannedPurchase.java:7](../../src/main/java/coupons/PlannedPurchase.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/PreparedImportList.java:3](../../src/main/java/coupons/PreparedImportList.java#L3) | `import coupons.repository.*;` |
| [src/main/java/coupons/PreparedImportList.java:4](../../src/main/java/coupons/PreparedImportList.java#L4) | `import java.io.IOException;` |
| [src/main/java/coupons/PreparedImportList.java:5](../../src/main/java/coupons/PreparedImportList.java#L5) | `import java.nio.file.*;` |
| [src/main/java/coupons/PreparedImportList.java:6](../../src/main/java/coupons/PreparedImportList.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/PreparedImportList.java:7](../../src/main/java/coupons/PreparedImportList.java#L7) | `import java.util.Properties;` |
| [src/main/java/coupons/Promotion.java:3](../../src/main/java/coupons/Promotion.java#L3) | `import java.util.Objects; // Provide null checking for required object values.` |
| [src/main/java/coupons/PromotionQualification.java:3](../../src/main/java/coupons/PromotionQualification.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/PromotionQualification.java:4](../../src/main/java/coupons/PromotionQualification.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/PromotionQualification.java:5](../../src/main/java/coupons/PromotionQualification.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:3](../../src/main/java/coupons/ProviderBalanceSummary.java#L3) | `import coupons.ReceiptObservations.EarningKind;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:4](../../src/main/java/coupons/ProviderBalanceSummary.java#L4) | `import coupons.repository.*;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:5](../../src/main/java/coupons/ProviderBalanceSummary.java#L5) | `import java.io.PrintStream;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:6](../../src/main/java/coupons/ProviderBalanceSummary.java#L6) | `import java.math.*;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:7](../../src/main/java/coupons/ProviderBalanceSummary.java#L7) | `import java.time.*;` |
| [src/main/java/coupons/ProviderBalanceSummary.java:8](../../src/main/java/coupons/ProviderBalanceSummary.java#L8) | `import java.util.*;` |
| [src/main/java/coupons/PurchaseItem.java:2](../../src/main/java/coupons/PurchaseItem.java#L2) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/PurchaseItem.java:3](../../src/main/java/coupons/PurchaseItem.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/PurchaseItem.java:4](../../src/main/java/coupons/PurchaseItem.java#L4) | `import java.util.UUID;` |
| [src/main/java/coupons/QualifyingSpend.java:3](../../src/main/java/coupons/QualifyingSpend.java#L3) | `import coupons.ReceiptObservations.DiscountKind;` |
| [src/main/java/coupons/QualifyingSpend.java:4](../../src/main/java/coupons/QualifyingSpend.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/QualifyingSpend.java:5](../../src/main/java/coupons/QualifyingSpend.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/RebateBenefit.java:3](../../src/main/java/coupons/RebateBenefit.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/RebateBenefit.java:4](../../src/main/java/coupons/RebateBenefit.java#L4) | `import java.util.Currency;` |
| [src/main/java/coupons/RebateBenefit.java:5](../../src/main/java/coupons/RebateBenefit.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/RebateClaim.java:3](../../src/main/java/coupons/RebateClaim.java#L3) | `import java.time.LocalDate;` |
| [src/main/java/coupons/RebateClaim.java:4](../../src/main/java/coupons/RebateClaim.java#L4) | `import java.util.HashSet;` |
| [src/main/java/coupons/RebateClaim.java:5](../../src/main/java/coupons/RebateClaim.java#L5) | `import java.util.List;` |
| [src/main/java/coupons/RebateClaim.java:6](../../src/main/java/coupons/RebateClaim.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/RebateClaim.java:7](../../src/main/java/coupons/RebateClaim.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/ReceiptDateInput.java:3](../../src/main/java/coupons/ReceiptDateInput.java#L3) | `import java.time.DateTimeException;` |
| [src/main/java/coupons/ReceiptDateInput.java:4](../../src/main/java/coupons/ReceiptDateInput.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ReceiptDateInput.java:5](../../src/main/java/coupons/ReceiptDateInput.java#L5) | `import java.time.format.DateTimeFormatter;` |
| [src/main/java/coupons/ReceiptDateInput.java:6](../../src/main/java/coupons/ReceiptDateInput.java#L6) | `import java.time.format.DateTimeFormatterBuilder;` |
| [src/main/java/coupons/ReceiptDateInput.java:7](../../src/main/java/coupons/ReceiptDateInput.java#L7) | `import java.time.format.ResolverStyle;` |
| [src/main/java/coupons/ReceiptDateInput.java:8](../../src/main/java/coupons/ReceiptDateInput.java#L8) | `import java.util.ArrayList;` |
| [src/main/java/coupons/ReceiptDateInput.java:9](../../src/main/java/coupons/ReceiptDateInput.java#L9) | `import java.util.List;` |
| [src/main/java/coupons/ReceiptDateInput.java:10](../../src/main/java/coupons/ReceiptDateInput.java#L10) | `import java.util.Locale;` |
| [src/main/java/coupons/ReceiptDraftFile.java:3](../../src/main/java/coupons/ReceiptDraftFile.java#L3) | `import coupons.repository.*;` |
| [src/main/java/coupons/ReceiptDraftFile.java:4](../../src/main/java/coupons/ReceiptDraftFile.java#L4) | `import java.io.*;` |
| [src/main/java/coupons/ReceiptDraftFile.java:5](../../src/main/java/coupons/ReceiptDraftFile.java#L5) | `import java.nio.channels.*;` |
| [src/main/java/coupons/ReceiptDraftFile.java:6](../../src/main/java/coupons/ReceiptDraftFile.java#L6) | `import java.nio.file.*;` |
| [src/main/java/coupons/ReceiptDraftFile.java:7](../../src/main/java/coupons/ReceiptDraftFile.java#L7) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptEntryApp.java:3](../../src/main/java/coupons/ReceiptEntryApp.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/ReceiptEntryApp.java:4](../../src/main/java/coupons/ReceiptEntryApp.java#L4) | `import java.io.PrintStream;` |
| [src/main/java/coupons/ReceiptEntryApp.java:5](../../src/main/java/coupons/ReceiptEntryApp.java#L5) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptEntryApp.java:6](../../src/main/java/coupons/ReceiptEntryApp.java#L6) | `import java.nio.file.Files;` |
| [src/main/java/coupons/ReceiptEntryApp.java:7](../../src/main/java/coupons/ReceiptEntryApp.java#L7) | `import java.nio.file.Path;` |
| [src/main/java/coupons/ReceiptEntryApp.java:8](../../src/main/java/coupons/ReceiptEntryApp.java#L8) | `import java.time.Instant;` |
| [src/main/java/coupons/ReceiptEntryApp.java:9](../../src/main/java/coupons/ReceiptEntryApp.java#L9) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ReceiptEntryApp.java:10](../../src/main/java/coupons/ReceiptEntryApp.java#L10) | `import java.util.ArrayList;` |
| [src/main/java/coupons/ReceiptEntryApp.java:11](../../src/main/java/coupons/ReceiptEntryApp.java#L11) | `import java.util.Arrays;` |
| [src/main/java/coupons/ReceiptEntryApp.java:12](../../src/main/java/coupons/ReceiptEntryApp.java#L12) | `import java.util.Collections;` |
| [src/main/java/coupons/ReceiptEntryApp.java:13](../../src/main/java/coupons/ReceiptEntryApp.java#L13) | `import java.util.Currency;` |
| [src/main/java/coupons/ReceiptEntryApp.java:14](../../src/main/java/coupons/ReceiptEntryApp.java#L14) | `import java.util.HashMap;` |
| [src/main/java/coupons/ReceiptEntryApp.java:15](../../src/main/java/coupons/ReceiptEntryApp.java#L15) | `import java.util.HashSet;` |
| [src/main/java/coupons/ReceiptEntryApp.java:16](../../src/main/java/coupons/ReceiptEntryApp.java#L16) | `import java.util.LinkedHashMap;` |
| [src/main/java/coupons/ReceiptEntryApp.java:17](../../src/main/java/coupons/ReceiptEntryApp.java#L17) | `import java.util.List;` |
| [src/main/java/coupons/ReceiptEntryApp.java:18](../../src/main/java/coupons/ReceiptEntryApp.java#L18) | `import java.util.Locale;` |
| [src/main/java/coupons/ReceiptEntryApp.java:19](../../src/main/java/coupons/ReceiptEntryApp.java#L19) | `import java.util.Map;` |
| [src/main/java/coupons/ReceiptEntryApp.java:20](../../src/main/java/coupons/ReceiptEntryApp.java#L20) | `import java.util.NoSuchElementException;` |
| [src/main/java/coupons/ReceiptEntryApp.java:21](../../src/main/java/coupons/ReceiptEntryApp.java#L21) | `import java.util.Objects;` |
| [src/main/java/coupons/ReceiptEntryApp.java:22](../../src/main/java/coupons/ReceiptEntryApp.java#L22) | `import java.util.Optional;` |
| [src/main/java/coupons/ReceiptEntryApp.java:23](../../src/main/java/coupons/ReceiptEntryApp.java#L23) | `import java.util.Scanner;` |
| [src/main/java/coupons/ReceiptEntryApp.java:24](../../src/main/java/coupons/ReceiptEntryApp.java#L24) | `import java.util.TreeSet;` |
| [src/main/java/coupons/ReceiptEntryApp.java:25](../../src/main/java/coupons/ReceiptEntryApp.java#L25) | `import java.util.UUID;` |
| [src/main/java/coupons/ReceiptEntryApp.java:27](../../src/main/java/coupons/ReceiptEntryApp.java#L27) | `import coupons.ReceiptObservations.DiscountKind;` |
| [src/main/java/coupons/ReceiptEntryApp.java:28](../../src/main/java/coupons/ReceiptEntryApp.java#L28) | `import coupons.ReceiptObservations.Earning;` |
| [src/main/java/coupons/ReceiptEntryApp.java:29](../../src/main/java/coupons/ReceiptEntryApp.java#L29) | `import coupons.ReceiptObservations.EarningKind;` |
| [src/main/java/coupons/ReceiptEntryApp.java:30](../../src/main/java/coupons/ReceiptEntryApp.java#L30) | `import coupons.ReceiptObservations.EventReference;` |
| [src/main/java/coupons/ReceiptEntryApp.java:31](../../src/main/java/coupons/ReceiptEntryApp.java#L31) | `import coupons.ReceiptObservations.FooterStatus;` |
| [src/main/java/coupons/ReceiptEntryApp.java:32](../../src/main/java/coupons/ReceiptEntryApp.java#L32) | `import coupons.ReceiptObservations.Inclusion;` |
| [src/main/java/coupons/ReceiptEntryApp.java:33](../../src/main/java/coupons/ReceiptEntryApp.java#L33) | `import coupons.ReceiptObservations.SavingsCategory;` |
| [src/main/java/coupons/ReceiptEntryApp.java:34](../../src/main/java/coupons/ReceiptEntryApp.java#L34) | `import coupons.ReceiptObservations.Scope;` |
| [src/main/java/coupons/ReceiptEntryApp.java:35](../../src/main/java/coupons/ReceiptEntryApp.java#L35) | `import coupons.ReceiptObservations.UnidentifiedRR;` |
| [src/main/java/coupons/ReceiptEntryApp.java:36](../../src/main/java/coupons/ReceiptEntryApp.java#L36) | `import coupons.ReceiptObservations.WalgreensCashAccount;` |
| [src/main/java/coupons/ReceiptEntryApp.java:37](../../src/main/java/coupons/ReceiptEntryApp.java#L37) | `import coupons.repository.GiftCardPromotion;` |
| [src/main/java/coupons/ReceiptEntryApp.java:38](../../src/main/java/coupons/ReceiptEntryApp.java#L38) | `import coupons.repository.InMemoryTransactionRepository;` |
| [src/main/java/coupons/ReceiptEntryApp.java:39](../../src/main/java/coupons/ReceiptEntryApp.java#L39) | `import coupons.repository.PurchaseLineSnap;` |
| [src/main/java/coupons/ReceiptEntryApp.java:40](../../src/main/java/coupons/ReceiptEntryApp.java#L40) | `import coupons.repository.RRUsageLink;` |
| [src/main/java/coupons/ReceiptEntryApp.java:41](../../src/main/java/coupons/ReceiptEntryApp.java#L41) | `import coupons.repository.ReceiptChangeContext;` |
| [src/main/java/coupons/ReceiptEntryApp.java:42](../../src/main/java/coupons/ReceiptEntryApp.java#L42) | `import coupons.repository.ReceiptCreateCtx;` |
| [src/main/java/coupons/ReceiptEntryApp.java:43](../../src/main/java/coupons/ReceiptEntryApp.java#L43) | `import coupons.repository.ReceiptEvidence;` |
| [src/main/java/coupons/ReceiptEntryApp.java:44](../../src/main/java/coupons/ReceiptEntryApp.java#L44) | `import coupons.repository.ReceiptMeta;` |
| [src/main/java/coupons/ReceiptEntryApp.java:45](../../src/main/java/coupons/ReceiptEntryApp.java#L45) | `import coupons.repository.ReceiptRecord;` |
| [src/main/java/coupons/ReceiptEntryApp.java:46](../../src/main/java/coupons/ReceiptEntryApp.java#L46) | `import coupons.repository.ReceiptSnapshot;` |
| [src/main/java/coupons/ReceiptEntryApp.java:47](../../src/main/java/coupons/ReceiptEntryApp.java#L47) | `import coupons.repository.ReceiptStatus;` |
| [src/main/java/coupons/ReceiptEntryApp.java:48](../../src/main/java/coupons/ReceiptEntryApp.java#L48) | `import coupons.repository.ReceiptTotalsSnap;` |
| [src/main/java/coupons/ReceiptEntryApp.java:49](../../src/main/java/coupons/ReceiptEntryApp.java#L49) | `import coupons.repository.ReceiptWriteRequest;` |
| [src/main/java/coupons/ReceiptEntryApp.java:50](../../src/main/java/coupons/ReceiptEntryApp.java#L50) | `import coupons.repository.ReceiptWriteResult;` |
| [src/main/java/coupons/ReceiptEntryApp.java:51](../../src/main/java/coupons/ReceiptEntryApp.java#L51) | `import coupons.repository.TransactionRepository;` |
| [src/main/java/coupons/ReceiptFileFormat.java:3](../../src/main/java/coupons/ReceiptFileFormat.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/ReceiptFileFormat.java:4](../../src/main/java/coupons/ReceiptFileFormat.java#L4) | `import java.nio.file.*;` |
| [src/main/java/coupons/ReceiptFileFormat.java:5](../../src/main/java/coupons/ReceiptFileFormat.java#L5) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/ReceiptFileFormat.java:6](../../src/main/java/coupons/ReceiptFileFormat.java#L6) | `import java.util.Set;` |
| [src/main/java/coupons/ReceiptImportDraft.java:3](../../src/main/java/coupons/ReceiptImportDraft.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/ReceiptImportDraft.java:4](../../src/main/java/coupons/ReceiptImportDraft.java#L4) | `import coupons.repository.ReceiptEvidence;` |
| [src/main/java/coupons/ReceiptImportDraft.java:5](../../src/main/java/coupons/ReceiptImportDraft.java#L5) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptImportDraft.java:6](../../src/main/java/coupons/ReceiptImportDraft.java#L6) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ReceiptImportDraft.java:7](../../src/main/java/coupons/ReceiptImportDraft.java#L7) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:3](../../src/main/java/coupons/ReceiptImportReader.java#L3) | `import coupons.ReceiptImportDraft.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:4](../../src/main/java/coupons/ReceiptImportReader.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:5](../../src/main/java/coupons/ReceiptImportReader.java#L5) | `import coupons.repository.ReceiptEvidence;` |
| [src/main/java/coupons/ReceiptImportReader.java:6](../../src/main/java/coupons/ReceiptImportReader.java#L6) | `import java.io.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:7](../../src/main/java/coupons/ReceiptImportReader.java#L7) | `import java.math.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:8](../../src/main/java/coupons/ReceiptImportReader.java#L8) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/ReceiptImportReader.java:9](../../src/main/java/coupons/ReceiptImportReader.java#L9) | `import java.nio.file.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:10](../../src/main/java/coupons/ReceiptImportReader.java#L10) | `import java.security.*;` |
| [src/main/java/coupons/ReceiptImportReader.java:11](../../src/main/java/coupons/ReceiptImportReader.java#L11) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ReceiptImportReader.java:12](../../src/main/java/coupons/ReceiptImportReader.java#L12) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptIssues.java:3](../../src/main/java/coupons/ReceiptIssues.java#L3) | `import coupons.repository.*;` |
| [src/main/java/coupons/ReceiptIssues.java:4](../../src/main/java/coupons/ReceiptIssues.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/ReceiptIssues.java:5](../../src/main/java/coupons/ReceiptIssues.java#L5) | `import java.io.PrintStream;` |
| [src/main/java/coupons/ReceiptIssues.java:6](../../src/main/java/coupons/ReceiptIssues.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptObservations.java:3](../../src/main/java/coupons/ReceiptObservations.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptObservations.java:4](../../src/main/java/coupons/ReceiptObservations.java#L4) | `import java.time.Instant;` |
| [src/main/java/coupons/ReceiptObservations.java:5](../../src/main/java/coupons/ReceiptObservations.java#L5) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ReceiptObservations.java:6](../../src/main/java/coupons/ReceiptObservations.java#L6) | `import java.util.Currency;` |
| [src/main/java/coupons/ReceiptObservations.java:7](../../src/main/java/coupons/ReceiptObservations.java#L7) | `import java.util.HashSet;` |
| [src/main/java/coupons/ReceiptObservations.java:8](../../src/main/java/coupons/ReceiptObservations.java#L8) | `import java.util.List;` |
| [src/main/java/coupons/ReceiptObservations.java:9](../../src/main/java/coupons/ReceiptObservations.java#L9) | `import java.util.Map;` |
| [src/main/java/coupons/ReceiptObservations.java:10](../../src/main/java/coupons/ReceiptObservations.java#L10) | `import java.util.Objects;` |
| [src/main/java/coupons/ReceiptObservations.java:11](../../src/main/java/coupons/ReceiptObservations.java#L11) | `import java.util.Optional;` |
| [src/main/java/coupons/ReceiptObservations.java:12](../../src/main/java/coupons/ReceiptObservations.java#L12) | `import java.util.UUID;` |
| [src/main/java/coupons/ReceiptPromotionLink.java:3](../../src/main/java/coupons/ReceiptPromotionLink.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/ReceiptPromotionLink.java:4](../../src/main/java/coupons/ReceiptPromotionLink.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptPromotionLink.java:5](../../src/main/java/coupons/ReceiptPromotionLink.java#L5) | `import java.time.Instant;` |
| [src/main/java/coupons/ReceiptPromotionLink.java:6](../../src/main/java/coupons/ReceiptPromotionLink.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptReport.java:3](../../src/main/java/coupons/ReceiptReport.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/ReceiptReport.java:4](../../src/main/java/coupons/ReceiptReport.java#L4) | `import coupons.repository.*;` |
| [src/main/java/coupons/ReceiptReport.java:5](../../src/main/java/coupons/ReceiptReport.java#L5) | `import java.io.PrintStream;` |
| [src/main/java/coupons/ReceiptReport.java:6](../../src/main/java/coupons/ReceiptReport.java#L6) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptReport.java:7](../../src/main/java/coupons/ReceiptReport.java#L7) | `import java.util.ArrayList;` |
| [src/main/java/coupons/ReceiptReport.java:8](../../src/main/java/coupons/ReceiptReport.java#L8) | `import java.util.Currency;` |
| [src/main/java/coupons/ReceiptReport.java:9](../../src/main/java/coupons/ReceiptReport.java#L9) | `import java.util.List;` |
| [src/main/java/coupons/ReceiptTimeInput.java:3](../../src/main/java/coupons/ReceiptTimeInput.java#L3) | `import java.time.DateTimeException;` |
| [src/main/java/coupons/ReceiptTimeInput.java:4](../../src/main/java/coupons/ReceiptTimeInput.java#L4) | `import java.time.LocalTime;` |
| [src/main/java/coupons/ReceiptTimeInput.java:5](../../src/main/java/coupons/ReceiptTimeInput.java#L5) | `import java.time.format.DateTimeFormatter;` |
| [src/main/java/coupons/ReceiptTimeInput.java:6](../../src/main/java/coupons/ReceiptTimeInput.java#L6) | `import java.util.Locale;` |
| [src/main/java/coupons/ReceiptTimeInput.java:7](../../src/main/java/coupons/ReceiptTimeInput.java#L7) | `import java.util.Optional;` |
| [src/main/java/coupons/ReceiptTimeInput.java:8](../../src/main/java/coupons/ReceiptTimeInput.java#L8) | `import java.util.regex.Pattern;` |
| [src/main/java/coupons/ReceiptTotals.java:3](../../src/main/java/coupons/ReceiptTotals.java#L3) | `import java.math.BigDecimal; // Use BigDecimal for precise monetary calculations.` |
| [src/main/java/coupons/ReceiptWorksheetData.java:3](../../src/main/java/coupons/ReceiptWorksheetData.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/ReceiptWorksheetData.java:4](../../src/main/java/coupons/ReceiptWorksheetData.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ReceiptWorksheetData.java:5](../../src/main/java/coupons/ReceiptWorksheetData.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:3](../../src/main/java/coupons/ReceiptWorksheetReport.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:4](../../src/main/java/coupons/ReceiptWorksheetReport.java#L4) | `import coupons.repository.*;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:5](../../src/main/java/coupons/ReceiptWorksheetReport.java#L5) | `import java.io.PrintStream;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:6](../../src/main/java/coupons/ReceiptWorksheetReport.java#L6) | `import java.math.*;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:7](../../src/main/java/coupons/ReceiptWorksheetReport.java#L7) | `import java.time.*;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:8](../../src/main/java/coupons/ReceiptWorksheetReport.java#L8) | `import java.time.temporal.TemporalAdjusters;` |
| [src/main/java/coupons/ReceiptWorksheetReport.java:9](../../src/main/java/coupons/ReceiptWorksheetReport.java#L9) | `import java.util.*;` |
| [src/main/java/coupons/RegisterReward.java:3](../../src/main/java/coupons/RegisterReward.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/RegisterReward.java:4](../../src/main/java/coupons/RegisterReward.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/RegisterReward.java:5](../../src/main/java/coupons/RegisterReward.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/RegisterReward.java:6](../../src/main/java/coupons/RegisterReward.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/RegisterRewardLabel.java:3](../../src/main/java/coupons/RegisterRewardLabel.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/RegisterRewardLabel.java:4](../../src/main/java/coupons/RegisterRewardLabel.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/RegisterRewardLabel.java:5](../../src/main/java/coupons/RegisterRewardLabel.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/RegisterRewardLabel.java:6](../../src/main/java/coupons/RegisterRewardLabel.java#L6) | `import java.util.stream.Collectors;` |
| [src/main/java/coupons/RegisterRewardRedemption.java:3](../../src/main/java/coupons/RegisterRewardRedemption.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/RegisterRewardRedemption.java:4](../../src/main/java/coupons/RegisterRewardRedemption.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/RegisterRewardRedemption.java:5](../../src/main/java/coupons/RegisterRewardRedemption.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/RegisterRewardRedemption.java:6](../../src/main/java/coupons/RegisterRewardRedemption.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/RewardSpendCheck.java:3](../../src/main/java/coupons/RewardSpendCheck.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/RewardSpendCheck.java:4](../../src/main/java/coupons/RewardSpendCheck.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/SavingsReport.java:3](../../src/main/java/coupons/SavingsReport.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/SavingsReport.java:4](../../src/main/java/coupons/SavingsReport.java#L4) | `import coupons.repository.*;` |
| [src/main/java/coupons/SavingsReport.java:5](../../src/main/java/coupons/SavingsReport.java#L5) | `import java.io.PrintStream;` |
| [src/main/java/coupons/SavingsReport.java:6](../../src/main/java/coupons/SavingsReport.java#L6) | `import java.math.*;` |
| [src/main/java/coupons/SavingsReport.java:7](../../src/main/java/coupons/SavingsReport.java#L7) | `import java.time.*;` |
| [src/main/java/coupons/SavingsReport.java:8](../../src/main/java/coupons/SavingsReport.java#L8) | `import java.util.*;` |
| [src/main/java/coupons/ShoppingEstimate.java:3](../../src/main/java/coupons/ShoppingEstimate.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/ShoppingEstimate.java:4](../../src/main/java/coupons/ShoppingEstimate.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ShoppingEstimate.java:5](../../src/main/java/coupons/ShoppingEstimate.java#L5) | `import java.util.ArrayList;` |
| [src/main/java/coupons/ShoppingEstimate.java:6](../../src/main/java/coupons/ShoppingEstimate.java#L6) | `import java.util.Comparator;` |
| [src/main/java/coupons/ShoppingEstimate.java:7](../../src/main/java/coupons/ShoppingEstimate.java#L7) | `import java.util.HashMap;` |
| [src/main/java/coupons/ShoppingEstimate.java:8](../../src/main/java/coupons/ShoppingEstimate.java#L8) | `import java.util.HashSet;` |
| [src/main/java/coupons/ShoppingEstimate.java:9](../../src/main/java/coupons/ShoppingEstimate.java#L9) | `import java.util.List;` |
| [src/main/java/coupons/ShoppingEstimate.java:10](../../src/main/java/coupons/ShoppingEstimate.java#L10) | `import java.util.Map;` |
| [src/main/java/coupons/ShoppingEstimate.java:11](../../src/main/java/coupons/ShoppingEstimate.java#L11) | `import java.util.Objects;` |
| [src/main/java/coupons/ShoppingEstimate.java:12](../../src/main/java/coupons/ShoppingEstimate.java#L12) | `import java.util.Set;` |
| [src/main/java/coupons/ShoppingEstimate.java:13](../../src/main/java/coupons/ShoppingEstimate.java#L13) | `import java.util.UUID;` |
| [src/main/java/coupons/ShoppingPlan.java:3](../../src/main/java/coupons/ShoppingPlan.java#L3) | `import java.time.LocalDate;` |
| [src/main/java/coupons/ShoppingPlan.java:4](../../src/main/java/coupons/ShoppingPlan.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/ShoppingPlan.java:5](../../src/main/java/coupons/ShoppingPlan.java#L5) | `import java.util.HashSet;` |
| [src/main/java/coupons/ShoppingPlan.java:6](../../src/main/java/coupons/ShoppingPlan.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/ShoppingPlan.java:7](../../src/main/java/coupons/ShoppingPlan.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/SourceArtifact.java:3](../../src/main/java/coupons/SourceArtifact.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/SourceArtifact.java:4](../../src/main/java/coupons/SourceArtifact.java#L4) | `import java.util.Arrays;` |
| [src/main/java/coupons/SourceArtifact.java:5](../../src/main/java/coupons/SourceArtifact.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/SourceArtifact.java:6](../../src/main/java/coupons/SourceArtifact.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:3](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:4](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L4) | `import coupons.repository.*;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:5](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L5) | `import java.io.IOException;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:6](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L6) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:7](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L7) | `import java.nio.file.*;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:8](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L8) | `import java.time.*;` |
| [src/main/java/coupons/SpreadsheetBalanceImport.java:9](../../src/main/java/coupons/SpreadsheetBalanceImport.java#L9) | `import java.util.*;` |
| [src/main/java/coupons/TransactionCalculator.java:3](../../src/main/java/coupons/TransactionCalculator.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/TransactionCalculator.java:4](../../src/main/java/coupons/TransactionCalculator.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/WalgreensImageImport.java:3](../../src/main/java/coupons/WalgreensImageImport.java#L3) | `import java.io.*;` |
| [src/main/java/coupons/WalgreensImageImport.java:4](../../src/main/java/coupons/WalgreensImageImport.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/WalgreensImageImport.java:5](../../src/main/java/coupons/WalgreensImageImport.java#L5) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/WalgreensImageImport.java:6](../../src/main/java/coupons/WalgreensImageImport.java#L6) | `import java.nio.file.*;` |
| [src/main/java/coupons/WalgreensImageImport.java:7](../../src/main/java/coupons/WalgreensImageImport.java#L7) | `import java.security.*;` |
| [src/main/java/coupons/WalgreensImageImport.java:8](../../src/main/java/coupons/WalgreensImageImport.java#L8) | `import java.time.LocalDate;` |
| [src/main/java/coupons/WalgreensImageImport.java:9](../../src/main/java/coupons/WalgreensImageImport.java#L9) | `import java.util.*;` |
| [src/main/java/coupons/WalgreensImageImport.java:10](../../src/main/java/coupons/WalgreensImageImport.java#L10) | `import java.util.regex.*;` |
| [src/main/java/coupons/api/ApiRequestStore.java:3](../../src/main/java/coupons/api/ApiRequestStore.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/api/CouponApiServer.java:3](../../src/main/java/coupons/api/CouponApiServer.java#L3) | `import coupons.repository.*;` |
| [src/main/java/coupons/api/CouponApiService.java:3](../../src/main/java/coupons/api/CouponApiService.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/api/CouponApiService.java:4](../../src/main/java/coupons/api/CouponApiService.java#L4) | `import java.io.OutputStream;` |
| [src/main/java/coupons/api/CouponApiService.java:5](../../src/main/java/coupons/api/CouponApiService.java#L5) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/api/CouponApiService.java:6](../../src/main/java/coupons/api/CouponApiService.java#L6) | `import java.net.InetSocketAddress;` |
| [src/main/java/coupons/api/CouponApiService.java:7](../../src/main/java/coupons/api/CouponApiService.java#L7) | `import java.net.URI;` |
| [src/main/java/coupons/api/CouponApiService.java:8](../../src/main/java/coupons/api/CouponApiService.java#L8) | `import java.net.URLDecoder;` |
| [src/main/java/coupons/api/CouponApiService.java:9](../../src/main/java/coupons/api/CouponApiService.java#L9) | `import java.nio.ByteBuffer;` |
| [src/main/java/coupons/api/CouponApiService.java:10](../../src/main/java/coupons/api/CouponApiService.java#L10) | `import java.nio.charset.CharacterCodingException;` |
| [src/main/java/coupons/api/CouponApiService.java:11](../../src/main/java/coupons/api/CouponApiService.java#L11) | `import java.nio.charset.CodingErrorAction;` |
| [src/main/java/coupons/api/CouponApiService.java:12](../../src/main/java/coupons/api/CouponApiService.java#L12) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/api/CouponApiService.java:13](../../src/main/java/coupons/api/CouponApiService.java#L13) | `import java.security.MessageDigest;` |
| [src/main/java/coupons/api/CouponApiService.java:14](../../src/main/java/coupons/api/CouponApiService.java#L14) | `import java.time.LocalDate;` |
| [src/main/java/coupons/api/CouponApiService.java:15](../../src/main/java/coupons/api/CouponApiService.java#L15) | `import java.time.format.DateTimeParseException;` |
| [src/main/java/coupons/api/CouponApiService.java:16](../../src/main/java/coupons/api/CouponApiService.java#L16) | `import java.util.ArrayList;` |
| [src/main/java/coupons/api/CouponApiService.java:17](../../src/main/java/coupons/api/CouponApiService.java#L17) | `import java.util.ConcurrentModificationException;` |
| [src/main/java/coupons/api/CouponApiService.java:18](../../src/main/java/coupons/api/CouponApiService.java#L18) | `import java.util.HashMap;` |
| [src/main/java/coupons/api/CouponApiService.java:19](../../src/main/java/coupons/api/CouponApiService.java#L19) | `import java.util.LinkedHashMap;` |
| [src/main/java/coupons/api/CouponApiService.java:20](../../src/main/java/coupons/api/CouponApiService.java#L20) | `import java.util.List;` |
| [src/main/java/coupons/api/CouponApiService.java:21](../../src/main/java/coupons/api/CouponApiService.java#L21) | `import java.util.Map;` |
| [src/main/java/coupons/api/CouponApiService.java:22](../../src/main/java/coupons/api/CouponApiService.java#L22) | `import java.util.NoSuchElementException;` |
| [src/main/java/coupons/api/CouponApiService.java:23](../../src/main/java/coupons/api/CouponApiService.java#L23) | `import java.util.Objects;` |
| [src/main/java/coupons/api/CouponApiService.java:24](../../src/main/java/coupons/api/CouponApiService.java#L24) | `import java.util.Set;` |
| [src/main/java/coupons/api/CouponApiService.java:25](../../src/main/java/coupons/api/CouponApiService.java#L25) | `import java.util.UUID;` |
| [src/main/java/coupons/api/CouponApiService.java:26](../../src/main/java/coupons/api/CouponApiService.java#L26) | `import java.util.concurrent.Executors;` |
| [src/main/java/coupons/api/CouponApiService.java:28](../../src/main/java/coupons/api/CouponApiService.java#L28) | `import com.sun.net.httpserver.HttpExchange;` |
| [src/main/java/coupons/api/CouponApiService.java:29](../../src/main/java/coupons/api/CouponApiService.java#L29) | `import com.sun.net.httpserver.HttpHandler;` |
| [src/main/java/coupons/api/CouponApiService.java:30](../../src/main/java/coupons/api/CouponApiService.java#L30) | `import com.sun.net.httpserver.HttpServer;` |
| [src/main/java/coupons/api/CouponApiService.java:32](../../src/main/java/coupons/api/CouponApiService.java#L32) | `import coupons.ClippedCoupon;` |
| [src/main/java/coupons/api/CouponApiService.java:33](../../src/main/java/coupons/api/CouponApiService.java#L33) | `import coupons.EarningDetails;` |
| [src/main/java/coupons/api/CouponApiService.java:34](../../src/main/java/coupons/api/CouponApiService.java#L34) | `import coupons.PromotionQualification;` |
| [src/main/java/coupons/api/CouponApiService.java:35](../../src/main/java/coupons/api/CouponApiService.java#L35) | `import coupons.RebateStatus;` |
| [src/main/java/coupons/api/CouponApiService.java:36](../../src/main/java/coupons/api/CouponApiService.java#L36) | `import coupons.ReceiptObservations;` |
| [src/main/java/coupons/api/CouponApiService.java:37](../../src/main/java/coupons/api/CouponApiService.java#L37) | `import coupons.ShoppingPlan;` |
| [src/main/java/coupons/api/CouponApiService.java:38](../../src/main/java/coupons/api/CouponApiService.java#L38) | `import coupons.TransactionCalculator;` |
| [src/main/java/coupons/api/CouponApiService.java:39](../../src/main/java/coupons/api/CouponApiService.java#L39) | `import static coupons.api.Json.asText;` |
| [src/main/java/coupons/api/CouponApiService.java:40](../../src/main/java/coupons/api/CouponApiService.java#L40) | `import static coupons.api.Json.known;` |
| [src/main/java/coupons/api/CouponApiService.java:41](../../src/main/java/coupons/api/CouponApiService.java#L41) | `import static coupons.api.Json.number;` |
| [src/main/java/coupons/api/CouponApiService.java:42](../../src/main/java/coupons/api/CouponApiService.java#L42) | `import static coupons.api.Json.optional;` |
| [src/main/java/coupons/api/CouponApiService.java:43](../../src/main/java/coupons/api/CouponApiService.java#L43) | `import static coupons.api.Json.required;` |
| [src/main/java/coupons/api/CouponApiService.java:44](../../src/main/java/coupons/api/CouponApiService.java#L44) | `import coupons.repository.ClippedCouponRepository;` |
| [src/main/java/coupons/api/CouponApiService.java:45](../../src/main/java/coupons/api/CouponApiService.java#L45) | `import coupons.repository.PostgresPlatformRepository;` |
| [src/main/java/coupons/api/CouponApiService.java:46](../../src/main/java/coupons/api/CouponApiService.java#L46) | `import coupons.repository.ReceiptChangeContext;` |
| [src/main/java/coupons/api/CouponApiService.java:47](../../src/main/java/coupons/api/CouponApiService.java#L47) | `import coupons.repository.ReceiptCreateCtx;` |
| [src/main/java/coupons/api/CouponApiService.java:48](../../src/main/java/coupons/api/CouponApiService.java#L48) | `import coupons.repository.ReceiptRecord;` |
| [src/main/java/coupons/api/CouponApiService.java:49](../../src/main/java/coupons/api/CouponApiService.java#L49) | `import coupons.repository.ReceiptSnapshot;` |
| [src/main/java/coupons/api/CouponApiService.java:50](../../src/main/java/coupons/api/CouponApiService.java#L50) | `import coupons.repository.ReceiptWriteRequest;` |
| [src/main/java/coupons/api/CouponApiService.java:51](../../src/main/java/coupons/api/CouponApiService.java#L51) | `import coupons.repository.ShoppingPlanRepository;` |
| [src/main/java/coupons/api/CouponApiService.java:52](../../src/main/java/coupons/api/CouponApiService.java#L52) | `import coupons.repository.TransactionRepository;` |
| [src/main/java/coupons/api/CouponInput.java:3](../../src/main/java/coupons/api/CouponInput.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/api/CouponInput.java:4](../../src/main/java/coupons/api/CouponInput.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/api/CouponInput.java:5](../../src/main/java/coupons/api/CouponInput.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/api/CouponInput.java:6](../../src/main/java/coupons/api/CouponInput.java#L6) | `import static coupons.api.Json.*;` |
| [src/main/java/coupons/api/DealInput.java:3](../../src/main/java/coupons/api/DealInput.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/api/DealInput.java:4](../../src/main/java/coupons/api/DealInput.java#L4) | `import java.time.*;` |
| [src/main/java/coupons/api/DealInput.java:5](../../src/main/java/coupons/api/DealInput.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/api/DealInput.java:6](../../src/main/java/coupons/api/DealInput.java#L6) | `import static coupons.api.Json.*;` |
| [src/main/java/coupons/api/EarningInput.java:2](../../src/main/java/coupons/api/EarningInput.java#L2) | `import coupons.*;` |
| [src/main/java/coupons/api/EarningInput.java:3](../../src/main/java/coupons/api/EarningInput.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/api/EarningInput.java:4](../../src/main/java/coupons/api/EarningInput.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/api/EarningInput.java:5](../../src/main/java/coupons/api/EarningInput.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/api/EarningInput.java:6](../../src/main/java/coupons/api/EarningInput.java#L6) | `import static coupons.api.Json.*;` |
| [src/main/java/coupons/api/EarningInput.java:7](../../src/main/java/coupons/api/EarningInput.java#L7) | `import static coupons.api.ReceiptInput.*;` |
| [src/main/java/coupons/api/Json.java:3](../../src/main/java/coupons/api/Json.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/api/Json.java:4](../../src/main/java/coupons/api/Json.java#L4) | `import java.util.*;` |
| [src/main/java/coupons/api/PostgresApiRequestStore.java:3](../../src/main/java/coupons/api/PostgresApiRequestStore.java#L3) | `import coupons.repository.PostgresConnectionFactory;` |
| [src/main/java/coupons/api/PostgresApiRequestStore.java:4](../../src/main/java/coupons/api/PostgresApiRequestStore.java#L4) | `import java.io.IOException;` |
| [src/main/java/coupons/api/PostgresApiRequestStore.java:5](../../src/main/java/coupons/api/PostgresApiRequestStore.java#L5) | `import java.sql.*;` |
| [src/main/java/coupons/api/PostgresApiRequestStore.java:6](../../src/main/java/coupons/api/PostgresApiRequestStore.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:3](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:4](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:5](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L5) | `import coupons.repository.*;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:6](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L6) | `import java.time.Instant;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:7](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L7) | `import java.util.*;` |
| [src/main/java/coupons/api/ReceiptEarningAddition.java:8](../../src/main/java/coupons/api/ReceiptEarningAddition.java#L8) | `import static coupons.api.Json.*;` |
| [src/main/java/coupons/api/ReceiptEarningRules.java:2](../../src/main/java/coupons/api/ReceiptEarningRules.java#L2) | `import coupons.ReceiptObservations.Earning;` |
| [src/main/java/coupons/api/ReceiptEarningRules.java:3](../../src/main/java/coupons/api/ReceiptEarningRules.java#L3) | `import java.util.*;` |
| [src/main/java/coupons/api/ReceiptEarningStatusUpdate.java:3](../../src/main/java/coupons/api/ReceiptEarningStatusUpdate.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/api/ReceiptEarningStatusUpdate.java:4](../../src/main/java/coupons/api/ReceiptEarningStatusUpdate.java#L4) | `import coupons.ReceiptObservations.Earning;` |
| [src/main/java/coupons/api/ReceiptEarningStatusUpdate.java:5](../../src/main/java/coupons/api/ReceiptEarningStatusUpdate.java#L5) | `import coupons.ReceiptObservations.EarningKind;` |
| [src/main/java/coupons/api/ReceiptEarningStatusUpdate.java:6](../../src/main/java/coupons/api/ReceiptEarningStatusUpdate.java#L6) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/api/ReceiptInput.java:3](../../src/main/java/coupons/api/ReceiptInput.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:4](../../src/main/java/coupons/api/ReceiptInput.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:5](../../src/main/java/coupons/api/ReceiptInput.java#L5) | `import coupons.repository.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:6](../../src/main/java/coupons/api/ReceiptInput.java#L6) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/api/ReceiptInput.java:7](../../src/main/java/coupons/api/ReceiptInput.java#L7) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/api/ReceiptInput.java:8](../../src/main/java/coupons/api/ReceiptInput.java#L8) | `import java.security.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:9](../../src/main/java/coupons/api/ReceiptInput.java#L9) | `import java.time.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:10](../../src/main/java/coupons/api/ReceiptInput.java#L10) | `import java.util.*;` |
| [src/main/java/coupons/api/ReceiptInput.java:11](../../src/main/java/coupons/api/ReceiptInput.java#L11) | `import static coupons.api.Json.*;` |
| [src/main/java/coupons/repository/AccountAdjustment.java:3](../../src/main/java/coupons/repository/AccountAdjustment.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/AccountAdjustment.java:4](../../src/main/java/coupons/repository/AccountAdjustment.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/AccountAdjustment.java:5](../../src/main/java/coupons/repository/AccountAdjustment.java#L5) | `import java.time.*;` |
| [src/main/java/coupons/repository/AccountAdjustment.java:6](../../src/main/java/coupons/repository/AccountAdjustment.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/AccountBalanceState.java:3](../../src/main/java/coupons/repository/AccountBalanceState.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/AccountBalanceState.java:4](../../src/main/java/coupons/repository/AccountBalanceState.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/AccountOpening.java:3](../../src/main/java/coupons/repository/AccountOpening.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/AccountOpening.java:4](../../src/main/java/coupons/repository/AccountOpening.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/AccountOpening.java:5](../../src/main/java/coupons/repository/AccountOpening.java#L5) | `import java.time.*;` |
| [src/main/java/coupons/repository/AccountOpening.java:6](../../src/main/java/coupons/repository/AccountOpening.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/ClippedCouponRepository.java:3](../../src/main/java/coupons/repository/ClippedCouponRepository.java#L3) | `import coupons.ClippedCoupon;` |
| [src/main/java/coupons/repository/ClippedCouponRepository.java:4](../../src/main/java/coupons/repository/ClippedCouponRepository.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/repository/ClippedCouponRepository.java:5](../../src/main/java/coupons/repository/ClippedCouponRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/CouponSnap.java:3](../../src/main/java/coupons/repository/CouponSnap.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/CouponSnap.java:4](../../src/main/java/coupons/repository/CouponSnap.java#L4) | `import java.time.LocalDate;` |
| [src/main/java/coupons/repository/CouponSnap.java:5](../../src/main/java/coupons/repository/CouponSnap.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/CouponSnap.java:7](../../src/main/java/coupons/repository/CouponSnap.java#L7) | `import coupons.Coupon;` |
| [src/main/java/coupons/repository/CouponSnap.java:8](../../src/main/java/coupons/repository/CouponSnap.java#L8) | `import coupons.CouponType;` |
| [src/main/java/coupons/repository/DealRepository.java:3](../../src/main/java/coupons/repository/DealRepository.java#L3) | `import coupons.AdvertisedDeal;` |
| [src/main/java/coupons/repository/DealRepository.java:4](../../src/main/java/coupons/repository/DealRepository.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/DealRepository.java:5](../../src/main/java/coupons/repository/DealRepository.java#L5) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/DealRepository.java:6](../../src/main/java/coupons/repository/DealRepository.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/EvidenceBatchValidator.java:3](../../src/main/java/coupons/repository/EvidenceBatchValidator.java#L3) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/EvidenceBatchValidator.java:4](../../src/main/java/coupons/repository/EvidenceBatchValidator.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/EvidenceBatchValidator.java:5](../../src/main/java/coupons/repository/EvidenceBatchValidator.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/EvidenceBatchValidator.java:6](../../src/main/java/coupons/repository/EvidenceBatchValidator.java#L6) | `import java.util.Set;` |
| [src/main/java/coupons/repository/EvidenceBatchValidator.java:7](../../src/main/java/coupons/repository/EvidenceBatchValidator.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/GiftCardPromotion.java:3](../../src/main/java/coupons/repository/GiftCardPromotion.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/GiftCardPromotion.java:4](../../src/main/java/coupons/repository/GiftCardPromotion.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/GiftCardPromotion.java:5](../../src/main/java/coupons/repository/GiftCardPromotion.java#L5) | `import java.time.LocalDate;` |
| [src/main/java/coupons/repository/GiftCardPromotion.java:6](../../src/main/java/coupons/repository/GiftCardPromotion.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/InMemoryStore.java:3](../../src/main/java/coupons/repository/InMemoryStore.java#L3) | `import java.util.Map;` |
| [src/main/java/coupons/repository/InMemoryStore.java:4](../../src/main/java/coupons/repository/InMemoryStore.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/InMemoryStore.java:5](../../src/main/java/coupons/repository/InMemoryStore.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/InMemoryStore.java:6](../../src/main/java/coupons/repository/InMemoryStore.java#L6) | `import java.util.List;` |
| [src/main/java/coupons/repository/InMemoryStore.java:8](../../src/main/java/coupons/repository/InMemoryStore.java#L8) | `import coupons.RegisterReward;` |
| [src/main/java/coupons/repository/InMemoryStore.java:9](../../src/main/java/coupons/repository/InMemoryStore.java#L9) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:3](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L3) | `import coupons.CouponTransaction;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:4](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L4) | `import coupons.RegisterReward;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:5](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L5) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:6](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L6) | `import java.time.Clock;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:7](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L7) | `import java.util.HashMap;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:8](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L8) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:9](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L9) | `import java.util.List;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:10](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L10) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:11](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L11) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:12](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L12) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:13](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L13) | `import coupons.repository.InMemoryStore.CommittedWrite;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:14](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L14) | `import coupons.repository.InMemoryStore.CreateInput;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:15](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L15) | `import coupons.repository.InMemoryStore.State;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:16](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L16) | `import coupons.repository.InMemoryStore.UpdateInput;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:17](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L17) | `import coupons.repository.InMemoryStore.DeleteInput;` |
| [src/main/java/coupons/repository/InMemoryTransactionRepository.java:18](../../src/main/java/coupons/repository/InMemoryTransactionRepository.java#L18) | `import coupons.repository.InMemoryStore.RestoreInput;` |
| [src/main/java/coupons/repository/InventoryAdjustCtx.java:3](../../src/main/java/coupons/repository/InventoryAdjustCtx.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/InventoryAdjustCtx.java:4](../../src/main/java/coupons/repository/InventoryAdjustCtx.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/InventoryRepository.java:3](../../src/main/java/coupons/repository/InventoryRepository.java#L3) | `import coupons.InventoryBalance;` |
| [src/main/java/coupons/repository/InventoryRepository.java:4](../../src/main/java/coupons/repository/InventoryRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/InventoryRepository.java:5](../../src/main/java/coupons/repository/InventoryRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/LocalMigrationPreview.java:3](../../src/main/java/coupons/repository/LocalMigrationPreview.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/repository/LocalMigrationPreview.java:4](../../src/main/java/coupons/repository/LocalMigrationPreview.java#L4) | `import java.nio.file.Path;` |
| [src/main/java/coupons/repository/LocalMigrationPreview.java:5](../../src/main/java/coupons/repository/LocalMigrationPreview.java#L5) | `import java.sql.*;` |
| [src/main/java/coupons/repository/LocalMigrationPreview.java:6](../../src/main/java/coupons/repository/LocalMigrationPreview.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/LocalMigrationPreview.java:7](../../src/main/java/coupons/repository/LocalMigrationPreview.java#L7) | `import static coupons.repository.PostgresRepositorySupport.serialize;` |
| [src/main/java/coupons/repository/LocalMigrationSource.java:3](../../src/main/java/coupons/repository/LocalMigrationSource.java#L3) | `import java.io.*;` |
| [src/main/java/coupons/repository/LocalMigrationSource.java:4](../../src/main/java/coupons/repository/LocalMigrationSource.java#L4) | `import java.nio.file.*;` |
| [src/main/java/coupons/repository/LocalMigrationSource.java:5](../../src/main/java/coupons/repository/LocalMigrationSource.java#L5) | `import java.security.*;` |
| [src/main/java/coupons/repository/LocalMigrationSource.java:6](../../src/main/java/coupons/repository/LocalMigrationSource.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:3](../../src/main/java/coupons/repository/LocalReceiptFile.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:4](../../src/main/java/coupons/repository/LocalReceiptFile.java#L4) | `import java.io.ObjectInputFilter;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:5](../../src/main/java/coupons/repository/LocalReceiptFile.java#L5) | `import java.io.ObjectInputStream;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:6](../../src/main/java/coupons/repository/LocalReceiptFile.java#L6) | `import java.io.ObjectOutputStream;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:7](../../src/main/java/coupons/repository/LocalReceiptFile.java#L7) | `import java.nio.channels.Channels;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:8](../../src/main/java/coupons/repository/LocalReceiptFile.java#L8) | `import java.nio.channels.FileChannel;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:9](../../src/main/java/coupons/repository/LocalReceiptFile.java#L9) | `import java.nio.channels.FileLock;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:10](../../src/main/java/coupons/repository/LocalReceiptFile.java#L10) | `import java.nio.file.Files;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:11](../../src/main/java/coupons/repository/LocalReceiptFile.java#L11) | `import java.nio.file.Path;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:12](../../src/main/java/coupons/repository/LocalReceiptFile.java#L12) | `import java.nio.file.StandardCopyOption;` |
| [src/main/java/coupons/repository/LocalReceiptFile.java:13](../../src/main/java/coupons/repository/LocalReceiptFile.java#L13) | `import java.nio.file.StandardOpenOption;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:3](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:4](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L4) | `import java.nio.file.Path;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:5](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L5) | `import java.sql.*;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:6](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:7](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L7) | `import static coupons.repository.PostgresRepositorySupport.*;` |
| [src/main/java/coupons/repository/LocalToPostgresMigration.java:8](../../src/main/java/coupons/repository/LocalToPostgresMigration.java#L8) | `import static coupons.repository.PostgresReceiptState.execute;` |
| [src/main/java/coupons/repository/PaymentSnap.java:3](../../src/main/java/coupons/repository/PaymentSnap.java#L3) | `import coupons.Payment;` |
| [src/main/java/coupons/repository/PaymentSnap.java:4](../../src/main/java/coupons/repository/PaymentSnap.java#L4) | `import coupons.PaymentType;` |
| [src/main/java/coupons/repository/PaymentSnap.java:5](../../src/main/java/coupons/repository/PaymentSnap.java#L5) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/PaymentSnap.java:6](../../src/main/java/coupons/repository/PaymentSnap.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PaymentSnap.java:7](../../src/main/java/coupons/repository/PaymentSnap.java#L7) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/PlannedPurchaseRepository.java:3](../../src/main/java/coupons/repository/PlannedPurchaseRepository.java#L3) | `import coupons.PlannedPurchase;` |
| [src/main/java/coupons/repository/PlannedPurchaseRepository.java:4](../../src/main/java/coupons/repository/PlannedPurchaseRepository.java#L4) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresClippedCouponRepository.java:3](../../src/main/java/coupons/repository/PostgresClippedCouponRepository.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/repository/PostgresClippedCouponRepository.java:4](../../src/main/java/coupons/repository/PostgresClippedCouponRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresClippedCouponRepository.java:5](../../src/main/java/coupons/repository/PostgresClippedCouponRepository.java#L5) | `import java.time.LocalDate;` |
| [src/main/java/coupons/repository/PostgresClippedCouponRepository.java:6](../../src/main/java/coupons/repository/PostgresClippedCouponRepository.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresConnectionFactory.java:3](../../src/main/java/coupons/repository/PostgresConnectionFactory.java#L3) | `import java.sql.Connection;` |
| [src/main/java/coupons/repository/PostgresConnectionFactory.java:4](../../src/main/java/coupons/repository/PostgresConnectionFactory.java#L4) | `import java.sql.DriverManager;` |
| [src/main/java/coupons/repository/PostgresConnectionFactory.java:5](../../src/main/java/coupons/repository/PostgresConnectionFactory.java#L5) | `import java.sql.SQLException;` |
| [src/main/java/coupons/repository/PostgresConnectionFactory.java:6](../../src/main/java/coupons/repository/PostgresConnectionFactory.java#L6) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PostgresDealRepository.java:3](../../src/main/java/coupons/repository/PostgresDealRepository.java#L3) | `import coupons.AdvertisedDeal;` |
| [src/main/java/coupons/repository/PostgresDealRepository.java:4](../../src/main/java/coupons/repository/PostgresDealRepository.java#L4) | `import coupons.DealTerms;` |
| [src/main/java/coupons/repository/PostgresDealRepository.java:5](../../src/main/java/coupons/repository/PostgresDealRepository.java#L5) | `import coupons.SourceArtifact;` |
| [src/main/java/coupons/repository/PostgresDealRepository.java:6](../../src/main/java/coupons/repository/PostgresDealRepository.java#L6) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresDealRepository.java:7](../../src/main/java/coupons/repository/PostgresDealRepository.java#L7) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresInventoryRepository.java:3](../../src/main/java/coupons/repository/PostgresInventoryRepository.java#L3) | `import coupons.InventoryBalance;` |
| [src/main/java/coupons/repository/PostgresInventoryRepository.java:4](../../src/main/java/coupons/repository/PostgresInventoryRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresInventoryRepository.java:5](../../src/main/java/coupons/repository/PostgresInventoryRepository.java#L5) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/PostgresInventoryRepository.java:6](../../src/main/java/coupons/repository/PostgresInventoryRepository.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:3](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:4](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L4) | `import java.math.RoundingMode;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:5](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L5) | `import java.sql.PreparedStatement;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:6](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L6) | `import java.sql.ResultSet;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:7](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L7) | `import java.sql.SQLException;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:8](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L8) | `import java.util.ArrayList;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:9](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L9) | `import java.util.ConcurrentModificationException;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:10](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L10) | `import java.util.LinkedHashMap;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:11](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L11) | `import java.util.List;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:12](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L12) | `import java.util.Map;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:13](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L13) | `import java.util.NoSuchElementException;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:14](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L14) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:15](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L15) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java:17](../../src/main/java/coupons/repository/PostgresPlanCheckoutRepository.java#L17) | `import coupons.ShoppingEstimate;` |
| [src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java:3](../../src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java#L3) | `import coupons.PlannedPurchase;` |
| [src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java:4](../../src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java:5](../../src/main/java/coupons/repository/PostgresPlannedPurchaseRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:3](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:4](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L4) | `import java.sql.Connection;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:5](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L5) | `import java.sql.SQLException;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:6](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L6) | `import java.time.LocalDate;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:7](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L7) | `import java.time.LocalTime;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:8](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L8) | `import java.time.YearMonth;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:9](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L9) | `import java.util.ArrayList;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:10](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L10) | `import java.util.Comparator;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:11](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L11) | `import java.util.HashMap;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:12](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L12) | `import java.util.LinkedHashMap;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:13](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L13) | `import java.util.List;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:14](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L14) | `import java.util.Map;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:15](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L15) | `import java.util.NoSuchElementException;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:16](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L16) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:17](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L17) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:19](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L19) | `import coupons.AdvertisedDeal;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:20](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L20) | `import coupons.ClippedCoupon;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:21](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L21) | `import coupons.PlannedPurchase;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:22](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L22) | `import coupons.PromotionQualification;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:23](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L23) | `import coupons.ProviderBalanceSummary;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:24](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L24) | `import coupons.PurchaseItem;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:25](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L25) | `import coupons.ReceiptTimeInput;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:26](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L26) | `import coupons.ReceiptWorksheetReport;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:27](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L27) | `import coupons.SavingsReport;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:28](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L28) | `import coupons.ShoppingEstimate;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:29](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L29) | `import coupons.SourceArtifact;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:30](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L30) | `import coupons.TransactionCalculator;` |
| [src/main/java/coupons/repository/PostgresPlatformRepository.java:31](../../src/main/java/coupons/repository/PostgresPlatformRepository.java#L31) | `import static coupons.repository.PostgresRepositorySupport.transaction;` |
| [src/main/java/coupons/repository/PostgresProviderAccountRepository.java:3](../../src/main/java/coupons/repository/PostgresProviderAccountRepository.java#L3) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresProviderAccountRepository.java:4](../../src/main/java/coupons/repository/PostgresProviderAccountRepository.java#L4) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/PostgresProviderAccountRepository.java:5](../../src/main/java/coupons/repository/PostgresProviderAccountRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresRebateRepository.java:3](../../src/main/java/coupons/repository/PostgresRebateRepository.java#L3) | `import coupons.*;` |
| [src/main/java/coupons/repository/PostgresRebateRepository.java:4](../../src/main/java/coupons/repository/PostgresRebateRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresRebateRepository.java:5](../../src/main/java/coupons/repository/PostgresRebateRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:3](../../src/main/java/coupons/repository/PostgresReceiptState.java#L3) | `import coupons.RegisterReward;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:4](../../src/main/java/coupons/repository/PostgresReceiptState.java#L4) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:5](../../src/main/java/coupons/repository/PostgresReceiptState.java#L5) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:6](../../src/main/java/coupons/repository/PostgresReceiptState.java#L6) | `import java.time.Clock;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:7](../../src/main/java/coupons/repository/PostgresReceiptState.java#L7) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresReceiptState.java:8](../../src/main/java/coupons/repository/PostgresReceiptState.java#L8) | `import static coupons.repository.PostgresRepositorySupport.*;` |
| [src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java:3](../../src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java#L3) | `import java.time.Clock;` |
| [src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java:4](../../src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java#L4) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java:5](../../src/main/java/coupons/repository/PostgresReceiptSupplementRepository.java#L5) | `import static coupons.repository.PostgresRepositorySupport.*;` |
| [src/main/java/coupons/repository/PostgresRepositorySupport.java:3](../../src/main/java/coupons/repository/PostgresRepositorySupport.java#L3) | `import java.io.*;` |
| [src/main/java/coupons/repository/PostgresRepositorySupport.java:4](../../src/main/java/coupons/repository/PostgresRepositorySupport.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresRepositorySupport.java:5](../../src/main/java/coupons/repository/PostgresRepositorySupport.java#L5) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/PostgresRepositorySupport.java:6](../../src/main/java/coupons/repository/PostgresRepositorySupport.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresSchema.java:3](../../src/main/java/coupons/repository/PostgresSchema.java#L3) | `import java.io.IOException;` |
| [src/main/java/coupons/repository/PostgresSchema.java:4](../../src/main/java/coupons/repository/PostgresSchema.java#L4) | `import java.io.InputStream;` |
| [src/main/java/coupons/repository/PostgresSchema.java:5](../../src/main/java/coupons/repository/PostgresSchema.java#L5) | `import java.nio.charset.StandardCharsets;` |
| [src/main/java/coupons/repository/PostgresSchema.java:6](../../src/main/java/coupons/repository/PostgresSchema.java#L6) | `import java.sql.Connection;` |
| [src/main/java/coupons/repository/PostgresSchema.java:7](../../src/main/java/coupons/repository/PostgresSchema.java#L7) | `import java.sql.SQLException;` |
| [src/main/java/coupons/repository/PostgresSchema.java:8](../../src/main/java/coupons/repository/PostgresSchema.java#L8) | `import java.sql.Statement;` |
| [src/main/java/coupons/repository/PostgresSchema.java:9](../../src/main/java/coupons/repository/PostgresSchema.java#L9) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PostgresShoppingPlanRepository.java:3](../../src/main/java/coupons/repository/PostgresShoppingPlanRepository.java#L3) | `import coupons.ShoppingPlan;` |
| [src/main/java/coupons/repository/PostgresShoppingPlanRepository.java:4](../../src/main/java/coupons/repository/PostgresShoppingPlanRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresShoppingPlanRepository.java:5](../../src/main/java/coupons/repository/PostgresShoppingPlanRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresSourceArtifactRepository.java:3](../../src/main/java/coupons/repository/PostgresSourceArtifactRepository.java#L3) | `import coupons.SourceArtifact;` |
| [src/main/java/coupons/repository/PostgresSourceArtifactRepository.java:4](../../src/main/java/coupons/repository/PostgresSourceArtifactRepository.java#L4) | `import java.sql.*;` |
| [src/main/java/coupons/repository/PostgresSourceArtifactRepository.java:5](../../src/main/java/coupons/repository/PostgresSourceArtifactRepository.java#L5) | `import java.util.*;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:3](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L3) | `import coupons.CouponTransaction;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:4](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L4) | `import coupons.RegisterReward;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:5](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L5) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:6](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L6) | `import java.io.ByteArrayInputStream;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:7](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L7) | `import java.io.ByteArrayOutputStream;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:8](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L8) | `import java.io.IOException;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:9](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L9) | `import java.io.ObjectInputStream;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:10](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L10) | `import java.io.ObjectOutputStream;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:11](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L11) | `import java.sql.Connection;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:12](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L12) | `import java.sql.PreparedStatement;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:13](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L13) | `import java.sql.ResultSet;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:14](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L14) | `import java.sql.SQLException;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:15](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L15) | `import java.sql.Types;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:16](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L16) | `import java.time.Clock;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:17](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L17) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:18](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L18) | `import java.util.ArrayList;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:19](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L19) | `import java.util.Arrays;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:20](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L20) | `import java.util.Comparator;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:21](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L21) | `import java.util.ConcurrentModificationException;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:22](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L22) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:23](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L23) | `import java.util.List;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:24](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L24) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:25](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L25) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/PostgresTransactionRepository.java:26](../../src/main/java/coupons/repository/PostgresTransactionRepository.java#L26) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/PromotionSnap.java:3](../../src/main/java/coupons/repository/PromotionSnap.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PromotionSnap.java:5](../../src/main/java/coupons/repository/PromotionSnap.java#L5) | `import coupons.Promotion;` |
| [src/main/java/coupons/repository/PromotionSnap.java:6](../../src/main/java/coupons/repository/PromotionSnap.java#L6) | `import coupons.PromotionType;` |
| [src/main/java/coupons/repository/PromotionSnap.java:7](../../src/main/java/coupons/repository/PromotionSnap.java#L7) | `import coupons.RewardType;` |
| [src/main/java/coupons/repository/ProviderAccountRepository.java:3](../../src/main/java/coupons/repository/ProviderAccountRepository.java#L3) | `import java.util.List;` |
| [src/main/java/coupons/repository/ProviderAccountRepository.java:4](../../src/main/java/coupons/repository/ProviderAccountRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/ProviderAccountRepository.java:5](../../src/main/java/coupons/repository/ProviderAccountRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ProviderRedemption.java:3](../../src/main/java/coupons/repository/ProviderRedemption.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/ProviderRedemption.java:4](../../src/main/java/coupons/repository/ProviderRedemption.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/ProviderRedemption.java:5](../../src/main/java/coupons/repository/ProviderRedemption.java#L5) | `import java.time.*;` |
| [src/main/java/coupons/repository/ProviderRedemption.java:6](../../src/main/java/coupons/repository/ProviderRedemption.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/PurchaseLineSnap.java:3](../../src/main/java/coupons/repository/PurchaseLineSnap.java#L3) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/PurchaseLineSnap.java:4](../../src/main/java/coupons/repository/PurchaseLineSnap.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/PurchaseLineSnap.java:5](../../src/main/java/coupons/repository/PurchaseLineSnap.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/PurchaseLineSnap.java:7](../../src/main/java/coupons/repository/PurchaseLineSnap.java#L7) | `import coupons.PurchaseItem;` |
| [src/main/java/coupons/repository/RRRedemptionRepository.java:3](../../src/main/java/coupons/repository/RRRedemptionRepository.java#L3) | `import java.util.List;` |
| [src/main/java/coupons/repository/RRRedemptionRepository.java:4](../../src/main/java/coupons/repository/RRRedemptionRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/RRRedemptionRepository.java:5](../../src/main/java/coupons/repository/RRRedemptionRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/RRRedemptionRepository.java:7](../../src/main/java/coupons/repository/RRRedemptionRepository.java#L7) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/RRUsageLink.java:3](../../src/main/java/coupons/repository/RRUsageLink.java#L3) | `import java.io.Serializable;` |
| [src/main/java/coupons/repository/RRUsageLink.java:4](../../src/main/java/coupons/repository/RRUsageLink.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/RRUsageLink.java:5](../../src/main/java/coupons/repository/RRUsageLink.java#L5) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/RRUsageLink.java:6](../../src/main/java/coupons/repository/RRUsageLink.java#L6) | `import java.util.*;` |
| [src/main/java/coupons/repository/RebateRepository.java:3](../../src/main/java/coupons/repository/RebateRepository.java#L3) | `import coupons.RebateClaim;` |
| [src/main/java/coupons/repository/RebateRepository.java:4](../../src/main/java/coupons/repository/RebateRepository.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/RebateRepository.java:5](../../src/main/java/coupons/repository/RebateRepository.java#L5) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/RebateRepository.java:6](../../src/main/java/coupons/repository/RebateRepository.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptChangeContext.java:3](../../src/main/java/coupons/repository/ReceiptChangeContext.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/ReceiptChangeContext.java:4](../../src/main/java/coupons/repository/ReceiptChangeContext.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptCreateCtx.java:3](../../src/main/java/coupons/repository/ReceiptCreateCtx.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptEvidence.java:3](../../src/main/java/coupons/repository/ReceiptEvidence.java#L3) | `import java.util.Arrays;` |
| [src/main/java/coupons/repository/ReceiptEvidence.java:4](../../src/main/java/coupons/repository/ReceiptEvidence.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptEvidence.java:5](../../src/main/java/coupons/repository/ReceiptEvidence.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptEvidenceRepository.java:3](../../src/main/java/coupons/repository/ReceiptEvidenceRepository.java#L3) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptEvidenceRepository.java:4](../../src/main/java/coupons/repository/ReceiptEvidenceRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/ReceiptEvidenceRepository.java:5](../../src/main/java/coupons/repository/ReceiptEvidenceRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptHistoryEntry.java:3](../../src/main/java/coupons/repository/ReceiptHistoryEntry.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptHistoryEntry.java:4](../../src/main/java/coupons/repository/ReceiptHistoryEntry.java#L4) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptHistoryRepository.java:3](../../src/main/java/coupons/repository/ReceiptHistoryRepository.java#L3) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptHistoryRepository.java:4](../../src/main/java/coupons/repository/ReceiptHistoryRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/ReceiptHistoryRepository.java:5](../../src/main/java/coupons/repository/ReceiptHistoryRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptMeta.java:3](../../src/main/java/coupons/repository/ReceiptMeta.java#L3) | `import java.time.LocalDateTime;` |
| [src/main/java/coupons/repository/ReceiptMeta.java:4](../../src/main/java/coupons/repository/ReceiptMeta.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:3](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L3) | `import coupons.BenefitKind;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:4](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L4) | `import coupons.ReceiptObservations.EventKind;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:5](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L5) | `import coupons.ReceiptObservations.EventReference;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:6](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L6) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:7](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L7) | `import java.util.Currency;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:8](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L8) | `import java.util.HashMap;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:9](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L9) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:10](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L10) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:11](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L11) | `import java.util.Map;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:12](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L12) | `import java.util.Set;` |
| [src/main/java/coupons/repository/ReceiptObservationValidator.java:13](../../src/main/java/coupons/repository/ReceiptObservationValidator.java#L13) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptRecord.java:3](../../src/main/java/coupons/repository/ReceiptRecord.java#L3) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/ReceiptRecord.java:4](../../src/main/java/coupons/repository/ReceiptRecord.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptRecord.java:5](../../src/main/java/coupons/repository/ReceiptRecord.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptRecord.java:6](../../src/main/java/coupons/repository/ReceiptRecord.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptRecord.java:8](../../src/main/java/coupons/repository/ReceiptRecord.java#L8) | `import coupons.CouponTransaction;` |
| [src/main/java/coupons/repository/ReceiptRevSnap.java:3](../../src/main/java/coupons/repository/ReceiptRevSnap.java#L3) | `import java.util.HashSet;` |
| [src/main/java/coupons/repository/ReceiptRevSnap.java:4](../../src/main/java/coupons/repository/ReceiptRevSnap.java#L4) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptRevSnap.java:5](../../src/main/java/coupons/repository/ReceiptRevSnap.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptRevSnap.java:6](../../src/main/java/coupons/repository/ReceiptRevSnap.java#L6) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:3](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L3) | `import coupons.CouponApplied;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:4](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L4) | `import coupons.CouponTransaction;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:5](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L5) | `import coupons.Coupon;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:6](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L6) | `import coupons.Payment;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:7](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L7) | `import coupons.Promotion;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:8](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L8) | `import coupons.PurchaseItem;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:9](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L9) | `import coupons.ReceiptTotals;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:10](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L10) | `import coupons.Reward;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:11](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L11) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:12](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L12) | `import coupons.ReceiptObservations;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:13](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L13) | `import java.util.List;` |
| [src/main/java/coupons/repository/ReceiptSnapshot.java:14](../../src/main/java/coupons/repository/ReceiptSnapshot.java#L14) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptTotalsSnap.java:3](../../src/main/java/coupons/repository/ReceiptTotalsSnap.java#L3) | `import coupons.ReceiptTotals;` |
| [src/main/java/coupons/repository/ReceiptTotalsSnap.java:4](../../src/main/java/coupons/repository/ReceiptTotalsSnap.java#L4) | `import java.math.BigDecimal;` |
| [src/main/java/coupons/repository/ReceiptTotalsSnap.java:5](../../src/main/java/coupons/repository/ReceiptTotalsSnap.java#L5) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptValidator.java:3](../../src/main/java/coupons/repository/ReceiptValidator.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptValidator.java:5](../../src/main/java/coupons/repository/ReceiptValidator.java#L5) | `import coupons.RegisterRewardRedemption;` |
| [src/main/java/coupons/repository/ReceiptWriteRequest.java:3](../../src/main/java/coupons/repository/ReceiptWriteRequest.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/ReceiptWriteRequest.java:4](../../src/main/java/coupons/repository/ReceiptWriteRequest.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptWriteRequest.java:5](../../src/main/java/coupons/repository/ReceiptWriteRequest.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ReceiptWriteResult.java:3](../../src/main/java/coupons/repository/ReceiptWriteResult.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/ReceiptWriteResult.java:4](../../src/main/java/coupons/repository/ReceiptWriteResult.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/ReceiptWriteResult.java:5](../../src/main/java/coupons/repository/ReceiptWriteResult.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/RegisterRewardRepository.java:3](../../src/main/java/coupons/repository/RegisterRewardRepository.java#L3) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/RegisterRewardRepository.java:4](../../src/main/java/coupons/repository/RegisterRewardRepository.java#L4) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/RegisterRewardRepository.java:6](../../src/main/java/coupons/repository/RegisterRewardRepository.java#L6) | `import coupons.RegisterReward;` |
| [src/main/java/coupons/repository/RewardSnap.java:3](../../src/main/java/coupons/repository/RewardSnap.java#L3) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/RewardSnap.java:5](../../src/main/java/coupons/repository/RewardSnap.java#L5) | `import coupons.Reward;` |
| [src/main/java/coupons/repository/RewardVoid.java:3](../../src/main/java/coupons/repository/RewardVoid.java#L3) | `import java.time.Instant;` |
| [src/main/java/coupons/repository/RewardVoid.java:4](../../src/main/java/coupons/repository/RewardVoid.java#L4) | `import java.util.Objects;` |
| [src/main/java/coupons/repository/RewardVoid.java:5](../../src/main/java/coupons/repository/RewardVoid.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/ShoppingPlanRepository.java:3](../../src/main/java/coupons/repository/ShoppingPlanRepository.java#L3) | `import coupons.ShoppingPlan;` |
| [src/main/java/coupons/repository/ShoppingPlanRepository.java:4](../../src/main/java/coupons/repository/ShoppingPlanRepository.java#L4) | `import java.util.*;` |
| [src/main/java/coupons/repository/SourceArtifactRepository.java:3](../../src/main/java/coupons/repository/SourceArtifactRepository.java#L3) | `import coupons.SourceArtifact;` |
| [src/main/java/coupons/repository/SourceArtifactRepository.java:4](../../src/main/java/coupons/repository/SourceArtifactRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/SourceArtifactRepository.java:5](../../src/main/java/coupons/repository/SourceArtifactRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/TransactionRepository.java:3](../../src/main/java/coupons/repository/TransactionRepository.java#L3) | `import java.util.List;` |
| [src/main/java/coupons/repository/TransactionRepository.java:4](../../src/main/java/coupons/repository/TransactionRepository.java#L4) | `import java.util.Optional;` |
| [src/main/java/coupons/repository/TransactionRepository.java:5](../../src/main/java/coupons/repository/TransactionRepository.java#L5) | `import java.util.UUID;` |
| [src/main/java/coupons/repository/TransactionRepository.java:7](../../src/main/java/coupons/repository/TransactionRepository.java#L7) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/AdvertisedDealTest.java:3](../../src/test/java/coupons/AdvertisedDealTest.java#L3) | `import java.time.LocalDate;` |
| [src/test/java/coupons/AdvertisedDealTest.java:4](../../src/test/java/coupons/AdvertisedDealTest.java#L4) | `import java.util.ArrayList;` |
| [src/test/java/coupons/AdvertisedDealTest.java:5](../../src/test/java/coupons/AdvertisedDealTest.java#L5) | `import java.util.Arrays;` |
| [src/test/java/coupons/AdvertisedDealTest.java:6](../../src/test/java/coupons/AdvertisedDealTest.java#L6) | `import java.util.List;` |
| [src/test/java/coupons/AdvertisedDealTest.java:7](../../src/test/java/coupons/AdvertisedDealTest.java#L7) | `import java.util.UUID;` |
| [src/test/java/coupons/AdvertisedDealTest.java:8](../../src/test/java/coupons/AdvertisedDealTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/AdvertisedDealTest.java:9](../../src/test/java/coupons/AdvertisedDealTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CalendarWeeksTest.java:2](../../src/test/java/coupons/CalendarWeeksTest.java#L2) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CalendarWeeksTest.java:3](../../src/test/java/coupons/CalendarWeeksTest.java#L3) | `import java.time.LocalDate;` |
| [src/test/java/coupons/CalendarWeeksTest.java:4](../../src/test/java/coupons/CalendarWeeksTest.java#L4) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:3](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:4](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L4) | `import java.io.*;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:5](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:6](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:7](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:8](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CliNavigationAndPromotionProgressTest.java:9](../../src/test/java/coupons/CliNavigationAndPromotionProgressTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:3](../../src/test/java/coupons/CompactAccountDisplayTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:4](../../src/test/java/coupons/CompactAccountDisplayTest.java#L4) | `import java.io.*;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:5](../../src/test/java/coupons/CompactAccountDisplayTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:6](../../src/test/java/coupons/CompactAccountDisplayTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:7](../../src/test/java/coupons/CompactAccountDisplayTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:8](../../src/test/java/coupons/CompactAccountDisplayTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CompactAccountDisplayTest.java:9](../../src/test/java/coupons/CompactAccountDisplayTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:3](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:4](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:5](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:6](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L6) | `import java.math.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:7](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:8](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:9](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CompactReceiptRewardTotalsTest.java:10](../../src/test/java/coupons/CompactReceiptRewardTotalsTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:3](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:4](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:5](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:6](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L6) | `import java.math.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:7](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:8](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:9](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CompactSavingsSummaryTest.java:10](../../src/test/java/coupons/CompactSavingsSummaryTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/CouponAppliedTest.java:3](../../src/test/java/coupons/CouponAppliedTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/CouponAppliedTest.java:4](../../src/test/java/coupons/CouponAppliedTest.java#L4) | `import java.util.ArrayList;` |
| [src/test/java/coupons/CouponAppliedTest.java:5](../../src/test/java/coupons/CouponAppliedTest.java#L5) | `import java.util.Arrays;` |
| [src/test/java/coupons/CouponAppliedTest.java:6](../../src/test/java/coupons/CouponAppliedTest.java#L6) | `import java.util.List;` |
| [src/test/java/coupons/CouponAppliedTest.java:7](../../src/test/java/coupons/CouponAppliedTest.java#L7) | `import java.util.UUID;` |
| [src/test/java/coupons/CouponAppliedTest.java:9](../../src/test/java/coupons/CouponAppliedTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/CouponAppliedTest.java:10](../../src/test/java/coupons/CouponAppliedTest.java#L10) | `import static org.junit.jupiter.api.Assertions.assertNotNull;` |
| [src/test/java/coupons/CouponAppliedTest.java:11](../../src/test/java/coupons/CouponAppliedTest.java#L11) | `import static org.junit.jupiter.api.Assertions.assertNotSame;` |
| [src/test/java/coupons/CouponAppliedTest.java:12](../../src/test/java/coupons/CouponAppliedTest.java#L12) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/CouponAppliedTest.java:13](../../src/test/java/coupons/CouponAppliedTest.java#L13) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/CouponAppliedTest.java:14](../../src/test/java/coupons/CouponAppliedTest.java#L14) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CouponSystemTest.java:3](../../src/test/java/coupons/CouponSystemTest.java#L3) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/CouponSystemTest.java:5](../../src/test/java/coupons/CouponSystemTest.java#L5) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/CouponSystemTest.java:6](../../src/test/java/coupons/CouponSystemTest.java#L6) | `import java.io.PrintStream;` |
| [src/test/java/coupons/CouponSystemTest.java:8](../../src/test/java/coupons/CouponSystemTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CouponTest.java:2](../../src/test/java/coupons/CouponTest.java#L2) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/CouponTest.java:4](../../src/test/java/coupons/CouponTest.java#L4) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/CouponTest.java:5](../../src/test/java/coupons/CouponTest.java#L5) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/CouponTest.java:6](../../src/test/java/coupons/CouponTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/CouponTest.java:7](../../src/test/java/coupons/CouponTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/CouponTransactionTest.java:3](../../src/test/java/coupons/CouponTransactionTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/CouponTransactionTest.java:4](../../src/test/java/coupons/CouponTransactionTest.java#L4) | `import java.util.List;` |
| [src/test/java/coupons/CouponTransactionTest.java:5](../../src/test/java/coupons/CouponTransactionTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/CouponTransactionTest.java:7](../../src/test/java/coupons/CouponTransactionTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/CouponTransactionTest.java:8](../../src/test/java/coupons/CouponTransactionTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/CouponTransactionTest.java:9](../../src/test/java/coupons/CouponTransactionTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/CouponTransactionTest.java:10](../../src/test/java/coupons/CouponTransactionTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:3](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:4](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L4) | `import java.io.*;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:5](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:6](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:7](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:8](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/GiftCardPromotionCliTest.java:9](../../src/test/java/coupons/GiftCardPromotionCliTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/InventoryBalanceTest.java:3](../../src/test/java/coupons/InventoryBalanceTest.java#L3) | `import java.util.UUID;` |
| [src/test/java/coupons/InventoryBalanceTest.java:4](../../src/test/java/coupons/InventoryBalanceTest.java#L4) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/InventoryBalanceTest.java:5](../../src/test/java/coupons/InventoryBalanceTest.java#L5) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:3](../../src/test/java/coupons/ItemEarningScopeTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:4](../../src/test/java/coupons/ItemEarningScopeTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:5](../../src/test/java/coupons/ItemEarningScopeTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:6](../../src/test/java/coupons/ItemEarningScopeTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:7](../../src/test/java/coupons/ItemEarningScopeTest.java#L7) | `import java.nio.file.Path;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:8](../../src/test/java/coupons/ItemEarningScopeTest.java#L8) | `import java.util.List;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:9](../../src/test/java/coupons/ItemEarningScopeTest.java#L9) | `import java.util.Scanner;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:10](../../src/test/java/coupons/ItemEarningScopeTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:11](../../src/test/java/coupons/ItemEarningScopeTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ItemEarningScopeTest.java:12](../../src/test/java/coupons/ItemEarningScopeTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:3](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:4](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:5](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:6](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:7](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L7) | `import java.nio.file.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:8](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L8) | `import java.time.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:9](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:10](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:11](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/OwnerReceiptActionsTest.java:12](../../src/test/java/coupons/OwnerReceiptActionsTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:2](../../src/test/java/coupons/PdfReceiptImportTest.java#L2) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:3](../../src/test/java/coupons/PdfReceiptImportTest.java#L3) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:4](../../src/test/java/coupons/PdfReceiptImportTest.java#L4) | `import org.apache.pdfbox.pdmodel.*;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:5](../../src/test/java/coupons/PdfReceiptImportTest.java#L5) | `import org.apache.pdfbox.pdmodel.font.*;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:6](../../src/test/java/coupons/PdfReceiptImportTest.java#L6) | `import java.nio.file.*;` |
| [src/test/java/coupons/PdfReceiptImportTest.java:7](../../src/test/java/coupons/PdfReceiptImportTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:3](../../src/test/java/coupons/PersistentBalancesTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:4](../../src/test/java/coupons/PersistentBalancesTest.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:5](../../src/test/java/coupons/PersistentBalancesTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/PersistentBalancesTest.java:6](../../src/test/java/coupons/PersistentBalancesTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:7](../../src/test/java/coupons/PersistentBalancesTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:8](../../src/test/java/coupons/PersistentBalancesTest.java#L8) | `import java.nio.file.*;` |
| [src/test/java/coupons/PersistentBalancesTest.java:9](../../src/test/java/coupons/PersistentBalancesTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/PersistentBalancesTest.java:10](../../src/test/java/coupons/PersistentBalancesTest.java#L10) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/PersistentBalancesTest.java:11](../../src/test/java/coupons/PersistentBalancesTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PhotoImportClasspathTest.java:2](../../src/test/java/coupons/PhotoImportClasspathTest.java#L2) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/PhotoImportClasspathTest.java:3](../../src/test/java/coupons/PhotoImportClasspathTest.java#L3) | `import java.net.URLClassLoader;` |
| [src/test/java/coupons/PhotoImportClasspathTest.java:4](../../src/test/java/coupons/PhotoImportClasspathTest.java#L4) | `import java.nio.file.Path;` |
| [src/test/java/coupons/PhotoImportClasspathTest.java:5](../../src/test/java/coupons/PhotoImportClasspathTest.java#L5) | `import java.lang.reflect.InvocationTargetException;` |
| [src/test/java/coupons/PhotoImportClasspathTest.java:6](../../src/test/java/coupons/PhotoImportClasspathTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PreparedImportListTest.java:2](../../src/test/java/coupons/PreparedImportListTest.java#L2) | `import coupons.repository.*;` |
| [src/test/java/coupons/PreparedImportListTest.java:3](../../src/test/java/coupons/PreparedImportListTest.java#L3) | `import java.nio.file.*;` |
| [src/test/java/coupons/PreparedImportListTest.java:4](../../src/test/java/coupons/PreparedImportListTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/PreparedImportListTest.java:5](../../src/test/java/coupons/PreparedImportListTest.java#L5) | `import java.time.Instant;` |
| [src/test/java/coupons/PreparedImportListTest.java:6](../../src/test/java/coupons/PreparedImportListTest.java#L6) | `import java.util.*;` |
| [src/test/java/coupons/PreparedImportListTest.java:7](../../src/test/java/coupons/PreparedImportListTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/PreparedImportListTest.java:8](../../src/test/java/coupons/PreparedImportListTest.java#L8) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/PreparedImportListTest.java:9](../../src/test/java/coupons/PreparedImportListTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PromotionQualificationTest.java:2](../../src/test/java/coupons/PromotionQualificationTest.java#L2) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/PromotionQualificationTest.java:3](../../src/test/java/coupons/PromotionQualificationTest.java#L3) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/PromotionQualificationTest.java:4](../../src/test/java/coupons/PromotionQualificationTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/PromotionQualificationTest.java:5](../../src/test/java/coupons/PromotionQualificationTest.java#L5) | `import java.util.*;` |
| [src/test/java/coupons/PromotionQualificationTest.java:6](../../src/test/java/coupons/PromotionQualificationTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:3](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:4](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:5](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L5) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:6](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L6) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:7](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:8](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L8) | `import java.time.LocalDate;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:9](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L9) | `import java.util.Currency;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:10](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L10) | `import java.util.List;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:11](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L11) | `import java.util.Map;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:12](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L12) | `import java.util.UUID;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:13](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L13) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ProviderBalanceSummaryTest.java:14](../../src/test/java/coupons/ProviderBalanceSummaryTest.java#L14) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/PurchaseItemTest.java:3](../../src/test/java/coupons/PurchaseItemTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/PurchaseItemTest.java:4](../../src/test/java/coupons/PurchaseItemTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/PurchaseItemTest.java:6](../../src/test/java/coupons/PurchaseItemTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/PurchaseItemTest.java:7](../../src/test/java/coupons/PurchaseItemTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/PurchaseItemTest.java:8](../../src/test/java/coupons/PurchaseItemTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertNotNull;` |
| [src/test/java/coupons/PurchaseItemTest.java:9](../../src/test/java/coupons/PurchaseItemTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertNotSame;` |
| [src/test/java/coupons/PurchaseItemTest.java:10](../../src/test/java/coupons/PurchaseItemTest.java#L10) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/PurchaseItemTest.java:11](../../src/test/java/coupons/PurchaseItemTest.java#L11) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:3](../../src/test/java/coupons/RRUsageAndCarryTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:4](../../src/test/java/coupons/RRUsageAndCarryTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:5](../../src/test/java/coupons/RRUsageAndCarryTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:6](../../src/test/java/coupons/RRUsageAndCarryTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:7](../../src/test/java/coupons/RRUsageAndCarryTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:8](../../src/test/java/coupons/RRUsageAndCarryTest.java#L8) | `import java.nio.file.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:9](../../src/test/java/coupons/RRUsageAndCarryTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:10](../../src/test/java/coupons/RRUsageAndCarryTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:11](../../src/test/java/coupons/RRUsageAndCarryTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/RRUsageAndCarryTest.java:12](../../src/test/java/coupons/RRUsageAndCarryTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/RebateBenefitTest.java:3](../../src/test/java/coupons/RebateBenefitTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RebateBenefitTest.java:4](../../src/test/java/coupons/RebateBenefitTest.java#L4) | `import java.util.Currency;` |
| [src/test/java/coupons/RebateBenefitTest.java:5](../../src/test/java/coupons/RebateBenefitTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RebateBenefitTest.java:6](../../src/test/java/coupons/RebateBenefitTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/RebateClaimTest.java:3](../../src/test/java/coupons/RebateClaimTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RebateClaimTest.java:4](../../src/test/java/coupons/RebateClaimTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/RebateClaimTest.java:5](../../src/test/java/coupons/RebateClaimTest.java#L5) | `import java.util.ArrayList;` |
| [src/test/java/coupons/RebateClaimTest.java:6](../../src/test/java/coupons/RebateClaimTest.java#L6) | `import java.util.Arrays;` |
| [src/test/java/coupons/RebateClaimTest.java:7](../../src/test/java/coupons/RebateClaimTest.java#L7) | `import java.util.Currency;` |
| [src/test/java/coupons/RebateClaimTest.java:8](../../src/test/java/coupons/RebateClaimTest.java#L8) | `import java.util.List;` |
| [src/test/java/coupons/RebateClaimTest.java:9](../../src/test/java/coupons/RebateClaimTest.java#L9) | `import java.util.UUID;` |
| [src/test/java/coupons/RebateClaimTest.java:10](../../src/test/java/coupons/RebateClaimTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RebateClaimTest.java:11](../../src/test/java/coupons/RebateClaimTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:3](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:4](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:5](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L5) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:6](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L6) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:7](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:8](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L8) | `import java.util.Currency;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:9](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L9) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:10](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L10) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:11](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L11) | `import java.util.UUID;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:12](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L12) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptCliRevisionTest.java:13](../../src/test/java/coupons/ReceiptCliRevisionTest.java#L13) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptDateInputTest.java:3](../../src/test/java/coupons/ReceiptDateInputTest.java#L3) | `import java.time.LocalDate;` |
| [src/test/java/coupons/ReceiptDateInputTest.java:4](../../src/test/java/coupons/ReceiptDateInputTest.java#L4) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptDateInputTest.java:5](../../src/test/java/coupons/ReceiptDateInputTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptDateInputTest.java:6](../../src/test/java/coupons/ReceiptDateInputTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:3](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:4](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L4) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:5](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L5) | `import java.nio.file.*;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:6](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L6) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:7](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:8](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L8) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptDraftRecoveryTest.java:9](../../src/test/java/coupons/ReceiptDraftRecoveryTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:3](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:4](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:5](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:6](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:7](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L7) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:8](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptEarningNavigationTest.java:9](../../src/test/java/coupons/ReceiptEarningNavigationTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:3](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:4](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:5](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:6](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:7](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:8](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:9](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptEditFieldsTest.java:10](../../src/test/java/coupons/ReceiptEditFieldsTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:3](../../src/test/java/coupons/ReceiptEntryAppTest.java#L3) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:4](../../src/test/java/coupons/ReceiptEntryAppTest.java#L4) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:5](../../src/test/java/coupons/ReceiptEntryAppTest.java#L5) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:6](../../src/test/java/coupons/ReceiptEntryAppTest.java#L6) | `import java.time.LocalDate;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:7](../../src/test/java/coupons/ReceiptEntryAppTest.java#L7) | `import coupons.repository.InMemoryTransactionRepository;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:8](../../src/test/java/coupons/ReceiptEntryAppTest.java#L8) | `import coupons.repository.ReceiptMeta;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:9](../../src/test/java/coupons/ReceiptEntryAppTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptEntryAppTest.java:10](../../src/test/java/coupons/ReceiptEntryAppTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptFileFormatTest.java:2](../../src/test/java/coupons/ReceiptFileFormatTest.java#L2) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptFileFormatTest.java:3](../../src/test/java/coupons/ReceiptFileFormatTest.java#L3) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptFileFormatTest.java:4](../../src/test/java/coupons/ReceiptFileFormatTest.java#L4) | `import java.nio.file.*;` |
| [src/test/java/coupons/ReceiptFileFormatTest.java:5](../../src/test/java/coupons/ReceiptFileFormatTest.java#L5) | `import java.nio.charset.StandardCharsets;` |
| [src/test/java/coupons/ReceiptFileFormatTest.java:6](../../src/test/java/coupons/ReceiptFileFormatTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptFooterTest.java:3](../../src/test/java/coupons/ReceiptFooterTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptFooterTest.java:4](../../src/test/java/coupons/ReceiptFooterTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptFooterTest.java:5](../../src/test/java/coupons/ReceiptFooterTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptFooterTest.java:6](../../src/test/java/coupons/ReceiptFooterTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptFooterTest.java:7](../../src/test/java/coupons/ReceiptFooterTest.java#L7) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptFooterTest.java:8](../../src/test/java/coupons/ReceiptFooterTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptFooterTest.java:9](../../src/test/java/coupons/ReceiptFooterTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:3](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:4](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:5](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:6](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:7](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L7) | `import java.time.Instant;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:8](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L8) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:9](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L9) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:10](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L10) | `import java.util.UUID;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:11](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L11) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptHistoryDisplayTest.java:12](../../src/test/java/coupons/ReceiptHistoryDisplayTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:3](../../src/test/java/coupons/ReceiptImportTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:4](../../src/test/java/coupons/ReceiptImportTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:5](../../src/test/java/coupons/ReceiptImportTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:6](../../src/test/java/coupons/ReceiptImportTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptImportTest.java:7](../../src/test/java/coupons/ReceiptImportTest.java#L7) | `import java.nio.file.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:8](../../src/test/java/coupons/ReceiptImportTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptImportTest.java:9](../../src/test/java/coupons/ReceiptImportTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptImportTest.java:10](../../src/test/java/coupons/ReceiptImportTest.java#L10) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptImportTest.java:11](../../src/test/java/coupons/ReceiptImportTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:3](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:4](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:5](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:6](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:7](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L7) | `import java.nio.file.Path;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:8](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L8) | `import java.time.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:9](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:10](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:11](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java:12](../../src/test/java/coupons/ReceiptIssuesAndOnlineOrdersTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptLifecycleCliTest.java:3](../../src/test/java/coupons/ReceiptLifecycleCliTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptLifecycleCliTest.java:4](../../src/test/java/coupons/ReceiptLifecycleCliTest.java#L4) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptLifecycleCliTest.java:5](../../src/test/java/coupons/ReceiptLifecycleCliTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptLifecycleCliTest.java:6](../../src/test/java/coupons/ReceiptLifecycleCliTest.java#L6) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptLifecycleCliTest.java:7](../../src/test/java/coupons/ReceiptLifecycleCliTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:3](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:4](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:5](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:6](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:7](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L7) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:8](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptMenuCleanupTest.java:9](../../src/test/java/coupons/ReceiptMenuCleanupTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:3](../../src/test/java/coupons/ReceiptPresentationTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:4](../../src/test/java/coupons/ReceiptPresentationTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:5](../../src/test/java/coupons/ReceiptPresentationTest.java#L5) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:6](../../src/test/java/coupons/ReceiptPresentationTest.java#L6) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:7](../../src/test/java/coupons/ReceiptPresentationTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:8](../../src/test/java/coupons/ReceiptPresentationTest.java#L8) | `import java.util.Currency;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:9](../../src/test/java/coupons/ReceiptPresentationTest.java#L9) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:10](../../src/test/java/coupons/ReceiptPresentationTest.java#L10) | `import java.util.Map;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:11](../../src/test/java/coupons/ReceiptPresentationTest.java#L11) | `import java.util.UUID;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:12](../../src/test/java/coupons/ReceiptPresentationTest.java#L12) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptPresentationTest.java:13](../../src/test/java/coupons/ReceiptPresentationTest.java#L13) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:3](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:4](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L4) | `import java.io.*;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:5](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:6](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:7](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:8](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L8) | `import java.nio.file.Path;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:9](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:10](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L10) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java:11](../../src/test/java/coupons/ReceiptPromotionAndBalancesCliTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptRestartTest.java:3](../../src/test/java/coupons/ReceiptRestartTest.java#L3) | `import java.nio.file.Files;` |
| [src/test/java/coupons/ReceiptRestartTest.java:4](../../src/test/java/coupons/ReceiptRestartTest.java#L4) | `import java.nio.file.Path;` |
| [src/test/java/coupons/ReceiptRestartTest.java:5](../../src/test/java/coupons/ReceiptRestartTest.java#L5) | `import java.util.concurrent.TimeUnit;` |
| [src/test/java/coupons/ReceiptRestartTest.java:6](../../src/test/java/coupons/ReceiptRestartTest.java#L6) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptRestartTest.java:7](../../src/test/java/coupons/ReceiptRestartTest.java#L7) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/ReceiptRestartTest.java:8](../../src/test/java/coupons/ReceiptRestartTest.java#L8) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:3](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:4](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:5](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:6](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:7](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L7) | `import java.time.Instant;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:8](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L8) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:9](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L9) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:10](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L10) | `import java.util.UUID;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:11](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L11) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptSavedEditingTest.java:12](../../src/test/java/coupons/ReceiptSavedEditingTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptTimeInputTest.java:3](../../src/test/java/coupons/ReceiptTimeInputTest.java#L3) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptTimeInputTest.java:4](../../src/test/java/coupons/ReceiptTimeInputTest.java#L4) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:3](../../src/test/java/coupons/ReceiptWorkflowTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:4](../../src/test/java/coupons/ReceiptWorkflowTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:5](../../src/test/java/coupons/ReceiptWorkflowTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:6](../../src/test/java/coupons/ReceiptWorkflowTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:7](../../src/test/java/coupons/ReceiptWorkflowTest.java#L7) | `import java.util.List;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:8](../../src/test/java/coupons/ReceiptWorkflowTest.java#L8) | `import java.util.Scanner;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:9](../../src/test/java/coupons/ReceiptWorkflowTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ReceiptWorkflowTest.java:10](../../src/test/java/coupons/ReceiptWorkflowTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:3](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:4](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:5](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L5) | `import java.util.List;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:6](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L6) | `import java.util.UUID;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:8](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:9](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertNotNull;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:10](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L10) | `import static org.junit.jupiter.api.Assertions.assertNotSame;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:11](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L11) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:12](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L12) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/RegisterRewardRedemptionTest.java:13](../../src/test/java/coupons/RegisterRewardRedemptionTest.java#L13) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RegisterRewardTest.java:3](../../src/test/java/coupons/RegisterRewardTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RegisterRewardTest.java:4](../../src/test/java/coupons/RegisterRewardTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/RegisterRewardTest.java:5](../../src/test/java/coupons/RegisterRewardTest.java#L5) | `import java.util.List;` |
| [src/test/java/coupons/RegisterRewardTest.java:6](../../src/test/java/coupons/RegisterRewardTest.java#L6) | `import java.util.UUID;` |
| [src/test/java/coupons/RegisterRewardTest.java:7](../../src/test/java/coupons/RegisterRewardTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RegisterRewardTest.java:9](../../src/test/java/coupons/RegisterRewardTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:3](../../src/test/java/coupons/RewardSpendCheckTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:4](../../src/test/java/coupons/RewardSpendCheckTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:5](../../src/test/java/coupons/RewardSpendCheckTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:6](../../src/test/java/coupons/RewardSpendCheckTest.java#L6) | `import java.time.Instant;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:7](../../src/test/java/coupons/RewardSpendCheckTest.java#L7) | `import java.nio.file.Path;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:8](../../src/test/java/coupons/RewardSpendCheckTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:9](../../src/test/java/coupons/RewardSpendCheckTest.java#L9) | `import java.io.*;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:10](../../src/test/java/coupons/RewardSpendCheckTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:11](../../src/test/java/coupons/RewardSpendCheckTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/RewardSpendCheckTest.java:12](../../src/test/java/coupons/RewardSpendCheckTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:3](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:4](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:5](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:6](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:7](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:8](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L8) | `import java.nio.file.Path;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:9](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:10](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:11](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/SaleAdjustedRewardsTest.java:12](../../src/test/java/coupons/SaleAdjustedRewardsTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/SavingsReportTest.java:3](../../src/test/java/coupons/SavingsReportTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/SavingsReportTest.java:4](../../src/test/java/coupons/SavingsReportTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/SavingsReportTest.java:5](../../src/test/java/coupons/SavingsReportTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/SavingsReportTest.java:6](../../src/test/java/coupons/SavingsReportTest.java#L6) | `import java.math.*;` |
| [src/test/java/coupons/SavingsReportTest.java:7](../../src/test/java/coupons/SavingsReportTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/SavingsReportTest.java:8](../../src/test/java/coupons/SavingsReportTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/SavingsReportTest.java:9](../../src/test/java/coupons/SavingsReportTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/SavingsReportTest.java:10](../../src/test/java/coupons/SavingsReportTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:3](../../src/test/java/coupons/SharedCouponEntryTest.java#L3) | `import coupons.repository.*;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:4](../../src/test/java/coupons/SharedCouponEntryTest.java#L4) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:5](../../src/test/java/coupons/SharedCouponEntryTest.java#L5) | `import java.io.PrintStream;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:6](../../src/test/java/coupons/SharedCouponEntryTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:7](../../src/test/java/coupons/SharedCouponEntryTest.java#L7) | `import java.nio.file.Path;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:8](../../src/test/java/coupons/SharedCouponEntryTest.java#L8) | `import java.util.List;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:9](../../src/test/java/coupons/SharedCouponEntryTest.java#L9) | `import java.util.Scanner;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:10](../../src/test/java/coupons/SharedCouponEntryTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:11](../../src/test/java/coupons/SharedCouponEntryTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/SharedCouponEntryTest.java:12](../../src/test/java/coupons/SharedCouponEntryTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ShoppingEstimateTest.java:2](../../src/test/java/coupons/ShoppingEstimateTest.java#L2) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ShoppingEstimateTest.java:3](../../src/test/java/coupons/ShoppingEstimateTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ShoppingEstimateTest.java:4](../../src/test/java/coupons/ShoppingEstimateTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/ShoppingEstimateTest.java:5](../../src/test/java/coupons/ShoppingEstimateTest.java#L5) | `import java.util.*;` |
| [src/test/java/coupons/ShoppingEstimateTest.java:6](../../src/test/java/coupons/ShoppingEstimateTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:3](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:4](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:5](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L5) | `import java.util.List;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:6](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L6) | `import java.util.UUID;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:7](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/ShoppingPlanAndCouponTest.java:8](../../src/test/java/coupons/ShoppingPlanAndCouponTest.java#L8) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/SourceArtifactTest.java:3](../../src/test/java/coupons/SourceArtifactTest.java#L3) | `import java.nio.charset.StandardCharsets;` |
| [src/test/java/coupons/SourceArtifactTest.java:4](../../src/test/java/coupons/SourceArtifactTest.java#L4) | `import java.time.Instant;` |
| [src/test/java/coupons/SourceArtifactTest.java:5](../../src/test/java/coupons/SourceArtifactTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/SourceArtifactTest.java:6](../../src/test/java/coupons/SourceArtifactTest.java#L6) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/SourceArtifactTest.java:7](../../src/test/java/coupons/SourceArtifactTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:3](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:4](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:5](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L5) | `import java.io.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:6](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:7](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L7) | `import java.nio.file.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:8](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L8) | `import java.time.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:9](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:10](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:11](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/SpreadsheetBalancePersistenceTest.java:12](../../src/test/java/coupons/SpreadsheetBalancePersistenceTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:3](../../src/test/java/coupons/TransactionCalculatorTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:4](../../src/test/java/coupons/TransactionCalculatorTest.java#L4) | `import java.util.List;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:5](../../src/test/java/coupons/TransactionCalculatorTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:7](../../src/test/java/coupons/TransactionCalculatorTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:8](../../src/test/java/coupons/TransactionCalculatorTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:9](../../src/test/java/coupons/TransactionCalculatorTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:10](../../src/test/java/coupons/TransactionCalculatorTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/TransactionCalculatorTest.java:12](../../src/test/java/coupons/TransactionCalculatorTest.java#L12) | `import coupons.repository.ReceiptSnapshot;` |
| [src/test/java/coupons/WalgreensImageImportTest.java:3](../../src/test/java/coupons/WalgreensImageImportTest.java#L3) | `import java.nio.file.*;` |
| [src/test/java/coupons/WalgreensImageImportTest.java:4](../../src/test/java/coupons/WalgreensImageImportTest.java#L4) | `import java.util.*;` |
| [src/test/java/coupons/WalgreensImageImportTest.java:5](../../src/test/java/coupons/WalgreensImageImportTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/WalgreensImageImportTest.java:6](../../src/test/java/coupons/WalgreensImageImportTest.java#L6) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/WalgreensImageImportTest.java:7](../../src/test/java/coupons/WalgreensImageImportTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:3](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:4](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:5](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:6](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L6) | `import java.nio.file.Path;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:7](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L7) | `import java.time.Instant;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:8](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:9](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:10](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L10) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/WholeReceiptDiscountTest.java:11](../../src/test/java/coupons/WholeReceiptDiscountTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/WorkbookTransactionTest.java:3](../../src/test/java/coupons/WorkbookTransactionTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/WorkbookTransactionTest.java:4](../../src/test/java/coupons/WorkbookTransactionTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/WorkbookTransactionTest.java:6](../../src/test/java/coupons/WorkbookTransactionTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/WorkbookTransactionTest.java:7](../../src/test/java/coupons/WorkbookTransactionTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:3](../../src/test/java/coupons/api/CouponApiServiceTest.java#L3) | `import com.sun.net.httpserver.HttpServer;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:4](../../src/test/java/coupons/api/CouponApiServiceTest.java#L4) | `import coupons.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:5](../../src/test/java/coupons/api/CouponApiServiceTest.java#L5) | `import coupons.repository.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:6](../../src/test/java/coupons/api/CouponApiServiceTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:7](../../src/test/java/coupons/api/CouponApiServiceTest.java#L7) | `import java.net.URI;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:8](../../src/test/java/coupons/api/CouponApiServiceTest.java#L8) | `import java.net.http.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:9](../../src/test/java/coupons/api/CouponApiServiceTest.java#L9) | `import java.time.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:10](../../src/test/java/coupons/api/CouponApiServiceTest.java#L10) | `import java.util.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:11](../../src/test/java/coupons/api/CouponApiServiceTest.java#L11) | `import java.util.concurrent.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:12](../../src/test/java/coupons/api/CouponApiServiceTest.java#L12) | `import org.junit.jupiter.api.*;` |
| [src/test/java/coupons/api/CouponApiServiceTest.java:13](../../src/test/java/coupons/api/CouponApiServiceTest.java#L13) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:3](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L3) | `import com.sun.net.httpserver.HttpServer;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:4](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L4) | `import coupons.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:5](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L5) | `import coupons.repository.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:6](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L6) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:7](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L7) | `import java.net.URI;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:8](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L8) | `import java.net.http.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:9](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L9) | `import java.time.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:10](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L10) | `import java.sql.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:11](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L11) | `import java.util.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:12](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L12) | `import java.util.concurrent.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:13](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L13) | `import org.junit.jupiter.api.*;` |
| [src/test/java/coupons/api/PostgresApiIntegrationTest.java:14](../../src/test/java/coupons/api/PostgresApiIntegrationTest.java#L14) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:2](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L2) | `import coupons.*;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:3](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L3) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:4](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:5](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L5) | `import java.util.*;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:6](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L6) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java:7](../../src/test/java/coupons/api/ReceiptEarningStatusUpdateTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:3](../../src/test/java/coupons/api/ReceiptInputTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:4](../../src/test/java/coupons/api/ReceiptInputTest.java#L4) | `import coupons.repository.*;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:5](../../src/test/java/coupons/api/ReceiptInputTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:6](../../src/test/java/coupons/api/ReceiptInputTest.java#L6) | `import java.util.*;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:7](../../src/test/java/coupons/api/ReceiptInputTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/api/ReceiptInputTest.java:8](../../src/test/java/coupons/api/ReceiptInputTest.java#L8) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:3](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:4](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:5](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L5) | `import java.time.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:6](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L6) | `import java.nio.file.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:7](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:8](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:9](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L9) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:10](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/AtomicReceiptPromotionTest.java:102](../../src/test/java/coupons/repository/AtomicReceiptPromotionTest.java#L102) | `import java.time.*; import java.util.*; import java.math.*;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:3](../../src/test/java/coupons/repository/CouponSnapTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:4](../../src/test/java/coupons/repository/CouponSnapTest.java#L4) | `import java.time.LocalDate;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:5](../../src/test/java/coupons/repository/CouponSnapTest.java#L5) | `import java.util.List;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:7](../../src/test/java/coupons/repository/CouponSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:8](../../src/test/java/coupons/repository/CouponSnapTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:9](../../src/test/java/coupons/repository/CouponSnapTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:10](../../src/test/java/coupons/repository/CouponSnapTest.java#L10) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:11](../../src/test/java/coupons/repository/CouponSnapTest.java#L11) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:13](../../src/test/java/coupons/repository/CouponSnapTest.java#L13) | `import coupons.Coupon;` |
| [src/test/java/coupons/repository/CouponSnapTest.java:14](../../src/test/java/coupons/repository/CouponSnapTest.java#L14) | `import coupons.CouponType;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:3](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L3) | `import java.util.ArrayList;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:4](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L4) | `import java.util.Arrays;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:5](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L5) | `import java.util.List;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:6](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L6) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:7](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/EvidenceBatchValidatorTest.java:8](../../src/test/java/coupons/repository/EvidenceBatchValidatorTest.java#L8) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:3](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:4](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:5](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L5) | `import java.time.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:6](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L6) | `import java.nio.file.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:7](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:8](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:9](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L9) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:10](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/GiftCardPromotionTest.java:106](../../src/test/java/coupons/repository/GiftCardPromotionTest.java#L106) | `import java.util.*;` |
| [src/test/java/coupons/repository/InMemoryStoreTest.java:3](../../src/test/java/coupons/repository/InMemoryStoreTest.java#L3) | `import java.util.HashMap;` |
| [src/test/java/coupons/repository/InMemoryStoreTest.java:4](../../src/test/java/coupons/repository/InMemoryStoreTest.java#L4) | `import java.util.Map;` |
| [src/test/java/coupons/repository/InMemoryStoreTest.java:5](../../src/test/java/coupons/repository/InMemoryStoreTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/InMemoryStoreTest.java:6](../../src/test/java/coupons/repository/InMemoryStoreTest.java#L6) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/InMemoryStoreTest.java:7](../../src/test/java/coupons/repository/InMemoryStoreTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:3](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:4](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:5](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L5) | `import java.time.Clock;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:6](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L6) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:7](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L7) | `import java.time.LocalDateTime;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:8](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L8) | `import java.time.ZoneOffset;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:9](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L9) | `import java.util.ArrayList;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:10](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L10) | `import java.util.List;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:11](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L11) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:12](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L12) | `import java.util.concurrent.Callable;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:13](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L13) | `import java.util.concurrent.CountDownLatch;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:14](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L14) | `import java.util.concurrent.Executors;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:15](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L15) | `import java.util.concurrent.TimeUnit;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:16](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L16) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java:17](../../src/test/java/coupons/repository/InMemoryTransactionRepositoryTest.java#L17) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/InventoryAdjustCtxTest.java:3](../../src/test/java/coupons/repository/InventoryAdjustCtxTest.java#L3) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/InventoryAdjustCtxTest.java:4](../../src/test/java/coupons/repository/InventoryAdjustCtxTest.java#L4) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/InventoryAdjustCtxTest.java:5](../../src/test/java/coupons/repository/InventoryAdjustCtxTest.java#L5) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:3](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:4](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L4) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:5](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:6](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:7](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L7) | `import java.util.*;` |
| [src/test/java/coupons/repository/LifecycleReferenceValidationTest.java:8](../../src/test/java/coupons/repository/LifecycleReferenceValidationTest.java#L8) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:3](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:4](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:5](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:6](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L6) | `import java.nio.file.Files;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:7](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L7) | `import java.nio.file.Path;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:8](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L8) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:9](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L9) | `import java.time.LocalDate;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:10](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L10) | `import java.util.Currency;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:11](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L11) | `import java.util.List;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:12](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L12) | `import java.util.Map;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:13](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L13) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:14](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L14) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:15](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L15) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/LocalReceiptPersistenceTest.java:16](../../src/test/java/coupons/repository/LocalReceiptPersistenceTest.java#L16) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:3](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:4](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:5](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:6](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L6) | `import java.nio.file.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:7](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L7) | `import java.sql.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:8](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L8) | `import java.time.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:9](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L9) | `import java.util.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:10](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L10) | `import org.junit.jupiter.api.*;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:11](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L11) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/LocalToPostgresMigrationTest.java:12](../../src/test/java/coupons/repository/LocalToPostgresMigrationTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:3](../../src/test/java/coupons/repository/PaymentSnapTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:4](../../src/test/java/coupons/repository/PaymentSnapTest.java#L4) | `import java.util.List;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:6](../../src/test/java/coupons/repository/PaymentSnapTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:7](../../src/test/java/coupons/repository/PaymentSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:8](../../src/test/java/coupons/repository/PaymentSnapTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:9](../../src/test/java/coupons/repository/PaymentSnapTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:11](../../src/test/java/coupons/repository/PaymentSnapTest.java#L11) | `import coupons.Payment;` |
| [src/test/java/coupons/repository/PaymentSnapTest.java:12](../../src/test/java/coupons/repository/PaymentSnapTest.java#L12) | `import coupons.PaymentType;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:3](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:4](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:5](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L5) | `import java.sql.Connection;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:6](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L6) | `import java.time.*;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:7](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L7) | `import java.util.List;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:8](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L8) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:9](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L9) | `import java.util.ConcurrentModificationException;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:10](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L10) | `import org.junit.jupiter.api.*;` |
| [src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java:12](../../src/test/java/coupons/repository/PostgresMilestone2RepositoryTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:4](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:5](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L5) | `import java.util.LinkedHashMap;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:6](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L6) | `import java.util.List;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:7](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L7) | `import java.util.Map;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:8](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L8) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:10](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L10) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:11](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L11) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:12](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L12) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:13](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L13) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java:15](../../src/test/java/coupons/repository/PostgresPlanCheckoutCalculationTest.java#L15) | `import coupons.ShoppingEstimate;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:3](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L3) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:5](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:6](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L6) | `import java.sql.Connection;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:7](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L7) | `import java.time.*;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:8](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L8) | `import java.util.*;` |
| [src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java:9](../../src/test/java/coupons/repository/PostgresProviderAccountRepositoryTest.java#L9) | `import org.junit.jupiter.api.*;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:3](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L3) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:4](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L4) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:5](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L5) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:7](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L7) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:8](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L8) | `import coupons.PurchaseItem;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:9](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L9) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:10](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L10) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:11](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L11) | `import java.sql.Connection;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:12](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L12) | `import java.time.Clock;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:13](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L13) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:14](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L14) | `import java.time.ZoneOffset;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:15](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L15) | `import java.util.List;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:16](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L16) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:17](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L17) | `import org.junit.jupiter.api.Assumptions;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:18](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L18) | `import org.junit.jupiter.api.BeforeEach;` |
| [src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java:19](../../src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java#L19) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:3](../../src/test/java/coupons/repository/PromotionSnapTest.java#L3) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:4](../../src/test/java/coupons/repository/PromotionSnapTest.java#L4) | `import static org.junit.jupiter.api.Assertions.assertFalse;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:5](../../src/test/java/coupons/repository/PromotionSnapTest.java#L5) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:6](../../src/test/java/coupons/repository/PromotionSnapTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:7](../../src/test/java/coupons/repository/PromotionSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:8](../../src/test/java/coupons/repository/PromotionSnapTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:10](../../src/test/java/coupons/repository/PromotionSnapTest.java#L10) | `import coupons.Promotion;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:11](../../src/test/java/coupons/repository/PromotionSnapTest.java#L11) | `import coupons.PromotionType;` |
| [src/test/java/coupons/repository/PromotionSnapTest.java:12](../../src/test/java/coupons/repository/PromotionSnapTest.java#L12) | `import coupons.RewardType;` |
| [src/test/java/coupons/repository/PurchaseLineSnapTest.java:3](../../src/test/java/coupons/repository/PurchaseLineSnapTest.java#L3) | `import coupons.PurchaseItem;` |
| [src/test/java/coupons/repository/PurchaseLineSnapTest.java:4](../../src/test/java/coupons/repository/PurchaseLineSnapTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/PurchaseLineSnapTest.java:5](../../src/test/java/coupons/repository/PurchaseLineSnapTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/PurchaseLineSnapTest.java:6](../../src/test/java/coupons/repository/PurchaseLineSnapTest.java#L6) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/PurchaseLineSnapTest.java:7](../../src/test/java/coupons/repository/PurchaseLineSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptCreateCtxTest.java:3](../../src/test/java/coupons/repository/ReceiptCreateCtxTest.java#L3) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/ReceiptCreateCtxTest.java:4](../../src/test/java/coupons/repository/ReceiptCreateCtxTest.java#L4) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/ReceiptCreateCtxTest.java:5](../../src/test/java/coupons/repository/ReceiptCreateCtxTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:3](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L3) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:4](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L4) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:5](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:6](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L6) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:7](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L7) | `import java.util.ArrayList;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:8](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L8) | `import java.util.Arrays;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:9](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L9) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:10](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L10) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:11](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L11) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java:12](../../src/test/java/coupons/repository/ReceiptEvidenceRefsTest.java#L12) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptEvidenceTest.java:3](../../src/test/java/coupons/repository/ReceiptEvidenceTest.java#L3) | `import java.nio.charset.StandardCharsets;` |
| [src/test/java/coupons/repository/ReceiptEvidenceTest.java:4](../../src/test/java/coupons/repository/ReceiptEvidenceTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptEvidenceTest.java:5](../../src/test/java/coupons/repository/ReceiptEvidenceTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptEvidenceTest.java:6](../../src/test/java/coupons/repository/ReceiptEvidenceTest.java#L6) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:3](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:4](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L4) | `import coupons.ReceiptObservations.WalgreensCashAccount;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:5](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:7](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:8](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L8) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:9](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L9) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:10](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L10) | `import java.util.Map;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:11](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L11) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java:13](../../src/test/java/coupons/repository/ReceiptFinancialRegressionTest.java#L13) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:3](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L3) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:4](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L4) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:5](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:6](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L6) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:7](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L7) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:8](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptHistoryEntryTest.java:9](../../src/test/java/coupons/repository/ReceiptHistoryEntryTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:3](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:4](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:5](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:6](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L6) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:7](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:8](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L8) | `import java.nio.file.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:9](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L9) | `import java.time.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:10](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L10) | `import java.util.*;` |
| [src/test/java/coupons/repository/ReceiptLifecycleTest.java:11](../../src/test/java/coupons/repository/ReceiptLifecycleTest.java#L11) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptMetaTest.java:3](../../src/test/java/coupons/repository/ReceiptMetaTest.java#L3) | `import java.time.LocalDateTime;` |
| [src/test/java/coupons/repository/ReceiptMetaTest.java:4](../../src/test/java/coupons/repository/ReceiptMetaTest.java#L4) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptMetaTest.java:5](../../src/test/java/coupons/repository/ReceiptMetaTest.java#L5) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:3](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L3) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:4](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L4) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:5](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:6](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L6) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:7](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L7) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:8](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java:9](../../src/test/java/coupons/repository/ReceiptMetadataCaptureTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:3](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:4](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L4) | `import coupons.ReceiptObservations.*;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:5](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L5) | `import java.io.ByteArrayOutputStream;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:6](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L6) | `import java.io.PrintStream;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:7](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L7) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:8](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L8) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:9](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L9) | `import java.util.ArrayList;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:10](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L10) | `import java.util.Currency;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:11](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L11) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:12](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L12) | `import java.util.Map;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:13](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L13) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:14](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L14) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptObservationsTest.java:15](../../src/test/java/coupons/repository/ReceiptObservationsTest.java#L15) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:3](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:4](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:5](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L5) | `import java.time.LocalDate;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:6](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L6) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:7](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L7) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:8](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptRestoreTest.java:9](../../src/test/java/coupons/repository/ReceiptRestoreTest.java#L9) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:3](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:4](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:6](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:7](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:8](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:9](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:10](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:12](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L12) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:13](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L13) | `import coupons.Payment;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:14](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L14) | `import coupons.PaymentType;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:15](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L15) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptRevSnapTest.java:16](../../src/test/java/coupons/repository/ReceiptRevSnapTest.java#L16) | `import coupons.RegisterRewardRedemption;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:3](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:4](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:5](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L5) | `import java.util.ArrayList;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:6](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L6) | `import java.util.Arrays;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:7](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L7) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:8](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L8) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:9](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptSnapshotTest.java:10](../../src/test/java/coupons/repository/ReceiptSnapshotTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptTotalsSnapTest.java:3](../../src/test/java/coupons/repository/ReceiptTotalsSnapTest.java#L3) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptTotalsSnapTest.java:4](../../src/test/java/coupons/repository/ReceiptTotalsSnapTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptTotalsSnapTest.java:5](../../src/test/java/coupons/repository/ReceiptTotalsSnapTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptTotalsSnapTest.java:7](../../src/test/java/coupons/repository/ReceiptTotalsSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:3](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L3) | `import coupons.*;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:4](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L4) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:5](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L5) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:6](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L6) | `import java.util.*;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:7](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L7) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:8](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L8) | `import org.junit.jupiter.api.io.TempDir;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:9](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L9) | `import java.nio.file.Path;` |
| [src/test/java/coupons/repository/ReceiptUpdateTest.java:10](../../src/test/java/coupons/repository/ReceiptUpdateTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:3](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L3) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:4](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L4) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:5](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L5) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:7](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:8](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:9](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L9) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:10](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L10) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:12](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L12) | `import coupons.CouponTransaction;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:13](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L13) | `import coupons.ReceiptTotals;` |
| [src/test/java/coupons/repository/ReceiptValidatorTest.java:14](../../src/test/java/coupons/repository/ReceiptValidatorTest.java#L14) | `import coupons.RegisterRewardRedemption;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:3](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L3) | `import coupons.CouponApplied;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:4](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L4) | `import coupons.RegisterRewardRedemption;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:5](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L5) | `import java.math.BigDecimal;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:6](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L6) | `import java.time.LocalDate;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:7](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L7) | `import java.util.List;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:8](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L8) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:9](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptValueEqualityTest.java:10](../../src/test/java/coupons/repository/ReceiptValueEqualityTest.java#L10) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/ReceiptWriteRequestTest.java:3](../../src/test/java/coupons/repository/ReceiptWriteRequestTest.java#L3) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptWriteRequestTest.java:4](../../src/test/java/coupons/repository/ReceiptWriteRequestTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptWriteRequestTest.java:6](../../src/test/java/coupons/repository/ReceiptWriteRequestTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/ReceiptWriteRequestTest.java:7](../../src/test/java/coupons/repository/ReceiptWriteRequestTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/ReceiptWriteRequestTest.java:8](../../src/test/java/coupons/repository/ReceiptWriteRequestTest.java#L8) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptWriteResultTest.java:3](../../src/test/java/coupons/repository/ReceiptWriteResultTest.java#L3) | `import java.time.Instant;` |
| [src/test/java/coupons/repository/ReceiptWriteResultTest.java:4](../../src/test/java/coupons/repository/ReceiptWriteResultTest.java#L4) | `import java.util.UUID;` |
| [src/test/java/coupons/repository/ReceiptWriteResultTest.java:5](../../src/test/java/coupons/repository/ReceiptWriteResultTest.java#L5) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/ReceiptWriteResultTest.java:7](../../src/test/java/coupons/repository/ReceiptWriteResultTest.java#L7) | `import static org.junit.jupiter.api.Assertions.*;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:3](../../src/test/java/coupons/repository/RewardSnapTest.java#L3) | `import static org.junit.jupiter.api.Assertions.assertEquals;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:4](../../src/test/java/coupons/repository/RewardSnapTest.java#L4) | `import static org.junit.jupiter.api.Assertions.assertFalse;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:5](../../src/test/java/coupons/repository/RewardSnapTest.java#L5) | `import static org.junit.jupiter.api.Assertions.assertNotEquals;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:6](../../src/test/java/coupons/repository/RewardSnapTest.java#L6) | `import static org.junit.jupiter.api.Assertions.assertNull;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:7](../../src/test/java/coupons/repository/RewardSnapTest.java#L7) | `import static org.junit.jupiter.api.Assertions.assertThrows;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:8](../../src/test/java/coupons/repository/RewardSnapTest.java#L8) | `import static org.junit.jupiter.api.Assertions.assertTrue;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:9](../../src/test/java/coupons/repository/RewardSnapTest.java#L9) | `import org.junit.jupiter.api.Test;` |
| [src/test/java/coupons/repository/RewardSnapTest.java:11](../../src/test/java/coupons/repository/RewardSnapTest.java#L11) | `import coupons.Reward;` |
## Revision 1.2 — receipt evidence and reconciliation

ReceiptDetail now imports ReceiptFinancialReview from existing ReceiptCreate.tsx (ReceiptCreate does not import ReceiptDetail). TransactionCalculator uses ReceiptMeta for observed basis/completeness; existing domain code already uses repository types. No package moves or dependency framework changes. Earlier import index is the original snapshot.



## October 23 incremental receipt workflow dependencies

- CouponApiService -> ReceiptCorrection -> ReceiptInput parsing helpers, saved
  ReceiptSnapshot, ReceiptMeta, ReceiptValidator and existing receipt repositories.
- ReceiptDetail -> ReceiptEdit / ReceiptStatusAction -> ApiClient, shared
  ReceiptFinancialReview and existing financial response types.
- WorkspacePages -> receiptBrowse: search, ordering, original-time formatting and
  Sunday–Saturday shopping-week labels. PostgresPlatformRepository supplies monetary
  period summaries and parsed sortTime.
- No new external package was introduced. The original import inventory above is
  a historical snapshot; these entries document the subsequent receipt changes.

ReceiptCreate and ReceiptEdit now use ProductPricingInfo to colocate pricing guidance
and linked recorded deductions with each product. It uses the existing money formatter
and adds no calculation or external dependency.

October 23 item-entry update: ItemDiscountFields replaces ProductPricingInfo in
ReceiptCreate/ReceiptEdit. ReceiptCorrection creates read-only edit projections and
preserves historical costs; CouponApiService includes editDiscounts and
preservePriceLineIds in receipt responses. Existing calculators/repositories remain
the monetary/validation authority. No new external dependency was added.

## Form readability dependencies — October 23, 2026

index.css imports styles/tokens.css. ReceiptCreate, ReceiptEdit and ItemDiscountFields
use components/AutoSizeTextArea.tsx (React refs/layout effect plus ResizeObserver;
no new package). App.css applies dialog-container responsive grids and token-sized
controls. Playwright adds a WebKit project; fixture scenarios block service workers
for isolated API interception. Detailed values live in FRONTEND-DESIGN-SYSTEM.md.


October 23 sizing increase: controls enlarged from 48px to 60px and value fonts
from 16px to 18px. Modal/filter labels are 15px; horizontal control padding is
16px. Dialog maximum width is 1040px. Receipt editor/product fields are full-width
single-column even on desktop. Long descriptions expand. Current specification:
docs/reference/FRONTEND-DESIGN-SYSTEM.md; executable values:
frontend/src/index.css. SRS/SDD internal revision 1.5 supersedes 1.4 sizing.

## Revision 1.6 — current dependency additions

App → WorkspacePages → RewardsAccounts → ApiClient.account → CouponApiService → PostgresPlatformRepository.account → AccountActivityView / ProviderBalanceSummary / PostgresReceiptState. Account opening command → PostgresProviderAccountRepository with API request journal. ReceiptCreate/ReceiptEdit → PrintedFooterFields + printedFooter helpers → ReceiptInput/ReceiptCorrection → PrintedFooter → existing ReceiptObservations. ReceiptDetail → RewardConnection / PrintedFooterView. RegisterRewardLabel.offer supplies shared readable promotion labels. No new third-party dependency.


## Revision 1.7 — receipt RR correction and matching

ReceiptDetail → ReceiptRRMatch → ApiClient.rrMatches / mutate → CouponApiService → PostgresPlatformRepository → ReceiptRRMatching and PostgresReceiptSupplementRepository.matchRR. Availability reuses isolated in-memory rules without modifying persistent state; commit repeats eligibility under the shared state lock. ReceiptEdit → ReceiptCorrection handles separate RR uses, role/account metadata and explicit reclassification within the reviewed correction.


## Revision 1.8 — RR without an earning receipt

ReceiptRRMatch.UnknownRRSource → CouponApiService unknown-source POST → PostgresPlatformRepository.addStandaloneMatch → PostgresReceiptSupplementRepository.addStandaloneMatch → ReceiptRRMatching.standalone → existing RegisterReward/RRUsageLink domain rules. Persistence of coupon and association shares one transaction and lock.


## Revision 1.9 — Rewards points-account display

RewardsAccounts uses its local hasPoints provider presentation rule to gate account list cells, detail summaries/activity, payout unit text and opening entry/payload. No new module, backend dependency or migration.


## Revision 1.10 — independent Rewards inventory

RewardsAccounts → StandaloneRewardPanel → ApiClient.standaloneRewards / mutate → CouponApiService → PostgresPlatformRepository.standaloneRewards/addStandaloneReward → PostgresReceiptSupplementRepository.addStandaloneReward. Later receipt matching uses ReceiptRRMatching.select, which gates historyUnknown source selection on confirmation under the same locked commit path.


## Revision 1.11 — visible recorded receipt calculations

TransactionCalculator.recordedChecks → reconcilePayments for each temporary basis → PostgresPlatformRepository.totals.calculationChecks → typed CalculationScenario → ReceiptFinancialReview / ReceiptCalculationChecks. Read projection only; no persistence dependency or new library.


## Revision 1.12 — simplified receipt totals display

ReceiptCalculationChecks.tsx is removed and ReceiptFinancialReview no longer imports/renders it. Java recordedChecks → totals.calculationChecks and typed response contract remain. PrintedFooterView filters one label at rendering; entry helpers/storage dependencies remain.


## Revision 1.13 — reuse existing files and align CLI rebate display

Current ownership: receipt create/review/shared printed fields and item discounts → ReceiptCreate.tsx; receipt editing/status/matching → ReceiptDetail.tsx; Rewards/account/standalone inventory and reward connection → WorkspacePages.tsx; browse/date/search helpers → api/format.ts; printed draft helpers → rewardStatus.ts; expanding textarea → components/ui.tsx; font/color/size tokens → index.css. AccountActivityView is nested in ProviderBalanceSummary.java; ReceiptRRMatching is nested in PostgresPlatformRepository.java; package-private correction/footer parsers share ReceiptInput.java. All earlier helper test cases remain in existing receipt/calculator/provider/repository test files. The receipt detail depends on existing receipt-create/shared UI and WorkspacePages reward-link exports; draft helpers stay in the existing non-React utility, avoiding reverse receipt dependencies. No new third-party dependency.

### Receipt period reporting update — October 23, 2026

Receipts → Period totals displays Regular price including tax, Actual OOP, Total amount saved, Confirmed benefits, Total Paid after rebates, and Total % saved. Pending rebates are not shown or included. Existing Java CLI savings rules and saved points valuations apply; original receipt data and valuations are unchanged. Cash/card Actual OOP excludes rewards used and gift cards. Percent saved uses regular merchandise plus recorded tax as denominator; a zero/unknown denominator remains Not recorded. Unknown inputs retain existing null/completeness behavior.

Select Purchase day or Month, enter inclusive Start date and End date, or click Month to date (the current local calendar month through today). Selecting a day/month clears the range; entering a range clears day/month. Clear filters returns to All time. Totals cover all active latest receipt revisions across every page, regardless of text search or status filter. Search continues across dates/statuses; without search the receipt list follows the selected period. Date-range loading/errors never reuse the prior summary. Invalid or incomplete ranges require correction.

Read-only authenticated API: GET /receipts/summary?from=YYYY-MM-DD&to=YYYY-MM-DD returns the existing Savings shape. Both dates are required, start must not exceed end, and undated/deleted receipts are excluded. Range calculation runs in Java through SavingsReport.range and the existing savings projection; the browser performs no money aggregation. No database schema change.

Existing implementation files changed: src/main/java/coupons/SavingsReport.java; src/main/java/coupons/repository/PostgresPlatformRepository.java; src/main/java/coupons/api/CouponApiService.java; frontend/src/api/client.ts; frontend/src/pages/WorkspacePages.tsx. Existing regression files: src/test/java/coupons/SavingsReportTest.java; frontend/src/features/receipts/ReceiptCreate.test.tsx. No new files. Layout uses the existing centered three-column receipt-period-totals grid (six metrics in two rows), collapsing to one column on narrow screens; existing typography/control tokens remain unchanged.


Clipped coupons and catalog cleanup: Deals & promotions and Coupons now expose permanent Delete actions with write access and revision checks. DELETE /deals/{id} and DELETE /clipped-coupons/{id} remove the catalog record and its own revision history, detach current plan associations while retaining product prices, and remove deal source artifacts only when not shared. Normal plan revision history remains; no deletion backup is created. Redeemed coupons cannot be deleted. Receipt entry accepts clippedCouponIds and offers saved coupon selection per product and in shared/receipt-wide discounts. Selection creates one editable discount with the coupon UUID, description, type and buy requirement; use the shared selector for a coupon covering several products. Percentage/free-item deductions use the actual recorded receipt amount. Selecting does not consume a coupon; receipt creation and all selected coupon redemptions commit atomically in PostgreSQL. Expired, already-used, or ineligible coupons cause rollback. The Coupons page then shows Applied and the receipt connection. Do not manually repeat a selected deduction. Existing receipt corrections retain their separate workflow; editing a discount does not automatically release a previously redeemed coupon. No new schema migration or files are introduced.

Changed application and test files: frontend/src/App.tsx, frontend/src/pages/WorkspacePages.tsx, frontend/src/features/receipts/ReceiptCreate.tsx, frontend/src/types/platform.ts, frontend/src/App.css, frontend/src/features/receipts/ReceiptCreate.test.tsx, src/main/java/coupons/api/CouponApiService.java, src/main/java/coupons/api/ReceiptInput.java, src/main/java/coupons/repository/PostgresPlatformRepository.java, src/main/java/coupons/repository/PostgresTransactionRepository.java, src/main/java/coupons/repository/PostgresClippedCouponRepository.java, src/test/java/coupons/repository/PostgresTransactionRepositoryTest.java.


## Revision 1.15 — documentation consolidation audit (2026-10-24)

Merged all unique blocks from the historical requirements inventory into the SRS and both earlier Word design/combined plans into the SDD, then removed those separate Word copies. Source text/table preservation was checked before removal. The SRS and SDD are the only two application Word documents; both covers, metadata and revision tables identify 1.15.

Merged docs/design/API.md and docs/archive/api/API 2.md into API-REFERENCE.md, and the archived duplicate deployment guide into docs/operations/DEPLOYMENT-BACKUP.md. Older contracts are retained as clearly historical source sections, with current token identity, persistent replay journal and V1–V9 migrations taking precedence. Combined the October 20/21 FILE_INDEX.md inventories into one index and their ALL_DECLARATIONS.tsv rows into one TSV with an as_of column, preserving every row. This is not a fresh source inventory.

Updated the canonical-document map and merged-target Markdown links. Unique subject guides remain separate. Receipt evidence, migration source copies, distinct receipt.properties records and generated third-party legal notices were deliberately unchanged. No application source or financial records changed, no new files were created, and nothing was committed/pushed.
