
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { EditDialog } from './EditDialog'
import { ApiClient } from '../../api/client'
import { workspace, planningRow } from '../../api/fixtures.test-support'

describe('inline plan entry', () => {
  it('creates and links a deal without losing entered product or prices', async () => {
    const data = workspace()
    const row = {
      plan: {
        planId: crypto.randomUUID(),
        name: 'Trip',
        retailer: 'Store',
        shoppingDate: '2026-10-19',
        plannedPurchaseIds: [],
        revision: '1',
      },
      items: [],
      estimatedMerchandise: null,
      estimateComplete: false,
    }

    const api = new ApiClient('owner')
    const mutate = vi.spyOn(api, 'mutate').mockImplementation(
      async (path, _method, body) =>
        path === '/deals'
          ? { ...(body as object), revision: '1' }
          : {}
    )

    render(
      <EditDialog
        edit={{ kind: 'purchase', row }}
        data={data}
        api={api}
        onClose={vi.fn()}
        onSaved={vi.fn()}
        online
      />
    )

    const user = userEvent.setup()

    await user.type(
      screen.getByRole('textbox', { name: /Product description/i }),
      'Soap'
    )

    await user.type(
      screen.getByRole('textbox', { name: /Regular price/i }),
      '3.0000'
    )

    await user.click(
      screen.getByRole('button', { name: 'Add a deal' })
    )

    await user.type(screen.getByLabelText('Store'), 'Store')
    await user.type(screen.getByLabelText('Promotion name'), 'Soap sale')
    await user.type(
      screen.getByLabelText('Qualifying product description'),
      'Soap'
    )
    await user.type(
      screen.getByLabelText('Recorded offer terms'),
      'Soap sale'
    )
    await user.type(
      screen.getByLabelText('Sale unit price'),
      '2.50'
    )

    await user.click(
      screen.getByRole('button', { name: 'Save and link deal' })
    )

    expect(
      screen.getByRole('textbox', { name: /Product description/i })
    ).toHaveValue('Soap')

    expect(
      screen.getByRole('textbox', { name: /Regular price/i })
    ).toHaveValue('3.0000')

    expect(
      screen.getByRole('textbox', { name: /Sale price/i })
    ).toHaveValue('2.50')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    const body = mutate.mock.calls[1][2] as {
      regularUnitPrice: string
      saleUnitPrice: string
      advertisedDealId: string
      priceSource: string
    }

    expect(body.regularUnitPrice).toBe('3.0000')
    expect(body.saleUnitPrice).toBe('2.50')
    expect(body.advertisedDealId).toBeTruthy()
    expect(body.priceSource).toBe('DEAL')
  })

  it('uses the shared paper coupon form without requiring an account', async () => {
    const api = new ApiClient('owner')
    const mutate = vi.spyOn(api, 'mutate').mockResolvedValue([])
    const user = userEvent.setup()

    render(
      <EditDialog
        edit={{ kind: 'coupon' }}
        api={api}
        data={workspace()}
        online
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    )

    await user.type(
      screen.getByLabelText('Coupon description'),
      'Soap'
    )

    await user.type(
      screen.getByLabelText('Issuing manufacturer, store or provider'),
      'Maker'
    )

    await user.type(
      screen.getByLabelText('Dollar amount'),
      '2.00'
    )

    await user.clear(
      screen.getByLabelText('Number of available copies')
    )

    await user.type(
      screen.getByLabelText('Number of available copies'),
      '3'
    )

    await user.click(
      screen.getByRole('button', { name: 'Save coupon' })
    )

    const body = mutate.mock.calls[0][2] as {
      copies: number
      accountRestriction: null
      source: string
    }

    expect(body.copies).toBe(3)
    expect(body.accountRestriction).toBeNull()
    expect(body.source).toBe('PAPER_MANUFACTURER')
  })
})

it('preselects the originating deal for a new planned item', () => {
  const data = workspace(); const row = planningRow()
  data.deals = [{ dealId: 'deal-progress', title: 'Dove reward', interpretedTerms: 'Buy two', retailer: 'Walgreens', validFrom: null, validThrough: null, sourceArtifactIds: [], revision: '1' }]
  render(<EditDialog edit={{ kind: 'purchase', row, initialDealId: 'deal-progress' }} data={data} api={new ApiClient('test')} onClose={vi.fn()} onSaved={vi.fn()} online />)
  expect(screen.getByRole('combobox', { name: /Advertised deal/ })).toHaveValue('deal-progress')
})
