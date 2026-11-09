import { ImageOff } from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/cn';

/** Product photo with lazy loading and a neutral placeholder when the image is missing or broken. */
export function ProductImage({ src, alt, className, eager = false }: {
  src: string | null | undefined;
  alt: string;
  className?: string;
  eager?: boolean;
}) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (!src || failedSrc === src) {
    return (
      <div role="img" aria-label={alt} className={cn('flex items-center justify-center bg-stone-100 text-stone-400', className)}>
        <ImageOff className="h-8 w-8" aria-hidden />
      </div>
    );
  }
  return (
    <img src={src} alt={alt} loading={eager ? 'eager' : 'lazy'} decoding="async" onError={() => setFailedSrc(src)}
      className={cn('bg-stone-100 object-cover', className)} />
  );
}
