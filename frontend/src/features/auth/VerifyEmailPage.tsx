import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CircleCheck, CircleX } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useSearchParams } from 'react-router';
import { Button, ButtonLink } from '@/components/ui/Button';
import { InlineAlert, Spinner } from '@/components/ui/Feedback';
import { FormField, Input } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { useDocumentTitle } from '@/lib/hooks';
import { authApi } from './api';
import { AuthShell } from './AuthShell';
import { emailOnlySchema, type EmailOnlyValues } from './schemas';
import { useSession } from './useSession';

export default function VerifyEmailPage() {
  useDocumentTitle('Verify email');
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const { status } = useSession();
  // A query (not an effect) so the single-use token is sent exactly once, even in StrictMode.
  const verification = useQuery({
    queryKey: ['verify-email', token],
    queryFn: () => authApi.verifyEmail(token),
    enabled: token.length > 0,
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
    refetchOnWindowFocus: false,
  });

  if (token && verification.isPending) {
    return (
      <AuthShell title="Verifying your email">
        <Spinner label="Verifying your email" />
      </AuthShell>
    );
  }

  if (token && verification.isSuccess) {
    return (
      <AuthShell title="Email verified">
        <div className="space-y-4 text-sm text-slate-600">
          <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
            <CircleCheck className="h-6 w-6" aria-hidden />
          </span>
          <p>{verification.data.message ?? 'Your email address is verified.'}</p>
          {status === 'authenticated'
            ? <ButtonLink to="/">Continue shopping</ButtonLink>
            : <ButtonLink to="/login">Sign in</ButtonLink>}
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Verification link not valid"
      subtitle="The link may have expired or already been used. Request a fresh one below.">
      <div className="space-y-4">
        {token && verification.isError && (
          <InlineAlert tone="danger">
            <span className="flex items-start gap-2">
              <CircleX className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              {errorMessage(verification.error)}
            </span>
          </InlineAlert>
        )}
        <ResendVerificationForm />
      </div>
    </AuthShell>
  );
}

function ResendVerificationForm() {
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(emailOnlySchema),
    defaultValues: { email: '' },
  });
  const resend = useMutation({ mutationFn: (values: EmailOnlyValues) => authApi.resendVerification(values.email) });
  if (resend.isSuccess) {
    return <InlineAlert tone="success">{resend.data.message ?? 'A new verification link has been sent.'}</InlineAlert>;
  }
  return (
    <form noValidate className="space-y-4" onSubmit={handleSubmit((values) => resend.mutate(values))}>
      {resend.isError && <InlineAlert tone="danger">{errorMessage(resend.error)}</InlineAlert>}
      <FormField label="Email" error={errors.email?.message}>
        {(field) => <Input {...field} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
      </FormField>
      <Button type="submit" className="w-full" loading={resend.isPending}>Send a new verification link</Button>
    </form>
  );
}
