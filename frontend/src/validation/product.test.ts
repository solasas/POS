import { productFormSchema, toProductInput } from './product'

const valid = {
  sku: 'MILK-1',
  barcode: '',
  name: 'Whole Milk',
  description: '',
  categoryId: 'cat-1',
  unit: 'LITRE' as const,
  price: '1.99',
  costPrice: '1.10',
  lowStockThreshold: '5',
}

describe('productFormSchema', () => {
  it('accepts a valid product and converts it to the API shape', () => {
    const parsed = productFormSchema.parse(valid)
    expect(toProductInput(parsed)).toEqual({
      sku: 'MILK-1',
      barcode: null,
      name: 'Whole Milk',
      description: null,
      categoryId: 'cat-1',
      unit: 'LITRE',
      price: 1.99,
      costPrice: 1.1,
      lowStockThreshold: 5,
    })
  })

  it('rejects a zero or malformed price', () => {
    expect(productFormSchema.safeParse({ ...valid, price: '0' }).success).toBe(false)
    expect(productFormSchema.safeParse({ ...valid, price: 'abc' }).success).toBe(false)
    expect(productFormSchema.safeParse({ ...valid, price: '1.999' }).success).toBe(false)
    expect(productFormSchema.safeParse({ ...valid, price: '-1' }).success).toBe(false)
  })

  it('allows a zero cost price but not a negative one', () => {
    expect(productFormSchema.safeParse({ ...valid, costPrice: '0' }).success).toBe(true)
    expect(productFormSchema.safeParse({ ...valid, costPrice: '-0.01' }).success).toBe(false)
  })

  it('requires SKU, name and category and restricts SKU characters', () => {
    const result = productFormSchema.safeParse({ ...valid, sku: 'bad sku!', name: ' ', categoryId: '' })
    expect(result.success).toBe(false)
    if (!result.success) {
      const fields = result.error.issues.map((issue) => issue.path[0])
      expect(fields).toEqual(expect.arrayContaining(['sku', 'name', 'categoryId']))
    }
  })

  it('allows an empty barcode but not one with spaces', () => {
    expect(productFormSchema.safeParse({ ...valid, barcode: '' }).success).toBe(true)
    expect(productFormSchema.safeParse({ ...valid, barcode: '12 34' }).success).toBe(false)
  })
})
