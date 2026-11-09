import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, Layers, Search, Tag, X } from 'lucide-react';
import { useId, useState, type KeyboardEvent } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { useSession } from '@/features/auth/useSession';
import { cn } from '@/lib/cn';
import { useDebouncedValue } from '@/lib/hooks';
import { searchApi } from './api';

interface Option {
  key: string;
  kind: 'query' | 'recent' | 'product' | 'category' | 'brand';
  label: string;
  to: string;
  imageUrl?: string | null;
}

const groupTitles: Record<Option['kind'], string> = {
  query: '',
  recent: 'Recent searches',
  product: 'Products',
  category: 'Categories',
  brand: 'Brands',
};

/** Header search with autocomplete. Remounted (keyed) when the URL's query changes. */
export function SearchBox({ className }: { className?: string }) {
  const location = useLocation();
  const [params] = useSearchParams();
  const currentQuery = location.pathname === '/search' ? params.get('q') ?? '' : '';
  return <SearchCombobox key={currentQuery} initialQuery={currentQuery} className={className} />;
}

function searchUrl(text: string): string {
  return `/search?q=${encodeURIComponent(text)}`;
}

function SearchCombobox({ initialQuery, className }: { initialQuery: string; className?: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { status } = useSession();
  const [value, setValue] = useState(initialQuery);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const listId = useId();
  const term = useDebouncedValue(value.trim(), 200);

  const suggestions = useQuery({
    queryKey: ['search-suggestions', status === 'authenticated', term],
    queryFn: ({ signal }) => searchApi.suggestions(term, signal),
    enabled: open,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });

  const options: Option[] = [];
  if (value.trim()) {
    options.push({ key: 'query', kind: 'query', label: value.trim(), to: searchUrl(value.trim()) });
  }
  const data = suggestions.data;
  if (data && term === value.trim()) {
    if (!term) {
      data.recentSearches.forEach((text) => options.push({ key: `recent-${text}`, kind: 'recent', label: text, to: searchUrl(text) }));
    }
    data.products.forEach((item) => options.push({ key: `product-${item.id}`, kind: 'product', label: item.name, to: `/products/${item.slug}`, imageUrl: item.imageUrl }));
    data.categories.forEach((item) => options.push({ key: `category-${item.id}`, kind: 'category', label: item.name, to: `/categories/${item.slug}` }));
    data.brands.forEach((item) => options.push({ key: `brand-${item.id}`, kind: 'brand', label: item.name, to: `/shop?brand=${encodeURIComponent(item.slug)}` }));
  }
  const expanded = open && options.length > 0;

  const go = (to: string) => {
    setOpen(false);
    setActive(-1);
    navigate(to);
  };

  const submit = () => {
    const text = value.trim();
    if (text) {
      go(searchUrl(text));
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((index) => (options.length ? (index + 1) % options.length : -1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => (options.length ? (index <= 0 ? options.length - 1 : index - 1) : -1));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (expanded && active >= 0 && options[active]) {
        go(options[active].to);
      } else {
        submit();
      }
    } else if (event.key === 'Escape') {
      setOpen(false);
      setActive(-1);
    }
  };

  const clearRecent = async () => {
    await searchApi.clearRecent();
    await queryClient.invalidateQueries({ queryKey: ['search-suggestions'] });
  };

  return (
    <div className={cn('relative', className)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
          setOpen(false);
          setActive(-1);
        }
      }}>
      <form role="search" onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}>
        <label htmlFor={`${listId}-input`} className="sr-only">Search products</label>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden />
          <input id={`${listId}-input`} type="search" value={value} autoComplete="off" enterKeyHint="search"
            placeholder='Try "wireless earbuds for the gym under 3000"'
            role="combobox" aria-expanded={expanded} aria-controls={listId} aria-autocomplete="list"
            aria-activedescendant={expanded && active >= 0 ? `${listId}-${active}` : undefined}
            onChange={(event) => {
              setValue(event.target.value);
              setOpen(true);
              setActive(-1);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={onKeyDown}
            className="h-11 w-full rounded-xl border border-stone-300 bg-stone-50 pl-9 pr-9 text-sm text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 [&::-webkit-search-cancel-button]:hidden" />
          {value && (
            <button type="button" onClick={() => {
              setValue('');
              setActive(-1);
            }} className="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:text-slate-700" aria-label="Clear search">
              <X className="h-4 w-4" aria-hidden />
            </button>
          )}
        </div>
      </form>
      {expanded && (
        <div className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-2xl border border-stone-200 bg-white shadow-xl animate-fade-in">
          <ul id={listId} role="listbox" aria-label="Search suggestions" className="max-h-[26rem] overflow-y-auto py-1.5">
            {options.map((option, index) => {
              const showTitle = option.kind !== 'query' && (index === 0 || options[index - 1].kind !== option.kind);
              return (
                <li key={option.key} role="presentation">
                  {showTitle && (
                    <div className="flex items-center justify-between px-4 pb-1 pt-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
                      <span>{groupTitles[option.kind]}</span>
                      {option.kind === 'recent' && (
                        <button type="button" tabIndex={-1} onMouseDown={(event) => event.preventDefault()}
                          onClick={() => void clearRecent()} className="normal-case tracking-normal text-brand-700 hover:underline">
                          Clear
                        </button>
                      )}
                    </div>
                  )}
                  <div id={`${listId}-${index}`} role="option" aria-selected={active === index}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => go(option.to)}
                    className={cn('mx-1.5 flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2 text-sm text-slate-700',
                      active === index && 'bg-stone-100 text-slate-900')}>
                    <OptionIcon option={option} />
                    <span className="min-w-0 flex-1 truncate">
                      {option.kind === 'query' ? <>Search for <strong className="text-slate-900">&ldquo;{option.label}&rdquo;</strong></> : option.label}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}

function OptionIcon({ option }: { option: Option }) {
  const box = 'flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-stone-100 text-slate-500';
  if (option.kind === 'product') {
    return option.imageUrl
      ? <img src={option.imageUrl} alt="" className="h-8 w-8 shrink-0 rounded-md bg-stone-100 object-cover" loading="lazy" />
      : <span className={box}><Tag className="h-4 w-4" aria-hidden /></span>;
  }
  const Icon = option.kind === 'recent' ? Clock : option.kind === 'category' ? Layers : option.kind === 'brand' ? Tag : Search;
  return <span className={box}><Icon className="h-4 w-4" aria-hidden /></span>;
}
