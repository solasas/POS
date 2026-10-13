import { render, screen } from '@testing-library/react'
import type { SalesSummary } from '../../types/api'
import { TodayCard } from './TodayCard'

const base: SalesSummary = {
  saleCount: 4,
  grandTotal: 1250,
  byPaymentMethod: [{ method: 'CASH', saleCount: 4, grandTotal: 1250 }],
  costTotal: null,
  profit: null,
}

describe('TodayCard', () => {
  it('shows profit to managers', () => {
    render(<TodayCard summary={{ ...base, costTotal: 800, profit: 380.5 }} loading={false} />)

    expect(screen.getByText('Profit')).toBeInTheDocument()
    expect(screen.getByText('₹380.50')).toBeInTheDocument()
  })

  it('hides profit entirely for cashiers (no cost data is returned)', () => {
    render(<TodayCard summary={base} loading={false} />)

    expect(screen.queryByText('Profit')).not.toBeInTheDocument()
    expect(screen.getByText('Average bill')).toBeInTheDocument()
  })

  it('shows a loss in red', () => {
    render(<TodayCard summary={{ ...base, costTotal: 1500, profit: -250 }} loading={false} />)

    expect(screen.getByText('-₹250.00')).toHaveClass('text-red-600')
  })
})
