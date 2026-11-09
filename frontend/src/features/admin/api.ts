import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/lib/api';
import type {
  AdminCustomerDetail, AdminCustomerSummary, AdminOrderSummary, AdminPayment, AdminProduct, AdminProductSummary,
  AdminReview, AuditLog, Brand, Category, CategoryAnalytics, Coupon, CustomerAnalytics, Dashboard, InventoryAnalytics,
  InventoryItem, Insights, OrderDetail, OrderStatus, Page, PaymentMethod, PaymentStatus, ProductAnalytics,
  ProductImage, ProductStatus, SalesAnalytics, StockMovement, StoreSettings,
} from '@/lib/types';

export type AdminProductQuery = {
  q?: string;
  status?: ProductStatus;
  categoryId?: number;
  brandId?: number;
  inStock?: boolean;
  sort?: string;
  page?: number;
  size?: number;
};

export interface VariantInput {
  id: number | null;
  sku: string;
  name: string;
  attributes: Record<string, string>;
  priceOverride: number | null;
  active: boolean;
  initialStock: number | null;
  lowStockThreshold: number | null;
}

export interface ProductInput {
  name: string;
  slug: string | null;
  brandId: number;
  categoryId: number;
  shortDescription: string | null;
  description: string;
  price: number;
  discountPercent: number;
  status: ProductStatus;
  featured: boolean;
  tags: string[];
  specifications: { name: string; value: string }[];
  variants: VariantInput[];
}

export interface CategoryInput {
  name: string;
  slug: string | null;
  description: string | null;
  imageUrl: string | null;
  sortOrder: number | null;
  active: boolean;
}

export interface BrandInput {
  name: string;
  slug: string | null;
  description: string | null;
  logoUrl: string | null;
  active: boolean;
}

export type AdminOrderQuery = {
  status?: OrderStatus;
  paymentStatus?: PaymentStatus;
  paymentMethod?: PaymentMethod;
  includePending?: boolean;
  q?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
};

export interface CouponInput {
  code: string;
  description: string | null;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderAmount: number | null;
  maxDiscountAmount: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  active: boolean;
}

export type SettingsInput = Omit<StoreSettings, 'currency' | 'razorpayConfigured' | 'updatedAt'>;

export const adminApi = {
  dashboard: () => apiGet<Dashboard>('/admin/dashboard'),

  products: (query: AdminProductQuery) => apiGet<Page<AdminProductSummary>>('/admin/products', query),
  product: (productId: number) => apiGet<AdminProduct>(`/admin/products/${productId}`),
  createProduct: (input: ProductInput) => apiPost<AdminProduct>('/admin/products', input),
  updateProduct: (productId: number, input: ProductInput) => apiPut<AdminProduct>(`/admin/products/${productId}`, input),
  setProductStatus: (productId: number, status: ProductStatus) => apiPatch<AdminProduct>(`/admin/products/${productId}/status`, { status }),
  archiveProduct: (productId: number) => apiDelete<void>(`/admin/products/${productId}`),
  uploadImages: (productId: number, files: File[]) => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file));
    return apiPost<ProductImage[]>(`/admin/products/${productId}/images`, form);
  },
  addImageUrl: (productId: number, url: string, altText: string | null) =>
    apiPost<ProductImage>(`/admin/products/${productId}/images/url`, { url, altText }),
  reorderImages: (productId: number, imageIds: number[]) => apiPut<ProductImage[]>(`/admin/products/${productId}/images/order`, { imageIds }),
  updateImageAlt: (productId: number, imageId: number, altText: string | null) =>
    apiPatch<ProductImage>(`/admin/products/${productId}/images/${imageId}`, { altText }),
  deleteImage: (productId: number, imageId: number) => apiDelete<void>(`/admin/products/${productId}/images/${imageId}`),

  categories: () => apiGet<Category[]>('/admin/categories'),
  createCategory: (input: CategoryInput) => apiPost<Category>('/admin/categories', input),
  updateCategory: (categoryId: number, input: CategoryInput) => apiPut<Category>(`/admin/categories/${categoryId}`, input),
  deleteCategory: (categoryId: number) => apiDelete<void>(`/admin/categories/${categoryId}`),
  brands: () => apiGet<Brand[]>('/admin/brands'),
  createBrand: (input: BrandInput) => apiPost<Brand>('/admin/brands', input),
  updateBrand: (brandId: number, input: BrandInput) => apiPut<Brand>(`/admin/brands/${brandId}`, input),
  deleteBrand: (brandId: number) => apiDelete<void>(`/admin/brands/${brandId}`),

  orders: (query: AdminOrderQuery) => apiGet<Page<AdminOrderSummary>>('/admin/orders', query),
  order: (orderId: number) => apiGet<OrderDetail>(`/admin/orders/${orderId}`),
  updateOrderStatus: (orderId: number, status: OrderStatus, note: string | null) =>
    apiPatch<OrderDetail>(`/admin/orders/${orderId}/status`, { status, note }),
  cancelOrder: (orderId: number, reason: string | null) => apiPost<OrderDetail>(`/admin/orders/${orderId}/cancel`, { reason }),

  payments: (query: { status?: PaymentStatus; method?: PaymentMethod; q?: string; page?: number; size?: number }) =>
    apiGet<Page<AdminPayment>>('/admin/payments', query),
  collectPayment: (paymentId: number) => apiPost<AdminPayment>(`/admin/payments/${paymentId}/collect`),
  retryRefund: (paymentId: number) => apiPost<AdminPayment>(`/admin/payments/${paymentId}/refund`),

  customers: (query: { q?: string; status?: 'ACTIVE' | 'DISABLED'; page?: number; size?: number }) =>
    apiGet<Page<AdminCustomerSummary>>('/admin/customers', query),
  customer: (customerId: number) => apiGet<AdminCustomerDetail>(`/admin/customers/${customerId}`),
  setCustomerStatus: (customerId: number, status: 'ACTIVE' | 'DISABLED') =>
    apiPatch<AdminCustomerSummary>(`/admin/customers/${customerId}/status`, { status }),

  inventory: (query: { q?: string; categoryId?: number; lowStock?: boolean; outOfStock?: boolean; page?: number; size?: number }) =>
    apiGet<Page<InventoryItem>>('/admin/inventory', query),
  adjustStock: (variantId: number, input: { type: 'RESTOCK' | 'ADJUSTMENT'; quantity: number; note: string | null }) =>
    apiPost<InventoryItem>(`/admin/inventory/${variantId}/adjustments`, input),
  setThreshold: (variantId: number, lowStockThreshold: number) =>
    apiPatch<InventoryItem>(`/admin/inventory/${variantId}`, { lowStockThreshold }),
  movements: (variantId: number, page: number) => apiGet<Page<StockMovement>>(`/admin/inventory/${variantId}/movements`, { page, size: 20 }),

  coupons: (query: { q?: string; active?: boolean; page?: number; size?: number }) => apiGet<Page<Coupon>>('/admin/coupons', query),
  createCoupon: (input: CouponInput) => apiPost<Coupon>('/admin/coupons', input),
  updateCoupon: (couponId: number, input: CouponInput) => apiPut<Coupon>(`/admin/coupons/${couponId}`, input),
  deleteCoupon: (couponId: number) => apiDelete<void>(`/admin/coupons/${couponId}`),

  reviews: (query: { status?: 'PUBLISHED' | 'HIDDEN'; rating?: number; productId?: number; q?: string; page?: number; size?: number }) =>
    apiGet<Page<AdminReview>>('/admin/reviews', query),
  moderateReview: (reviewId: number, status: 'PUBLISHED' | 'HIDDEN', note: string | null) =>
    apiPatch<AdminReview>(`/admin/reviews/${reviewId}`, { status, note }),

  settings: () => apiGet<StoreSettings>('/admin/settings'),
  updateSettings: (input: SettingsInput) => apiPut<StoreSettings>('/admin/settings', input),
  auditLogs: (query: { action?: string; entityType?: string; page?: number; size?: number }) => apiGet<Page<AuditLog>>('/admin/audit-logs', query),

  sales: (from: string, to: string, granularity: string) => apiGet<SalesAnalytics>('/admin/analytics/sales', { from, to, granularity }),
  productAnalytics: (from: string, to: string) => apiGet<ProductAnalytics>('/admin/analytics/products', { from, to, limit: 10 }),
  categoryAnalytics: (from: string, to: string) => apiGet<CategoryAnalytics>('/admin/analytics/categories', { from, to }),
  customerAnalytics: (from: string, to: string) => apiGet<CustomerAnalytics>('/admin/analytics/customers', { from, to }),
  inventoryAnalytics: () => apiGet<InventoryAnalytics>('/admin/analytics/inventory'),
  insights: (from: string, to: string) => apiGet<Insights>('/admin/analytics/insights', { from, to }),
};
