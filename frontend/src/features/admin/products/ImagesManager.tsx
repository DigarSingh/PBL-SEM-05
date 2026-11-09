import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, ImagePlus, Link2, Trash2, Upload } from 'lucide-react';
import { useRef, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { ProductImage } from '@/components/ProductImage';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { ConfirmDialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Field';
import { errorMessage } from '@/lib/errors';
import type { AdminProduct, ProductImage as ProductImageData } from '@/lib/types';
import { adminApi } from '../api';

const MAX_BYTES = 5 * 1024 * 1024;
// Must match the backend's ImageProcessor, which decodes and accepts JPEG and PNG only.
const TYPES = ['image/jpeg', 'image/png'];

export function ImagesManager({ product }: { product: AdminProduct }) {
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [url, setUrl] = useState('');
  const [deleting, setDeleting] = useState<ProductImageData | null>(null);
  const images = [...product.images].sort((a, b) => a.sortOrder - b.sortOrder);
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] }),
    queryClient.invalidateQueries({ queryKey: ['product'] }),
  ]);

  const upload = useMutation({
    mutationFn: (files: File[]) => adminApi.uploadImages(product.id, files),
    onSuccess: async (added) => {
      await refresh();
      toast.success(`${added.length} image${added.length === 1 ? '' : 's'} uploaded`);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const addUrl = useMutation({
    mutationFn: (value: string) => adminApi.addImageUrl(product.id, value, null),
    onSuccess: async () => {
      setUrl('');
      await refresh();
      toast.success('Image added');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const reorder = useMutation({
    mutationFn: (ids: number[]) => adminApi.reorderImages(product.id, ids),
    onSuccess: () => refresh(),
    onError: (error) => toast.error(errorMessage(error)),
  });
  const saveAlt = useMutation({
    mutationFn: ({ image, altText }: { image: ProductImageData; altText: string }) => adminApi.updateImageAlt(product.id, image.id, altText.trim() || null),
    onSuccess: async () => {
      await refresh();
      toast.success('Alt text saved');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: (image: ProductImageData) => adminApi.deleteImage(product.id, image.id),
    onSuccess: async () => {
      setDeleting(null);
      await refresh();
      toast.success('Image deleted');
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const pickFiles = (list: FileList | null) => {
    const files = Array.from(list ?? []);
    const rejected = files.filter((file) => !TYPES.includes(file.type) || file.size > MAX_BYTES);
    if (rejected.length) {
      toast.error(`${rejected.map((file) => file.name).join(', ')}: use JPEG or PNG images up to 5 MB.`);
    }
    const accepted = files.filter((file) => !rejected.includes(file));
    if (accepted.length) {
      upload.mutate(accepted);
    }
    if (fileInput.current) {
      fileInput.current.value = '';
    }
  };

  const move = (index: number, direction: -1 | 1) => {
    const ids = images.map((image) => image.id);
    const target = index + direction;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    reorder.mutate(ids);
  };

  const submitUrl = (event: FormEvent) => {
    event.preventDefault();
    if (url.trim()) {
      addUrl.mutate(url.trim());
    }
  };

  return (
    <Card>
      <CardHeader title="Images" description="The first image is the product's main photo. Upload JPEG or PNG files up to 5 MB, or add an https link." />
      <div className="space-y-4 p-5">
        <div className="flex flex-wrap gap-2">
          <input ref={fileInput} type="file" accept={TYPES.join(',')} multiple className="sr-only" id="product-images"
            onChange={(event) => pickFiles(event.target.files)} />
          <Button variant="secondary" icon={<Upload className="h-4 w-4" aria-hidden />} loading={upload.isPending} onClick={() => fileInput.current?.click()}>
            Upload images
          </Button>
          <form onSubmit={submitUrl} className="flex min-w-64 flex-1 gap-2">
            <label htmlFor="image-url" className="sr-only">Image URL</label>
            <Input id="image-url" type="url" placeholder="https://images.example.com/photo.jpg" value={url} onChange={(event) => setUrl(event.target.value)} />
            <Button type="submit" variant="secondary" icon={<Link2 className="h-4 w-4" aria-hidden />} loading={addUrl.isPending} disabled={!url.trim()}>Add</Button>
          </form>
        </div>
        {images.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-stone-300 py-10 text-sm text-slate-500">
            <ImagePlus className="h-6 w-6" aria-hidden />No images yet. Products without images show a placeholder in the store.
          </div>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {images.map((image, index) => (
              <li key={image.id} className="overflow-hidden rounded-xl border border-stone-200 bg-white">
                <div className="relative aspect-video bg-stone-100">
                  <ProductImage src={image.thumbnailUrl || image.url} alt={image.altText ?? ''} className="h-full w-full" />
                  {index === 0 && <Badge tone="brand" className="absolute left-2 top-2">Main</Badge>}
                </div>
                <div className="space-y-2 p-3">
                  <AltTextField image={image} saving={saveAlt.isPending && saveAlt.variables?.image.id === image.id}
                    onSave={(altText) => saveAlt.mutate({ image, altText })} />
                  <div className="flex items-center justify-between">
                    <div className="flex gap-1">
                      <Button size="icon" variant="ghost" className="h-8 w-8" disabled={index === 0 || reorder.isPending} aria-label="Move earlier" onClick={() => move(index, -1)}>
                        <ArrowUp className="h-4 w-4" aria-hidden />
                      </Button>
                      <Button size="icon" variant="ghost" className="h-8 w-8" disabled={index === images.length - 1 || reorder.isPending} aria-label="Move later" onClick={() => move(index, 1)}>
                        <ArrowDown className="h-4 w-4" aria-hidden />
                      </Button>
                    </div>
                    <Button size="icon" variant="ghost" className="h-8 w-8 text-rose-700" aria-label="Delete image" onClick={() => setDeleting(image)}>
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} title="Delete this image?" message="It will be removed from the product."
        confirmLabel="Delete" loading={remove.isPending} onConfirm={() => {
          if (deleting) {
            remove.mutate(deleting);
          }
        }} />
    </Card>
  );
}

function AltTextField({ image, saving, onSave }: { image: ProductImageData; saving: boolean; onSave: (altText: string) => void }) {
  const [value, setValue] = useState(image.altText ?? '');
  const dirty = value !== (image.altText ?? '');
  return (
    <form className="flex gap-2" onSubmit={(event) => {
      event.preventDefault();
      onSave(value);
    }}>
      <label className="sr-only" htmlFor={`alt-${image.id}`}>Alt text</label>
      <Input id={`alt-${image.id}`} value={value} maxLength={255} placeholder="Describe the image (alt text)" onChange={(event) => setValue(event.target.value)} className="h-8 text-xs" />
      {dirty && <Button type="submit" size="sm" variant="secondary" loading={saving}>Save</Button>}
    </form>
  );
}
