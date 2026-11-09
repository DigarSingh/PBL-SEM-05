import { Search, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { cn } from '@/lib/cn';

/** Search box that applies on Enter (keeps list queries to one per search). Remount via `key` to reset. */
export function SearchInput({ value, onSearch, placeholder, className }: {
  value: string;
  onSearch: (value: string) => void;
  placeholder: string;
  className?: string;
}) {
  const [text, setText] = useState(value);
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSearch(text.trim());
  };
  return (
    <form role="search" onSubmit={submit} className={cn('relative', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
      <input type="search" value={text} onChange={(event) => setText(event.target.value)} placeholder={placeholder} aria-label={placeholder}
        className="h-9 w-full rounded-lg border border-stone-300 bg-white pl-9 pr-8 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 [&::-webkit-search-cancel-button]:hidden" />
      {text && (
        <button type="button" aria-label="Clear search" onClick={() => {
          setText('');
          onSearch('');
        }} className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:text-slate-700">
          <X className="h-4 w-4" aria-hidden />
        </button>
      )}
    </form>
  );
}
