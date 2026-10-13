import { Button } from './Button'
import { Icon } from './Icon'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onPageChange }: PaginationProps) {
  const lastPage = Math.max(totalPages - 1, 0)
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between gap-3 text-sm text-slate-600">
      <span className="num">
        {totalElements} {totalElements === 1 ? 'result' : 'results'}
        <span className="mx-2 text-slate-300">·</span>
        {`Page ${page + 1} of ${Math.max(totalPages, 1)}`}
      </span>
      <span className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          <Icon name="arrowLeft" size={15} />
          Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={page >= lastPage} onClick={() => onPageChange(page + 1)}>
          Next
          <Icon name="arrowRight" size={15} />
        </Button>
      </span>
    </nav>
  )
}
