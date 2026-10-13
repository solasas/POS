import { formatMoney, formatQuantity } from './format'

describe('formatMoney', () => {
  it('uses the rupee symbol and two decimals', () => {
    expect(formatMoney(1.09)).toBe('₹1.09')
    expect(formatMoney(0)).toBe('₹0.00')
    expect(formatMoney(1234.5)).toBe('₹1,234.50')
  })

  it('groups digits the Indian way (lakh and crore)', () => {
    expect(formatMoney(123456)).toBe('₹1,23,456.00')
    expect(formatMoney(12345678.9)).toBe('₹1,23,45,678.90')
  })

  it('never shows a dollar sign', () => {
    expect(formatMoney(99.99)).not.toContain('$')
  })
})

describe('formatQuantity', () => {
  it('keeps up to three decimals and Indian grouping', () => {
    expect(formatQuantity(1.255)).toBe('1.255')
    expect(formatQuantity(100000)).toBe('1,00,000')
  })
})
