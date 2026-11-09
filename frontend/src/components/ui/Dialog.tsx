import { Dialog as HeadlessDialog, DialogBackdrop, DialogPanel, DialogTitle } from '@headlessui/react';
import { X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { Button } from './Button';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg' | 'xl';
}

const widths = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl', xl: 'max-w-4xl' };

/** Accessible modal dialog (focus trap, Escape to close, labelled title). */
export function Modal({ open, onClose, title, description, children, footer, size = 'md' }: ModalProps) {
  return (
    <HeadlessDialog open={open} onClose={onClose} className="relative z-50">
      <DialogBackdrop transition className="fixed inset-0 bg-slate-900/40 backdrop-blur-[2px] transition duration-150 data-closed:opacity-0" />
      <div className="fixed inset-0 overflow-y-auto">
        <div className="flex min-h-full items-end justify-center p-0 sm:items-center sm:p-4">
          <DialogPanel transition className={cn('w-full rounded-t-2xl bg-white shadow-xl transition duration-150 sm:rounded-2xl',
            'data-closed:translate-y-4 data-closed:opacity-0', widths[size])}>
            <div className="flex items-start justify-between gap-4 border-b border-stone-100 px-5 py-4">
              <div>
                <DialogTitle className="text-base font-semibold text-slate-900">{title}</DialogTitle>
                {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
              </div>
              <button type="button" onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-500 hover:bg-stone-100" aria-label="Close">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="max-h-[70vh] overflow-y-auto px-5 py-4">{children}</div>
            {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-stone-100 px-5 py-3">{footer}</div>}
          </DialogPanel>
        </div>
      </div>
    </HeadlessDialog>
  );
}

interface ConfirmProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void | Promise<void>;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  tone?: 'danger' | 'primary';
  loading?: boolean;
  reasonLabel?: string;
}

/** Confirmation for dangerous actions, optionally collecting a reason. */
export function ConfirmDialog({ open, onClose, onConfirm, title, message, confirmLabel = 'Confirm', tone = 'danger',
  loading, reasonLabel }: ConfirmProps) {
  const [reason, setReason] = useState('');
  return (
    <Modal open={open} onClose={onClose} title={title} size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={loading}>Keep it</Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} loading={loading}
            onClick={() => void onConfirm(reason.trim())}>
            {confirmLabel}
          </Button>
        </>
      }>
      <div className="space-y-3 text-sm text-slate-600">
        <div>{message}</div>
        {reasonLabel && (
          <label className="block">
            <span className="mb-1 block font-medium text-slate-700">{reasonLabel}</span>
            <textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={255} rows={3}
              className="block w-full rounded-lg border border-stone-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
          </label>
        )}
      </div>
    </Modal>
  );
}

export function Drawer({ open, onClose, title, children, side = 'right', footer }: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  side?: 'left' | 'right';
  footer?: ReactNode;
}) {
  return (
    <HeadlessDialog open={open} onClose={onClose} className="relative z-50">
      <DialogBackdrop transition className="fixed inset-0 bg-slate-900/40 transition duration-150 data-closed:opacity-0" />
      <div className={cn('fixed inset-y-0 flex w-full max-w-md', side === 'right' ? 'right-0' : 'left-0')}>
        <DialogPanel transition className={cn('flex h-full w-full flex-col bg-white shadow-xl transition duration-200',
          side === 'right' ? 'data-closed:translate-x-full' : 'data-closed:-translate-x-full')}>
          <div className="flex items-center justify-between border-b border-stone-100 px-5 py-4">
            <DialogTitle className="text-base font-semibold">{title}</DialogTitle>
            <button type="button" onClick={onClose} className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-500 hover:bg-stone-100" aria-label="Close">
              <X className="h-5 w-5" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
          {footer && <div className="border-t border-stone-100 px-5 py-3">{footer}</div>}
        </DialogPanel>
      </div>
    </HeadlessDialog>
  );
}
