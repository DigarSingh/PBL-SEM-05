import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Dialog';
import { FormField, Input, Textarea } from '@/components/ui/Field';
import { RatingInput } from '@/components/ui/Rating';
import { emptyToNull } from '@/lib/forms';
import type { Review } from '@/lib/types';
import type { ReviewInput } from './api';

const reviewSchema = z.object({
  rating: z.number().int().min(1, 'Choose a star rating').max(5),
  title: z.string().trim().min(3, 'Add a short headline').max(120, 'Keep the headline under 120 characters'),
  comment: z.string().trim().max(2000, 'Keep your review under 2000 characters'),
});

export function ReviewForm({ open, onClose, existing, productName, submitting, onSubmit }: {
  open: boolean;
  onClose: () => void;
  existing: Review | null;
  productName: string;
  submitting: boolean;
  onSubmit: (input: ReviewInput) => void;
}) {
  const { register, control, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(reviewSchema),
    defaultValues: { rating: existing?.rating ?? 0, title: existing?.title ?? '', comment: existing?.comment ?? '' },
  });
  const submit = handleSubmit((values) => onSubmit({ rating: values.rating, title: values.title, comment: emptyToNull(values.comment) }));
  return (
    <Modal open={open} onClose={onClose} title={existing ? 'Edit your review' : 'Write a review'} description={productName}
      footer={<>
        <Button variant="secondary" onClick={onClose} disabled={submitting}>Cancel</Button>
        <Button type="submit" form="review-form" loading={submitting}>{existing ? 'Save changes' : 'Post review'}</Button>
      </>}>
      <form id="review-form" noValidate className="space-y-4" onSubmit={submit}>
        <div>
          <p className="mb-1.5 text-sm font-medium text-slate-700">Your rating</p>
          <Controller control={control} name="rating" render={({ field }) => <RatingInput value={field.value} onChange={field.onChange} />} />
          {errors.rating && <p className="mt-1 text-xs font-medium text-rose-600" role="alert">{errors.rating.message}</p>}
        </div>
        <FormField label="Headline" error={errors.title?.message} required>
          {(field) => <Input {...field} placeholder="What stood out?" {...register('title')} />}
        </FormField>
        <FormField label="Your review" error={errors.comment?.message} hint="Optional. What did you like or dislike? How do you use it?">
          {(field) => <Textarea {...field} rows={5} {...register('comment')} />}
        </FormField>
      </form>
    </Modal>
  );
}
