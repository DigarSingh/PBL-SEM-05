import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes,
  type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

const control = cn(
  'block w-full rounded-lg border border-stone-300 bg-white px-3 text-sm text-slate-900 shadow-xs',
  'placeholder:text-slate-400 transition-colors',
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20',
  'disabled:cursor-not-allowed disabled:bg-stone-100 disabled:text-slate-500',
  'aria-[invalid=true]:border-rose-500 aria-[invalid=true]:focus:ring-rose-500/20',
);

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...rest }, ref) {
  return <input ref={ref} className={cn(control, 'h-10', className)} {...rest} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...rest }, ref) {
  return <textarea ref={ref} className={cn(control, 'min-h-24 py-2', className)} {...rest} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...rest }, ref) {
  return (
    <select ref={ref} className={cn(control, 'h-10 pr-8', className)} {...rest}>
      {children}
    </select>
  );
});

interface FormFieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  className?: string;
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}

/** Label + control + hint/error with correct ARIA wiring for screen readers. */
export function FormField({ label, error, hint, required, className, children }: FormFieldProps) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-rose-600" aria-hidden>*</span>}
      </label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {error ? (
        <p id={`${id}-error`} className="text-xs font-medium text-rose-600" role="alert">{error}</p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-xs text-slate-500">{hint}</p>
      ) : null}
    </div>
  );
}

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox({ label, className, ...rest }, ref) {
  return (
    <label className={cn('inline-flex cursor-pointer items-center gap-2 text-sm text-slate-700', className)}>
      <input ref={ref} type="checkbox"
        className="h-4 w-4 rounded border-stone-300 text-brand-600 accent-brand-600 focus:ring-brand-500" {...rest} />
      <span>{label}</span>
    </label>
  );
});
