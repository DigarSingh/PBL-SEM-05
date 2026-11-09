import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { Button, ButtonLink } from '@/components/ui/Button';
import { InlineAlert } from '@/components/ui/Feedback';
import { FormField } from '@/components/ui/Field';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { errorMessage } from '@/lib/errors';
import { useDocumentTitle } from '@/lib/hooks';
import { authApi } from './api';
import { AuthShell } from './AuthShell';
import { resetPasswordSchema, type ResetPasswordValues } from './schemas';

export default function ResetPasswordPage() {
  useDocumentTitle('Choose a new password');
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: '', confirmPassword: '' },
  });
  const reset = useMutation({
    mutationFn: (values: ResetPasswordValues) => authApi.resetPassword(token, values.password),
    onSuccess: (envelope) => {
      toast.success(envelope.message ?? 'Your password has been reset. Please sign in.');
      navigate('/login', { replace: true });
    },
  });

  if (!token) {
    return (
      <AuthShell title="Invalid reset link" subtitle="This link is incomplete. Request a new one to reset your password.">
        <ButtonLink to="/forgot-password">Request a new link</ButtonLink>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Choose a new password" subtitle="Pick a password you haven't used here before."
      footer={<Link to="/forgot-password" className="font-semibold text-brand-700 hover:underline">Need a new reset link?</Link>}>
      <form noValidate className="space-y-4" onSubmit={handleSubmit((values) => reset.mutate(values))}>
        {reset.isError && <InlineAlert tone="danger">{errorMessage(reset.error)}</InlineAlert>}
        <FormField label="New password" error={errors.password?.message} required
          hint="At least 8 characters, including a letter and a number.">
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('password')} />}
        </FormField>
        <FormField label="Confirm new password" error={errors.confirmPassword?.message} required>
          {(field) => <PasswordInput {...field} autoComplete="new-password" {...register('confirmPassword')} />}
        </FormField>
        <Button type="submit" size="lg" className="w-full" loading={reset.isPending}>Reset password</Button>
      </form>
    </AuthShell>
  );
}
