import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, PenLine } from 'lucide-react';
import { useState } from 'react';
import { useLocation } from 'react-router';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/Badge';
import { Button, ButtonLink } from '@/components/ui/Button';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { InlineAlert, Skeleton } from '@/components/ui/Feedback';
import { Select } from '@/components/ui/Field';
import { RatingStars } from '@/components/ui/Rating';
import { useSession } from '@/features/auth/useSession';
import { errorMessage } from '@/lib/errors';
import { formatDate } from '@/lib/format';
import { loginPath } from '@/lib/navigation';
import type { Review } from '@/lib/types';
import { reviewsApi, type ReviewInput, type ReviewSort } from './api';
import { ReviewForm } from './ReviewForm';

export function ReviewsSection({ productId, productSlug }: { productId: number; productSlug: string }) {
  const queryClient = useQueryClient();
  const location = useLocation();
  const { status, user } = useSession();
  const [sort, setSort] = useState<ReviewSort>('newest');
  const [page, setPage] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const summary = useQuery({ queryKey: ['reviews', productId, 'summary'], queryFn: () => reviewsApi.summary(productId) });
  const list = useQuery({
    queryKey: ['reviews', productId, 'list', user?.id ?? 'guest', sort, page],
    queryFn: () => reviewsApi.list(productId, sort, page),
    placeholderData: keepPreviousData,
    enabled: status !== 'loading',
  });
  const eligibility = useQuery({
    queryKey: ['me', 'reviews', productId, 'eligibility'],
    queryFn: () => reviewsApi.eligibility(productId),
    enabled: status === 'authenticated',
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] }),
      queryClient.invalidateQueries({ queryKey: ['me', 'reviews', productId] }),
      queryClient.invalidateQueries({ queryKey: ['product', productSlug] }),
    ]);
  };

  const existing = eligibility.data?.existingReview ?? null;
  const save = useMutation({
    mutationFn: (input: ReviewInput) => (existing ? reviewsApi.update(existing.id, input) : reviewsApi.create(productId, input)),
    onSuccess: async () => {
      toast.success(existing ? 'Your review was updated' : 'Thanks for your review!');
      setFormOpen(false);
      await refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: (reviewId: number) => reviewsApi.remove(reviewId),
    onSuccess: async () => {
      toast.success('Your review was deleted');
      setConfirmDelete(false);
      await refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const stats = summary.data;
  return (
    <section id="reviews" className="scroll-mt-24 py-10" aria-labelledby="reviews-heading">
      <h2 id="reviews-heading" className="text-xl font-bold tracking-tight text-slate-900">Ratings &amp; reviews</h2>
      <div className="mt-6 grid gap-10 lg:grid-cols-[18rem_1fr]">
        <div>
          {stats ? (
            <>
              <div className="flex items-end gap-3">
                <span className="text-5xl font-bold text-slate-900">{stats.reviewCount > 0 ? stats.averageRating.toFixed(1) : '–'}</span>
                <div className="pb-1.5">
                  <RatingStars value={stats.averageRating} size="md" />
                  <p className="mt-1 text-sm text-slate-500">{stats.reviewCount} review{stats.reviewCount === 1 ? '' : 's'}</p>
                </div>
              </div>
              <ul className="mt-5 space-y-1.5">
                {[5, 4, 3, 2, 1].map((star) => {
                  const count = stats.distribution[String(star)] ?? 0;
                  const share = stats.reviewCount ? (count / stats.reviewCount) * 100 : 0;
                  return (
                    <li key={star} className="flex items-center gap-2 text-xs text-slate-600">
                      <span className="w-8">{star} star</span>
                      <span className="h-2 flex-1 overflow-hidden rounded-full bg-stone-200">
                        <span className="block h-full rounded-full bg-amber-400" style={{ width: `${share}%` }} />
                      </span>
                      <span className="w-6 text-right tabular-nums">{count}</span>
                    </li>
                  );
                })}
              </ul>
            </>
          ) : (
            <Skeleton className="h-40 w-full" />
          )}

          <div className="mt-6">
            {status !== 'authenticated' ? (
              <div className="rounded-2xl border border-stone-200 bg-white p-4 text-sm text-slate-600">
                <p>Bought this product? Sign in to share your experience.</p>
                <ButtonLink to={loginPath(location.pathname)} variant="secondary" size="sm" className="mt-3">Sign in to review</ButtonLink>
              </div>
            ) : eligibility.data?.canReview ? (
              <Button className="w-full" icon={<PenLine className="h-4 w-4" aria-hidden />} onClick={() => setFormOpen(true)}>
                Write a review
              </Button>
            ) : existing ? (
              <div className="flex gap-2">
                <Button variant="secondary" className="flex-1" onClick={() => setFormOpen(true)}>Edit your review</Button>
                <Button variant="ghost" className="text-rose-700" onClick={() => setConfirmDelete(true)}>Delete</Button>
              </div>
            ) : eligibility.data?.reason ? (
              <InlineAlert tone="info">{eligibility.data.reason}</InlineAlert>
            ) : null}
          </div>
        </div>

        <div>
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="text-sm text-slate-500">{list.data ? `${list.data.totalElements} review${list.data.totalElements === 1 ? '' : 's'}` : ''}</p>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              Sort
              <Select value={sort} onChange={(event) => {
                setSort(event.target.value as ReviewSort);
                setPage(0);
              }} className="h-9 w-auto">
                <option value="newest">Newest</option>
                <option value="highest">Highest rated</option>
                <option value="lowest">Lowest rated</option>
              </Select>
            </label>
          </div>
          {list.isPending ? (
            <div className="space-y-4">{[0, 1, 2].map((key) => <Skeleton key={key} className="h-24 w-full" />)}</div>
          ) : list.isError ? (
            <InlineAlert tone="danger">{errorMessage(list.error)}</InlineAlert>
          ) : list.data.items.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-stone-300 p-8 text-center text-sm text-slate-500">
              No reviews yet. Customers who receive this product can review it.
            </p>
          ) : (
            <ul className="space-y-4">
              {list.data.items.map((review) => <ReviewItem key={review.id} review={review} />)}
            </ul>
          )}
          {list.data && list.data.totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
              <span className="text-sm text-slate-500">Page {page + 1} of {list.data.totalPages}</span>
              <Button variant="secondary" size="sm" disabled={!list.data.hasNext} onClick={() => setPage(page + 1)}>Next</Button>
            </div>
          )}
        </div>
      </div>

      {formOpen && (
        <ReviewForm open={formOpen} onClose={() => setFormOpen(false)} existing={existing} productName=""
          submitting={save.isPending} onSubmit={(input) => save.mutate(input)} />
      )}
      <ConfirmDialog open={confirmDelete} onClose={() => setConfirmDelete(false)} title="Delete your review?"
        message="Your rating will be removed from this product's score. You can write a new review later."
        confirmLabel="Delete review" loading={remove.isPending}
        onConfirm={() => {
          if (existing) {
            remove.mutate(existing.id);
          }
        }} />
    </section>
  );
}

function ReviewItem({ review }: { review: Review }) {
  return (
    <li className="rounded-2xl border border-stone-200 bg-white p-5">
      <div className="flex flex-wrap items-center gap-2">
        <RatingStars value={review.rating} />
        <p className="font-semibold text-slate-900">{review.title}</p>
        {review.mine && <Badge tone="brand">Your review</Badge>}
      </div>
      {review.comment && <p className="mt-2 whitespace-pre-line text-sm text-slate-700">{review.comment}</p>}
      <p className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
        <span className="font-medium text-slate-700">{review.authorName}</span>
        {review.verifiedPurchase && (
          <span className="inline-flex items-center gap-1 text-emerald-700"><BadgeCheck className="h-3.5 w-3.5" aria-hidden />Verified purchase</span>
        )}
        <span>{formatDate(review.createdAt)}</span>
      </p>
    </li>
  );
}
