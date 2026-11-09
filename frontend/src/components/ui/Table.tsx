import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export function TableWrap({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('overflow-x-auto', className)} {...rest} />;
}

export function Table({ className, ...rest }: HTMLAttributes<HTMLTableElement>) {
  return <table className={cn('w-full min-w-[640px] text-left text-sm', className)} {...rest} />;
}

export function Th({ className, ...rest }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th scope="col" className={cn('border-b border-stone-200 bg-stone-50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500', className)} {...rest} />;
}

export function Td({ className, ...rest }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('border-b border-stone-100 px-4 py-3 align-middle text-slate-700', className)} {...rest} />;
}
