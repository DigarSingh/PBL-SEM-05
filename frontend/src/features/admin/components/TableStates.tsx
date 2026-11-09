import { Inbox } from 'lucide-react';
import { Skeleton } from '@/components/ui/Feedback';

export function TableSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div className="space-y-2 p-4" aria-busy="true">
      {Array.from({ length: rows }, (_, index) => <Skeleton key={index} className="h-10 w-full" />)}
    </div>
  );
}

export function TableEmpty({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-12 text-center text-sm text-slate-500">
      <Inbox className="h-6 w-6 text-slate-400" aria-hidden />
      {message}
    </div>
  );
}
