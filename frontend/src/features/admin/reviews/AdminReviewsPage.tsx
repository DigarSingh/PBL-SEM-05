import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { ErrorState } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { Pagination } from '@/components/ui/Pagination';
import { RatingStars } from '@/components/ui/Rating';
import { errorMessage } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import type { AdminReview } from '@/lib/types';
import { adminApi } from '../api';
import { AdminPage } from '../components/AdminPage';
import { SearchInput } from '../components/SearchInput';
import { TableEmpty, TableSkeleton } from '../components/TableStates';
import { pageFrom, useParamsState } from '../useParamsState';

const DEFAULTS = { q: '', status: '', rating: '', page: '' };

export default function AdminReviewsPage() {
  const [filters, setFilters] = useParamsState(DEFAULTS);
  const queryClient = useQueryClient();
  const [moderating, setModerating] = useState<AdminReview | null>(null);
  const query = {
    q: filters.q || undefined,
    status: (filters.status || undefined) as 'PUBLISHED' | 'HIDDEN' | undefined,
    rating: filters.rating ? Number(filters.rating) : undefined,
    page: pageFrom(filters.page),
    size: 20,
  };
  const reviews = useQuery({ queryKey: ['admin', 'reviews', query], queryFn: () => adminApi.reviews(query), placeholderData: keepPreviousData });
  const moderate = useMutation({
    mutationFn: ({ review, note }: { review: AdminReview; note: string }) =>
      adminApi.moderateReview(review.id, review.status === 'PUBLISHED' ? 'HIDDEN' : 'PUBLISHED', note || null),
    onSuccess: async (updated) => {
      setModerating(null);
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] }), queryClient.invalidateQueries({ queryKey: ['reviews'] })]);
      toast.success(updated.status === 'HIDDEN' ? 'Review hidden from the store' : 'Review published');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <AdminPage title="Reviews" description="Reviews publish immediately. Hide ones that break the guidelines; ratings update automatically.">
      <Card className="mb-4 flex flex-wrap items-center gap-3 p-4">
        <SearchInput key={filters.q} value={filters.q} onSearch={(q) => setFilters({ q })} placeholder="Product, reviewer or text" className="w-full sm:w-72" />
        <Select aria-label="Status" value={filters.status} onChange={(event) => setFilters({ status: event.target.value })} className="h-9 w-auto">
          <option value="">All reviews</option>
          <option value="PUBLISHED">Published</option>
          <option value="HIDDEN">Hidden</option>
        </Select>
        <Select aria-label="Rating" value={filters.rating} onChange={(event) => setFilters({ rating: event.target.value })} className="h-9 w-auto">
          <option value="">Any rating</option>
          {[5, 4, 3, 2, 1].map((stars) => <option key={stars} value={stars}>{stars} star{stars === 1 ? '' : 's'}</option>)}
        </Select>
      </Card>
      {reviews.isPending ? <Card><TableSkeleton /></Card> : reviews.isError ? (
        <ErrorState error={reviews.error} onRetry={() => void reviews.refetch()} />
      ) : reviews.data.items.length === 0 ? <Card><TableEmpty message="No reviews match." /></Card> : (
        <ul className="space-y-3">
          {reviews.data.items.map((review) => (
            <li key={review.id}>
              <Card className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-slate-500">{review.productName}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-2">
                      <RatingStars value={review.rating} />
                      <p className="font-semibold text-slate-900">{review.title}</p>
                      {review.status === 'HIDDEN' ? <Badge tone="danger">Hidden</Badge> : <Badge tone="success">Published</Badge>}
                    </div>
                    {review.comment && <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{review.comment}</p>}
                    <p className="mt-2 text-xs text-slate-500">{review.userName} · {review.userEmail} · {formatDateTime(review.createdAt)}</p>
                    {review.moderationNote && <p className="mt-1 text-xs text-amber-800">Moderation note: {review.moderationNote}</p>}
                  </div>
                  <Button size="sm" variant={review.status === 'PUBLISHED' ? 'secondary' : 'primary'} onClick={() => setModerating(review)}>
                    {review.status === 'PUBLISHED' ? 'Hide' : 'Publish'}
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
      {reviews.data && <Pagination className="mt-4" page={reviews.data.page} totalPages={reviews.data.totalPages} onChange={(page) => setFilters({ page: String(page + 1) })} />}
      <ConfirmDialog open={moderating !== null} onClose={() => setModerating(null)}
        tone={moderating?.status === 'PUBLISHED' ? 'danger' : 'primary'}
        title={moderating?.status === 'PUBLISHED' ? 'Hide this review?' : 'Publish this review?'}
        message={moderating?.status === 'PUBLISHED' ? 'It will disappear from the product page and stop counting towards the rating.' : 'It will appear on the product page again.'}
        reasonLabel="Internal note (optional)" confirmLabel={moderating?.status === 'PUBLISHED' ? 'Hide review' : 'Publish review'}
        loading={moderate.isPending} onConfirm={(note) => {
          if (moderating) {
            moderate.mutate({ review: moderating, note });
          }
        }} />
    </AdminPage>
  );
}
