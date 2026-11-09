import { apiDelete, apiGet, apiPost, apiPut } from '@/lib/api';
import type { Page, Review, ReviewEligibility, ReviewSummary } from '@/lib/types';

export type ReviewSort = 'newest' | 'highest' | 'lowest';

export interface ReviewInput {
  rating: number;
  title: string;
  comment: string | null;
}

export const reviewsApi = {
  list: (productId: number, sort: ReviewSort, page: number, size = 5) =>
    apiGet<Page<Review>>(`/products/${productId}/reviews`, { sort, page, size }),
  summary: (productId: number) => apiGet<ReviewSummary>(`/products/${productId}/reviews/summary`),
  eligibility: (productId: number) => apiGet<ReviewEligibility>(`/products/${productId}/reviews/eligibility`),
  create: (productId: number, input: ReviewInput) => apiPost<Review>(`/products/${productId}/reviews`, input),
  update: (reviewId: number, input: ReviewInput) => apiPut<Review>(`/reviews/${reviewId}`, input),
  remove: (reviewId: number) => apiDelete<void>(`/reviews/${reviewId}`),
};
