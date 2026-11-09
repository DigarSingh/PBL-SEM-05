import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { Button } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField, Input } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import { useDocumentTitle } from '@/lib/hooks';
import { authApi } from './api';
import { AuthShell } from './AuthShell';
import { emailOnlySchema, type EmailOnlyValues } from './schemas';

export default function ForgotPasswordPage() {
  useDocumentTitle('Forgot password');
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(emailOnlySchema),
    defaultValues: { email: '' },
  });
  const request = useMutation({ mutationFn: (values: EmailOnlyValues) => authApi.forgotPassword(values.email) });

  return (
    <AuthShell title="Forgot your password?" subtitle="Enter the email you signed up with and we'll send you a reset link."
      footer={<Link to="/login" className="font-semibold text-brand-700 hover:underline">Back to sign in</Link>}>
      {request.isSuccess ? (
        <InlineAlert tone="success">
          {request.data.message ?? 'If an account exists for that email, a password reset link has been sent.'}{' '}
          The link is valid for 30 minutes.
        </InlineAlert>
      ) : (
        <form noValidate className="space-y-4" onSubmit={handleSubmit((values) => request.mutate(values))}>
          {request.isError && <InlineAlert tone="danger">{errorMessage(request.error)}</InlineAlert>}
          <FormField label="Email" error={errors.email?.message}>
            {(field) => <Input {...field} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
          </FormField>
          <Button type="submit" size="lg" className="w-full" loading={request.isPending}>Send reset link</Button>
        </form>
      )}
    </AuthShell>
  );
}
