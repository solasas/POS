import { Button } from './Button'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, onPageChange }: PaginationProps) {
  const lastPage = Math.max(totalPages - 1, 0)
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between text-sm text-slate-600">
      <span>
        {totalElements} {totalElements === 1 ? 'result' : 'results'} · Page {page + 1} of {Math.max(totalPages, 1)}
      </span>
      <span className="flex gap-2">
        <Button variant="secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page >= lastPage} onClick={() => onPageChange(page + 1)}>
          Next
        </Button>
      </span>
    </nav>
  )
}
