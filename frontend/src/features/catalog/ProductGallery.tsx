import { useState } from 'react';
import { ProductImage } from '@/components/ProductImage';
import { cn } from '@/lib/cn';
import type { ProductImage as ProductImageData } from '@/lib/types';

export function ProductGallery({ images, name }: { images: ProductImageData[]; name: string }) {
  const [index, setIndex] = useState(0);
  const current = images[index] ?? images[0];
  return (
    <div className="flex flex-col-reverse gap-3 sm:flex-row">
      {images.length > 1 && (
        <div className="flex gap-2 overflow-x-auto scrollbar-none sm:max-h-[32rem] sm:flex-col sm:overflow-y-auto" role="tablist" aria-label="Product images">
          {images.map((image, position) => (
            <button key={image.id} type="button" role="tab" aria-selected={position === index}
              aria-label={`Show image ${position + 1} of ${images.length}`} onClick={() => setIndex(position)}
              className={cn('h-16 w-16 shrink-0 overflow-hidden rounded-xl border-2 bg-white transition-colors',
                position === index ? 'border-brand-500' : 'border-stone-200 hover:border-stone-400')}>
              <ProductImage src={image.thumbnailUrl || image.url} alt="" className="h-full w-full" />
            </button>
          ))}
        </div>
      )}
      <div className="relative aspect-square flex-1 overflow-hidden rounded-2xl border border-stone-200 bg-white">
        <ProductImage src={current?.url} alt={current?.altText || name} eager className="h-full w-full" />
      </div>
    </div>
  );
}
