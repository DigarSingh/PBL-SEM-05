import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField, Input } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { toApiError } from '@/lib/errors';
import { applyServerErrors } from '@/lib/forms';
import { useDocumentTitle } from '@/lib/hooks';
import { session } from '@/lib/session';
import { authApi } from './api';
import { AuthShell } from './AuthShell';
import { loginSchema, type LoginValues } from './schemas';

type Notice = { kind: 'error'; message: string } | { kind: 'unverified'; email: string };

export default function LoginPage() {
  useDocumentTitle('Sign in');
  const [params] = useSearchParams();
  const [notice, setNotice] = useState<Notice | null>(null);
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const login = useMutation({
    mutationFn: (values: LoginValues) => authApi.login(values.email, values.password),
    onSuccess: (auth) => {
      // The GuestOnly route redirects to the requested page as soon as the session is authenticated.
      session.setAuth(auth);
      session.announce('login');
      toast.success(`Welcome back, ${auth.user.fullName.split(' ')[0]}!`);
    },
    onError: (error, values) => {
      const apiError = toApiError(error);
      if (apiError.code === 'EMAIL_NOT_VERIFIED') {
        setNotice({ kind: 'unverified', email: values.email });
      } else if (!applyServerErrors(error, setError, ['email', 'password'])) {
        setNotice({ kind: 'error', message: apiError.message });
      }
    },
  });

  const resend = useMutation({
    mutationFn: (email: string) => authApi.resendVerification(email),
    onSuccess: (envelope) => toast.success(envelope.message ?? 'Verification email sent'),
    onError: (error) => toast.error(toApiError(error).message),
  });

  const query = params.toString();
  return (
    <AuthShell title="Sign in" subtitle="Welcome back. Sign in to manage your cart, check out and track orders."
      footer={<>New to ShopSmart?{' '}
        <Link to={`/register${query ? `?${query}` : ''}`} className="font-semibold text-brand-700 hover:underline">Create an account</Link>
      </>}>
      <form noValidate className="space-y-4" onSubmit={handleSubmit((values) => {
        setNotice(null);
        login.mutate(values);
      })}>
        {notice?.kind === 'error' && <InlineAlert tone="danger">{notice.message}</InlineAlert>}
        {notice?.kind === 'unverified' && (
          <InlineAlert tone="warning">
            <p className="font-semibold">Please verify your email address first.</p>
            <p className="mt-1">We sent a verification link to {notice.email}. Can&apos;t find it?</p>
            <Button variant="link" className="mt-1" loading={resend.isPending} onClick={() => resend.mutate(notice.email)}>
              Resend verification email
            </Button>
          </InlineAlert>
        )}
        <FormField label="Email" error={errors.email?.message}>
          {(field) => <Input {...field} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
        </FormField>
        <FormField label="Password" error={errors.password?.message}>
          {(field) => <PasswordInput {...field} autoComplete="current-password" {...register('password')} />}
        </FormField>
        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm font-medium text-brand-700 hover:underline">Forgot password?</Link>
        </div>
        <Button type="submit" size="lg" className="w-full" loading={login.isPending}>Sign in</Button>
      </form>
    </AuthShell>
  );
}
