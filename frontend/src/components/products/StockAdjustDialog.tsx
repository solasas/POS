import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { Alert } from '../ui/feedback'
import { TextField } from '../ui/fields'
import { inventoryService } from '../../services/inventoryService'
import { errorMessage } from '../../services/errors'
import type { Product } from '../../types/api'
import { formatQuantity, UNIT_SHORT } from '../../utils/format'

interface StockAdjustDialogProps {
  product: Product
  onClose: () => void
  onAdjusted: (newStock: number) => void
}

/** Receives or corrects stock. Every change is appended to the inventory ledger with a reason. */
export function StockAdjustDialog({ product, onClose, onAdjusted }: StockAdjustDialogProps) {
  const [change, setChange] = useState('')
  const [reason, setReason] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      inventoryService.adjust({ productId: product.id, quantityChange: Number(change), reason: reason.trim() }),
    onSuccess: (entry) => onAdjusted(entry.stockAfter),
  })

  const submit = () => {
    const value = Number(change)
    if (change.trim() === '' || !Number.isFinite(value) || value === 0) {
      setFieldError('Enter a non-zero number. Use a negative number to remove stock.')
      return
    }
    if (reason.trim() === '') {
      setFieldError('A reason is required.')
      return
    }
    setFieldError(null)
    mutation.mutate()
  }

  return (
    <Modal title={`Adjust stock: ${product.name}`} onClose={onClose} locked={mutation.isPending}>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          submit()
        }}
        className="space-y-4"
        noValidate
      >
        <p className="text-sm text-slate-600">
          On hand: <strong>{formatQuantity(product.stockQuantity)} {UNIT_SHORT[product.unit]}</strong>
        </p>
        <TextField
          label="Quantity change"
          hint="Positive adds stock (delivery); negative removes it (damage, shrinkage)."
          inputMode="decimal"
          value={change}
          onChange={(event) => setChange(event.target.value)}
        />
        <TextField label="Reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={255} />
        {fieldError && <Alert>{fieldError}</Alert>}
        {mutation.isError && <Alert>{errorMessage(mutation.error)}</Alert>}
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Apply adjustment'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
