import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClient, ApiError } from '../../api/client'
import type { PlanCheckout } from '../../types/platform'
import { Modal } from '../../components/ui'


interface Props {
  api: ApiClient
  planId: string
  planName: string
  writable: boolean
  onClose: () => void
  onSaved: () => void
}

const fields = [
  ['promoCode', 'Promotion code'],
  ['promoDiscount', 'Additional promotion discount ($)'],
  ['taxRate', 'Sales tax rate (%)'],
  ['taxableBase', 'Taxable amount ($)'],
  ['actualTax', 'Actual sales tax from receipt ($)'],
  ['walgreensCashUsed', 'Walgreens Cash used ($)'],
  ['registerRewardsCoupon', 'Register Rewards coupon ($)'],
  ['giftCardUsed', 'Gift card used ($)'],
  ['cashPayment', 'Cash payment ($)'],
  ['cardPayment', 'Card payment ($)'],
  ['otherPayment', 'Other payment ($)'],
  ['notes', 'Checkout notes'],
] as const

type Field = (typeof fields)[number][0]
type Values = Record<Field, string>
const moneyFields: Field[] = [
  'promoDiscount',
  'taxableBase',
  'actualTax',
  'walgreensCashUsed',
  'registerRewardsCoupon',
  'giftCardUsed',
  'cashPayment',
  'cardPayment',
  'otherPayment',
]

function validateCheckout(values: Values): string | null {
  for (const field of moneyFields) {
    const value = values[field].trim()

    if (value !== '' && !/^\d+(\.\d{1,2})?$/.test(value)) {
      const fieldLabel = fields.find(([name]) => name === field)?.[1]
      return `${fieldLabel} must be a nonnegative amount with no more than two decimal places.`
    }
  }

  const rate = values.taxRate.trim()

  if (rate !== '') {
    if (!/^\d+(\.\d{1,4})?$/.test(rate)) {
      return 'Sales tax rate must be a nonnegative percentage with no more than four decimal places.'
    }

    if (Number(rate) > 100) {
      return 'Sales tax rate cannot exceed 100%.'
    }
  }

  if (values.promoCode.length > 200) {
    return 'Promotion code cannot exceed 200 characters.'
  }

  if (values.notes.length > 2000) {
    return 'Checkout notes cannot exceed 2,000 characters.'
  }

  return null
}
function initialValues(checkout: PlanCheckout): Values {
  return Object.fromEntries(
    fields.map(([field]) => [field, checkout[field] ?? ''])
  ) as Values
}

export function CheckoutForm({
  api,
  planId,
  planName,
  writable,
  onClose,
  onSaved,
}: Props) {
  const [checkout, setCheckout] = useState<PlanCheckout | null>(null)
  const [values, setValues] = useState<Values | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [pendingKey, setPendingKey] = useState<string | null>(null)
  const [pendingValues, setPendingValues] = useState<Values | null>(null)

  useEffect(() => {
    let active = true

    api.checkout(planId)
      .then(result => {
        if (!active) return
        setCheckout(result)
        setValues(initialValues(result))
      })
      .catch(err => {
        if (active) setError((err as Error).message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [api, planId])

  async function submit(event: FormEvent) {
    event.preventDefault()

    if (!checkout || !values || !writable || saving) {
      return
    }

    const validationError = validateCheckout(values)

    if (validationError) {
      setError(validationError)
      return
    }

    setError('')
    setSaving(true)

    const key = pendingKey ?? crypto.randomUUID()
    const body = pendingValues ?? values

    setPendingKey(key)
    setPendingValues(body)

    try {
      await api.saveCheckout(planId, body, checkout.revision, key)
      onSaved()
      onClose()
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Checkout could not be saved.'
      )

      // A network failure has an uncertain outcome.
      // Preserve the request and idempotency key for a safe retry.
      //
      // A definite server rejection did not complete this request.
      // Unlock the form so the user can correct the values or reload.
      if (!(err instanceof ApiError) || err.status !== 0) {
        setPendingKey(null)
        setPendingValues(null)
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={`Checkout — ${planName}`} onClose={onClose}>
      {loading && <p>Loading checkout…</p>}
      {error && <div className="error" role="alert">{error}</div>}

      {!loading && checkout && values && (
        <form onSubmit={submit}>
          <p className="muted">
            Checkout revision {checkout.revision}
          </p>

          <div className="form-grid">
            {fields.map(([field, label]) => (
              <label key={field}>
                {label}
                {field === 'notes' ? (
                  <textarea
                    value={values[field]}
                    maxLength={2000}
                    disabled={!writable || saving || !!pendingKey}
                    onChange={event =>
                      setValues({
                        ...values,
                        [field]: event.target.value,
                      })
                    }
                  />
                ) : (
                  <input
                    value={values[field]}
                    inputMode={
                      field === 'promoCode' ? 'text' : 'decimal'
                    }
                    disabled={!writable || saving || !!pendingKey}
                    onChange={event =>
                      setValues({
                        ...values,
                        [field]: event.target.value,
                      })
                    }
                    placeholder={
                      field === 'taxRate' ? '8.25' : '0.00'
                    }
                  />
                )}
              </label>

            ))}
          </div>

          <h3>Estimated checkout summary</h3>

          <div className="detail-row">
            <span>Sales tax</span>
            <strong>{checkout.summary.tax ?? 'Not configured'}</strong>
          </div>

          <div className="detail-row">
            <span>Amount due</span>
            <strong>{checkout.summary.amountDue ?? 'Incomplete'}</strong>
          </div>

          <div className="detail-row">
            <span>Remaining payment</span>
            <strong>
              {checkout.summary.remainingPayment ?? 'Incomplete'}
            </strong>
          </div>

          {checkout.summary.issues.map((issue, index) => (
            <p className="panel-note" key={index}>{issue}</p>
          ))}



          <p className="muted">
            These totals reflect the last saved checkout.
            Save changes to recalculate them.
          </p>


          <div className="form-actions">
            <button type="button" className="secondary" onClick={onClose}>
              Cancel
            </button>
            <button
              type="submit"
              className="primary"
              disabled={!writable || saving}
            >
              {saving ? 'Saving…' : pendingKey ? 'Retry save' : 'Save checkout'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}