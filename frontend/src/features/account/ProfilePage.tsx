import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, KeyRound } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField, Input } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { useSession } from '@/features/auth/useSession';
import { errorMessage, toApiError } from '@/lib/errors';
import { applyServerErrors, emptyToNull, newPasswordField, optionalPhoneField } from '@/lib/forms';
import { formatDate } from '@/lib/format';
import { useDocumentTitle } from '@/lib/hooks';
import { session } from '@/lib/session';
import type { UserProfile } from '@/lib/types';
import { accountApi } from './api';

const profileSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter your full name').max(120, 'Name is too long'),
  phone: optionalPhoneField,
});

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'Enter your current password').max(72),
  newPassword: newPasswordField,
  confirmPassword: z.string().min(1, 'Confirm your new password'),
}).refine((values) => values.newPassword === values.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });

export default function ProfilePage() {
  useDocumentTitle('Profile');
  const { user } = useSession();
  if (!user) {
    return null;
  }
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Profile &amp; security</h1>
        <p className="mt-1 text-sm text-slate-500">Member since {formatDate(user.createdAt)}</p>
      </div>
      <ProfileForm user={user} />
      <PasswordForm />
    </div>
  );
}

function ProfileForm({ user }: { user: UserProfile }) {
  const queryClient = useQueryClient();
  const { register, handleSubmit, setError, reset, formState: { errors, isDirty } } = useForm({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user.fullName, phone: user.phone ?? '' },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof profileSchema>) => accountApi.updateProfile({ fullName: values.fullName, phone: emptyToNull(values.phone) }),
    onSuccess: (profile) => {
      session.updateUser(profile);
      reset({ fullName: profile.fullName, phone: profile.phone ?? '' });
      void queryClient.invalidateQueries({ queryKey: ['me', 'addresses'] });
      toast.success('Profile updated');
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError, ['fullName', 'phone'])) {
        toast.error(errorMessage(error));
      }
    },
  });
  return (
    <Card>
      <CardHeader title="Personal details" />
      <form noValidate className="grid gap-4 p-5 sm:grid-cols-2" onSubmit={handleSubmit((values) => save.mutate(values))}>
        <FormField label="Full name" error={errors.fullName?.message} required>
          {(field) => <Input {...field} autoComplete="name" {...register('fullName')} />}
        </FormField>
        <FormField label="Mobile number" error={errors.phone?.message}>
          {(field) => <Input {...field} type="tel" autoComplete="tel" {...register('phone')} />}
        </FormField>
        <div className="sm:col-span-2">
          <p className="mb-1.5 text-sm font-medium text-slate-700">Email</p>
          <p className="flex items-center gap-2 text-sm text-slate-900">
            {user.email}
            {user.emailVerified
              ? <Badge tone="success"><BadgeCheck className="h-3 w-3" aria-hidden />Verified</Badge>
              : <Badge tone="warning">Not verified</Badge>}
          </p>
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" loading={save.isPending} disabled={!isDirty}>Save changes</Button>
        </div>
      </form>
    </Card>
  );
}

function PasswordForm() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const change = useMutation({
    mutationFn: (values: z.output<typeof passwordSchema>) => accountApi.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: () => {
      // The server revoked every session, including this one: sign in again with the new password.
      session.clear('logout');
      session.announce('logout');
      queryClient.removeQueries({ queryKey: ['me'] });
      toast.success('Password changed. Please sign in with your new password.');
      navigate('/login', { replace: true });
    },
    onError: (error) => {
      const apiError = toApiError(error);
      if (apiError.code === 'INVALID_CREDENTIALS') {
        setError('currentPassword', { type: 'server', message: apiError.message });
      } else if (!applyServerErrors(error, setError, ['currentPassword', 'newPassword'])) {
        setError('newPassword', { type: 'server', message: apiError.message });
      }
    },
  });
  return (
    <Card>
      <CardHeader title={<span className="flex items-center gap-2"><KeyRound className="h-4 w-4" aria-hidden />Change password</span>}
        description="You'll be signed out on all devices and asked to sign in again." />
      <form noValidate className="grid gap-4 p-5 sm:grid-cols-2" onSubmit={handleSubmit((values) => change.mutate(values))}>
        <FormField label="Current password" error={errors.currentPassword?.message} required className="sm:col-span-2">
          {(field) => <PasswordInput {...field} autoComplete="current-password" {...register('currentPassword')} />}
        </FormField>
        <FormField label="New password" error={errors.newPassword?.message} required hint="At least 8 characters, including a letter and a number.">
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('newPassword')} />}
        </FormField>
        <FormField label="Confirm new password" error={errors.confirmPassword?.message} required>
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('confirmPassword')} />}
        </FormField>
        {change.isError && Object.keys(errors).length === 0 && <InlineAlert tone="danger" className="sm:col-span-2">{errorMessage(change.error)}</InlineAlert>}
        <div className="sm:col-span-2">
          <Button type="submit" loading={change.isPending}>Change password</Button>
        </div>
      </form>
    </Card>
  );
}
