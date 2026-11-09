import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { MailCheck } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { Button, ButtonLink } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField, Input } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { toApiError } from '@/lib/errors';
import { applyServerErrors, emptyToNull } from '@/lib/forms';
import { useDocumentTitle } from '@/lib/hooks';
import { authApi } from './api';
import { AuthShell } from './AuthShell';
import { registerSchema, type RegisterValues } from './schemas';

interface Registered {
  email: string;
  verificationRequired: boolean;
}

export default function RegisterPage() {
  useDocumentTitle('Create account');
  const [params] = useSearchParams();
  const [registered, setRegistered] = useState<Registered | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, setError, formState: { errors } } = useForm({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', phone: '', password: '', confirmPassword: '' },
  });

  const signUp = useMutation({
    mutationFn: (values: RegisterValues) => authApi.register({
      fullName: values.fullName,
      email: values.email,
      password: values.password,
      phone: emptyToNull(values.phone),
    }),
    onSuccess: (envelope, values) => setRegistered({
      email: values.email,
      verificationRequired: envelope.data.emailVerificationRequired,
    }),
    onError: (error) => {
      const apiError = toApiError(error);
      if (apiError.code === 'DUPLICATE_RESOURCE') {
        setError('email', { type: 'server', message: apiError.message });
      } else if (!applyServerErrors(error, setError, ['fullName', 'email', 'phone', 'password'])) {
        setFormError(apiError.message);
      }
    },
  });

  const resend = useMutation({
    mutationFn: (email: string) => authApi.resendVerification(email),
    onSuccess: (envelope) => toast.success(envelope.message ?? 'Verification email sent'),
    onError: (error) => toast.error(toApiError(error).message),
  });

  const query = params.toString();
  const loginLink = `/login${query ? `?${query}` : ''}`;

  if (registered) {
    return (
      <AuthShell title={registered.verificationRequired ? 'Check your inbox' : 'Account created'}>
        <div className="space-y-4 text-sm text-slate-600">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <MailCheck className="h-6 w-6" aria-hidden />
          </span>
          {registered.verificationRequired ? (
            <>
              <p>
                We sent a verification link to <strong className="text-slate-900">{registered.email}</strong>.
                Open it to activate your account, then sign in.
              </p>
              <p className="text-slate-500">The link is valid for 24 hours. Check your spam folder if you don&apos;t see it.</p>
              <div className="flex flex-wrap gap-2 pt-2">
                <ButtonLink to={loginLink}>Go to sign in</ButtonLink>
                <Button variant="secondary" loading={resend.isPending} onClick={() => resend.mutate(registered.email)}>
                  Resend email
                </Button>
              </div>
            </>
          ) : (
            <>
              <p>Your account is ready. Sign in to start shopping.</p>
              <ButtonLink to={loginLink}>Sign in</ButtonLink>
            </>
          )}
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Create your account" subtitle="Save your cart and wishlist, check out faster and track every order live."
      footer={<>Already have an account?{' '}
        <Link to={loginLink} className="font-semibold text-brand-700 hover:underline">Sign in</Link>
      </>}>
      <form noValidate className="space-y-4" onSubmit={handleSubmit((values) => {
        setFormError(null);
        signUp.mutate(values);
      })}>
        {formError && <InlineAlert tone="danger">{formError}</InlineAlert>}
        <FormField label="Full name" error={errors.fullName?.message} required>
          {(field) => <Input {...field} autoComplete="name" {...register('fullName')} />}
        </FormField>
        <FormField label="Email" error={errors.email?.message} required>
          {(field) => <Input {...field} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
        </FormField>
        <FormField label="Mobile number" error={errors.phone?.message} hint="Optional. Used for delivery updates.">
          {(field) => <Input {...field} type="tel" autoComplete="tel" inputMode="tel" {...register('phone')} />}
        </FormField>
        <FormField label="Password" error={errors.password?.message} required
          hint="At least 8 characters, including a letter and a number.">
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('password')} />}
        </FormField>
        <FormField label="Confirm password" error={errors.confirmPassword?.message} required>
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('confirmPassword')} />}
        </FormField>
        <Button type="submit" size="lg" className="w-full" loading={signUp.isPending}>Create account</Button>
      </form>
    </AuthShell>
  );
}
