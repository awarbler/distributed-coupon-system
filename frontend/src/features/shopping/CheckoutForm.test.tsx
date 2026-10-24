
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClient, ApiError } from '../../api/client'
import type { PlanCheckout } from '../../types/platform'
import { CheckoutForm } from './CheckoutForm'

// A checkout response used only by these tests.
// No real database or API requests are made.
function checkoutFixture(): PlanCheckout {
    return {
        planId: 'plan-test-123',
        revision: '0',
        promoCode: null,
        promoDiscount: '0.00',
        taxRate: null,
        taxableBase: null,
        actualTax: null,
        walgreensCashUsed: '0.00',
        registerRewardsCoupon: '0.00',
        giftCardUsed: '0.00',
        cashPayment: '0.00',
        cardPayment: '0.00',
        otherPayment: '0.00',
        notes: null,
        summary: {
            tax: null,
            amountDue: '25.98',
            remainingPayment: '25.98',
            expectedRewards: '2.00',
            issues: ['Sales tax is not configured.'],
        },
        estimateComplete: false,
    }
}

function setup() {
    const api = new ApiClient('test-token')

    const load = vi.spyOn(api, 'checkout')
        .mockResolvedValue(checkoutFixture())

    const save = vi.spyOn(api, 'saveCheckout')
        .mockResolvedValue({
            ...checkoutFixture(),
            revision: '1',
        })

    const onClose = vi.fn()
    const onSaved = vi.fn()

    render(
        <CheckoutForm
            api={api}
            planId="plan-test-123"
            planName="Pick Up Order"
            writable
            onClose={onClose}
            onSaved={onSaved}
        />
    )

    return {
        user: userEvent.setup(),
        load,
        save,
        onClose,
        onSaved,
    }
}

describe('shopping plan checkout', () => {
    it('loads checkout fields and existing totals', async () => {
        const { load } = setup()

        expect(
            await screen.findByLabelText('Sales tax rate (%)')
        ).toBeInTheDocument()

        expect(load).toHaveBeenCalledWith('plan-test-123')

        expect(
            screen.getByText('Checkout revision 0')
        ).toBeInTheDocument()

        expect(screen.getAllByText('25.98')).toHaveLength(2)
    })

    it('rejects invalid sales tax before saving', async () => {
        const { user, save } = setup()

        const tax = await screen.findByLabelText(
            'Sales tax rate (%)'
        )

        await user.type(tax, '101')

        await user.click(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        )

        expect(
            screen.getByRole('alert')
        ).toHaveTextContent('cannot exceed 100%')

        expect(save).not.toHaveBeenCalled()
    })

    it('rejects negative payment amounts', async () => {
        const { user, save } = setup()

        const payment = await screen.findByLabelText(
            'Cash payment ($)'
        )

        await user.type(payment, '-5')

        await user.click(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        )

        expect(
            screen.getByRole('alert')
        ).toHaveTextContent('nonnegative amount')

        expect(save).not.toHaveBeenCalled()
    })

    it('saves exact decimal strings with checkout revision', async () => {
        const { user, save, onSaved, onClose } = setup()

        await user.type(
            await screen.findByLabelText('Sales tax rate (%)'),
            '8.25'
        )

        const giftCard = screen.getByLabelText('Gift card used ($)')

        await user.clear(giftCard)
        await user.type(giftCard, '10.50')

        await user.click(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        )

        await waitFor(() => {
            expect(save).toHaveBeenCalledTimes(1)
        })

        const [planId, values, revision, key] =
            save.mock.calls[0]

        expect(planId).toBe('plan-test-123')
        expect(values.taxRate).toBe('8.25')
        expect(values.giftCardUsed).toBe('10.50')
        expect(revision).toBe('0')
        expect(key).toBeTruthy()

        await waitFor(() => {
            expect(onSaved).toHaveBeenCalledOnce()
            expect(onClose).toHaveBeenCalledOnce()
        })
    })

    it('preserves the request key after an uncertain network failure', async () => {
        const { user, save } = setup()

        save
            .mockRejectedValueOnce(
                new ApiError(0, 'Network outcome unknown.')
            )
            .mockResolvedValueOnce({
                ...checkoutFixture(),
                revision: '1',
            })

        await screen.findByLabelText('Sales tax rate (%)')

        await user.click(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        )

        expect(
            await screen.findByRole('alert')
        ).toHaveTextContent('Network outcome unknown.')

        await user.click(
            screen.getByRole('button', {
                name: 'Retry save',
            })
        )

        await waitFor(() => {
            expect(save).toHaveBeenCalledTimes(2)
        })

        expect(save.mock.calls[1][2]).toBe(
            save.mock.calls[0][2]
        )

        expect(save.mock.calls[1][3]).toBe(
            save.mock.calls[0][3]
        )

        expect(save.mock.calls[1][1]).toEqual(
            save.mock.calls[0][1]
        )
    })

    it('unlocks fields after a definite server rejection', async () => {
        const { user, save } = setup()

        save.mockRejectedValueOnce(
            new ApiError(409, 'Checkout revision changed.')
        )

        const tax = await screen.findByLabelText(
            'Sales tax rate (%)'
        )

        await user.type(tax, '8.25')

        await user.click(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        )

        expect(
            await screen.findByRole('alert')
        ).toHaveTextContent('Checkout revision changed.')

        expect(tax).toBeEnabled()

        expect(
            screen.getByRole('button', {
                name: 'Save checkout',
            })
        ).toBeEnabled()
    })
})

it('shows an incomplete potential reward subtotal without changing the saved amount due', async () => {
    const api = new ApiClient('test')
    const checkout = checkoutFixture()
    checkout.summary = { ...checkout.summary, expectedRewards: null, knownPotentialRewards: '5', rewardsComplete: false, reviewRequired: true }
    vi.spyOn(api, 'checkout').mockResolvedValue(checkout)
    render(<CheckoutForm api={api} planId="plan" planName="Trip" writable onClose={vi.fn()} onSaved={vi.fn()} />)
    expect(await screen.findByText('Known potential promotional rewards')).toBeInTheDocument()
    expect(screen.getByText('$5.00 — total incomplete')).toBeInTheDocument()
    expect(screen.getByText(/These are potential benefits, not earned rewards/)).toBeInTheDocument()
    expect(screen.getAllByText('25.98').length).toBeGreaterThan(0)
})
