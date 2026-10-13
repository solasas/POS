import { act, renderHook } from '@testing-library/react'
import { makePosProduct } from '../test/render'
import { useCart } from './useCart'

describe('useCart', () => {
  it('adds a product with quantity 1', () => {
    const { result } = renderHook(() => useCart())

    act(() => {
      result.current.add(makePosProduct())
    })

    expect(result.current.lines).toHaveLength(1)
    expect(result.current.lines[0].quantity).toBe(1)
  })

  it('increases the quantity of an existing line on repeated scans', () => {
    const { result } = renderHook(() => useCart())
    const milk = makePosProduct()

    act(() => {
      result.current.add(milk)
    })
    act(() => {
      result.current.add(milk)
    })
    act(() => {
      result.current.add(milk)
    })

    expect(result.current.lines).toHaveLength(1)
    expect(result.current.lines[0].quantity).toBe(3)
  })

  it('refuses to exceed the stock on hand', () => {
    const { result } = renderHook(() => useCart())
    const scarce = makePosProduct({ stockQuantity: 2 })
    let outcome: ReturnType<typeof result.current.add> | undefined

    act(() => {
      result.current.add(scarce, 2)
    })
    act(() => {
      outcome = result.current.add(scarce)
    })

    expect(outcome).toEqual({ ok: false, reason: expect.stringContaining('Only 2') })
    expect(result.current.lines[0].quantity).toBe(2)
  })

  it('rejects inactive and out-of-stock products', () => {
    const { result } = renderHook(() => useCart())
    let inactive: ReturnType<typeof result.current.add> | undefined
    let soldOut: ReturnType<typeof result.current.add> | undefined

    act(() => {
      inactive = result.current.add(makePosProduct({ active: false, available: false }))
      soldOut = result.current.add(makePosProduct({ id: 'x', stockQuantity: 0, available: false }))
    })

    expect(inactive).toEqual({ ok: false, reason: expect.stringContaining('inactive') })
    expect(soldOut).toEqual({ ok: false, reason: expect.stringContaining('out of stock') })
    expect(result.current.lines).toHaveLength(0)
  })

  it('only accepts whole numbers for countable units but decimals for weighed ones', () => {
    const { result } = renderHook(() => useCart())
    const piece = makePosProduct()
    const kg = makePosProduct({ id: 'kg-1', unit: 'KG', wholeQuantitiesOnly: false, stockQuantity: 20 })
    let fractionalPiece: ReturnType<typeof result.current.setQuantity> | undefined
    let fractionalKg: ReturnType<typeof result.current.setQuantity> | undefined

    act(() => {
      fractionalPiece = result.current.setQuantity(piece, 1.5)
      fractionalKg = result.current.setQuantity(kg, 1.255)
    })

    expect(fractionalPiece).toEqual({ ok: false, reason: expect.stringContaining('whole units') })
    expect(fractionalKg).toEqual({ ok: true, quantity: 1.255 })
    expect(result.current.lines).toHaveLength(1)
    expect(result.current.lines[0].product.id).toBe('kg-1')
  })

  it('rejects zero, negative and non-numeric quantities', () => {
    const { result } = renderHook(() => useCart())
    const milk = makePosProduct()
    const outcomes: unknown[] = []

    act(() => {
      outcomes.push(result.current.setQuantity(milk, 0))
      outcomes.push(result.current.setQuantity(milk, -2))
      outcomes.push(result.current.setQuantity(milk, Number.NaN))
    })

    expect(outcomes).toEqual([
      { ok: false, reason: expect.any(String) },
      { ok: false, reason: expect.any(String) },
      { ok: false, reason: expect.any(String) },
    ])
    expect(result.current.lines).toHaveLength(0)
  })

  it('removes lines and clears the cart', () => {
    const { result } = renderHook(() => useCart())

    act(() => {
      result.current.add(makePosProduct())
      result.current.add(makePosProduct({ id: 'b', sku: 'B' }))
    })
    act(() => result.current.remove('pos-1'))
    expect(result.current.lines.map((l) => l.product.id)).toEqual(['b'])

    act(() => result.current.clear())
    expect(result.current.lines).toHaveLength(0)
  })
})
