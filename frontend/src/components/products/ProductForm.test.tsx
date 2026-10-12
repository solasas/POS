import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { ApiError } from '../../services/errors'
import { makeCategory, makeProduct } from '../../test/render'
import { ProductForm } from './ProductForm'

const categories = [makeCategory(), makeCategory({ id: 'cat-2', name: 'Bakery' })]

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('SKU'), 'MILK-1')
  await user.type(screen.getByLabelText('Name'), 'Whole Milk')
  await user.selectOptions(screen.getByLabelText('Category'), 'cat-1')
  await user.type(screen.getByLabelText('Selling price'), '1.99')
}

describe('ProductForm', () => {
  it('shows field-level errors and does not submit an empty form', async () => {
    const onSubmit = vi.fn()
    render(<ProductForm categories={categories} onSubmit={onSubmit} onCancel={() => {}} />)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Create product' }))

    expect(await screen.findByText('SKU is required')).toBeInTheDocument()
    expect(screen.getByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Select a category')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits parsed numbers and a null barcode', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<ProductForm categories={categories} onSubmit={onSubmit} onCancel={() => {}} />)

    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create product' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1))
    expect(onSubmit).toHaveBeenCalledWith({
      sku: 'MILK-1',
      barcode: null,
      name: 'Whole Milk',
      description: null,
      categoryId: 'cat-1',
      unit: 'PIECE',
      price: 1.99,
      costPrice: 0,
      lowStockThreshold: 0,
    })
  })

  it('maps a server-side duplicate SKU error onto the SKU field', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError("SKU 'MILK-1' is already in use", 409, { sku: "SKU 'MILK-1' is already in use" }))
    const user = userEvent.setup()
    render(<ProductForm categories={categories} onSubmit={onSubmit} onCancel={() => {}} />)

    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create product' }))

    expect(await screen.findByText("SKU 'MILK-1' is already in use")).toBeInTheDocument()
    expect(screen.getByLabelText('SKU')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a general error when the server error is not tied to a field', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError('Cannot reach the server.', null))
    const user = userEvent.setup()
    render(<ProductForm categories={categories} onSubmit={onSubmit} onCancel={() => {}} />)

    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create product' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server.')
  })

  it('blocks duplicate submissions while a request is in flight', async () => {
    let resolve: () => void = () => {}
    const onSubmit = vi.fn(() => new Promise<void>((r) => (resolve = r)))
    const user = userEvent.setup()
    render(<ProductForm categories={categories} onSubmit={onSubmit} onCancel={() => {}} />)

    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Create product' }))

    const saving = await screen.findByRole('button', { name: 'Saving…' })
    expect(saving).toBeDisabled()
    await user.click(saving)
    expect(onSubmit).toHaveBeenCalledTimes(1)

    resolve()
  })

  it('prefills an existing product and keeps its inactive category selectable', () => {
    const inactive = makeCategory({ id: 'cat-3', name: 'Legacy', active: false })
    const product = makeProduct({ categoryId: 'cat-3', categoryName: 'Legacy' })
    render(
      <ProductForm categories={[...categories, inactive]} product={product} onSubmit={vi.fn()} onCancel={() => {}} />,
    )

    expect(screen.getByLabelText('SKU')).toHaveValue('MILK-1')
    expect(screen.getByLabelText('Selling price')).toHaveValue('1.09')
    expect(screen.getByRole('option', { name: 'Legacy (inactive)' })).toBeInTheDocument()
    expect(screen.getByLabelText('Category')).toHaveValue('cat-3')
  })
})
