import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

export function Pagination({ page, totalPages, onChange, className }: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  className?: string;
}) {
  if (totalPages <= 1) {
    return null;
  }
  const pages = pageWindow(page, totalPages);
  return (
    <nav aria-label="Pagination" className={className}>
      <ul className="flex items-center justify-center gap-1">
        <li>
          <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Previous page" className="h-9 w-9 p-0 sm:h-8 sm:w-8">
            <ChevronLeft className="h-4 w-4" />
          </Button>
        </li>
        {pages.map((value, index) => (
          <li key={`${value}-${index}`}>
            {value === -1 ? (
              <span className="px-2 text-sm text-slate-500">...</span>
            ) : (
              <Button variant={value === page ? 'primary' : 'ghost'} size="sm" onClick={() => onChange(value)}
                aria-current={value === page ? 'page' : undefined} className="h-9 min-w-9 sm:h-8 sm:min-w-8">
                {value + 1}
              </Button>
            )}
          </li>
        ))}
        <li>
          <Button variant="ghost" size="sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label="Next page" className="h-9 w-9 p-0 sm:h-8 sm:w-8">
            <ChevronRight className="h-4 w-4" />
          </Button>
        </li>
      </ul>
    </nav>
  );
}

function pageWindow(page: number, total: number): number[] {
  const pages = new Set<number>([0, total - 1, page, page - 1, page + 1]);
  const sorted = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
  const result: number[] = [];
  sorted.forEach((value, index) => {
    if (index > 0 && value - sorted[index - 1] > 1) {
      result.push(-1);
    }
    result.push(value);
  });
  return result;
}
