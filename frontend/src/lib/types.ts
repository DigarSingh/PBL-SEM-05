// API types mirroring the backend DTOs. Money values are JSON numbers (INR, two decimals).

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type Role = 'CUSTOMER' | 'ADMIN';

export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
  roles: Role[];
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserProfile;
}

export interface Ref {
  id: number;
  name: string;
  slug: string;
}

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export interface ProductSummary {
  id: number;
  name: string;
  slug: string;
  brand: Ref;
  category: Ref;
  imageUrl: string | null;
  thumbnailUrl: string | null;
  price: number;
  salePrice: number;
  discountPercent: number;
  averageRating: number;
  reviewCount: number;
  inStock: boolean;
  stockStatus: StockStatus;
  lowStockQuantity: number | null;
  featured: boolean;
  defaultVariantId: number | null;
  variantCount: number;
}

export interface ProductImage {
  id: number;
  url: string;
  thumbnailUrl: string;
  altText: string | null;
  sortOrder: number;
  width: number | null;
  height: number | null;
}

export interface Specification {
  name: string;
  value: string;
}

export interface Variant {
  id: number;
  sku: string;
  name: string;
  attributes: Record<string, string>;
  price: number;
  salePrice: number;
  inStock: boolean;
  stockStatus: StockStatus;
  lowStockQuantity: number | null;
}

export interface VariantOption {
  name: string;
  values: string[];
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  brand: Ref;
  category: Ref;
  shortDescription: string | null;
  description: string;
  price: number;
  salePrice: number;
  discountPercent: number;
  averageRating: number;
  reviewCount: number;
  soldCount: number;
  featured: boolean;
  inStock: boolean;
  stockStatus: StockStatus;
  tags: string[];
  specifications: Specification[];
  images: ProductImage[];
  variants: Variant[];
  options: VariantOption[];
  defaultVariantId: number | null;
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  imageUrl: string | null;
  sortOrder: number;
  active: boolean;
  productCount: number;
}

export interface Brand {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  logoUrl: string | null;
  active: boolean;
  productCount: number;
}

export interface Facets {
  brands: { id: number; name: string; slug: string; count: number }[];
  minPrice: number | null;
  maxPrice: number | null;
}

export interface PriceQuote {
  itemCount: number;
  mrpTotal: number;
  subtotal: number;
  productSavings: number;
  couponDiscount: number;
  shippingFee: number;
  freeShippingThreshold: number;
  amountToFreeShipping: number;
  taxRate: number;
  taxInclusive: boolean;
  taxAmount: number;
  total: number;
  currency: string;
}

export type CartIssue = 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK' | 'UNAVAILABLE';

export interface CartItem {
  id: number;
  variantId: number;
  productId: number;
  productName: string;
  productSlug: string;
  brandName: string;
  variantName: string;
  sku: string;
  attributes: Record<string, string>;
  imageUrl: string | null;
  unitMrp: number;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  available: number;
  maxQuantity: number;
  stockStatus: StockStatus;
  issue: CartIssue | null;
}

export interface Cart {
  items: CartItem[];
  summary: PriceQuote;
  coupon: { code: string; valid: boolean; message: string } | null;
  canCheckout: boolean;
  issues: string[];
}

export interface Offer {
  code: string;
  description: string | null;
  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  minOrderAmount: number | null;
  maxDiscountAmount: number | null;
  expiresAt: string | null;
}

export interface Wishlist {
  items: { product: ProductSummary; addedAt: string }[];
  count: number;
}

export interface Address {
  id: number;
  label: string | null;
  fullName: string;
  phone: string;
  line1: string;
  line2: string | null;
  landmark: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PLACED'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED';

export type PaymentMethod = 'COD' | 'RAZORPAY';
export type PaymentStatus = 'PENDING' | 'AUTHORIZED' | 'PAID' | 'FAILED' | 'REFUNDED' | 'CANCELLED';

export interface PaymentInfo {
  id: number;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  currency: string;
  providerMethod: string | null;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  failureReason: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  refundedAmount: number | null;
  refundPending: boolean;
}

export interface OrderItem {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  variantName: string;
  sku: string;
  imageUrl: string | null;
  unitMrp: number;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface TimelineStep {
  status: OrderStatus;
  label: string;
  reached: boolean;
  at: string | null;
}

export interface StatusHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  note: string | null;
  changedByUserId: number | null;
  changedBy: string | null;
  at: string;
}

export interface ShippingAddress {
  fullName: string;
  phone: string;
  line1: string;
  line2: string | null;
  landmark: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus | null;
  totalAmount: number;
  itemCount: number;
  firstItemName: string | null;
  firstItemImage: string | null;
  createdAt: string;
  placedAt: string | null;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  payment: PaymentInfo | null;
  items: OrderItem[];
  mrpTotal: number;
  subtotal: number;
  couponCode: string | null;
  couponDiscount: number;
  shippingFee: number;
  taxRate: number;
  taxInclusive: boolean;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  shippingAddress: ShippingAddress;
  customerNote: string | null;
  estimatedDeliveryDate: string | null;
  createdAt: string;
  placedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  timeline: TimelineStep[];
  history: StatusHistoryEntry[];
  canCancel: boolean;
  canPay: boolean;
  paymentDeadline: string | null;
  customer: { id: number; fullName: string; email: string; phone: string | null } | null;
  allowedTransitions: OrderStatus[];
}

export interface RazorpayCheckout {
  keyId: string;
  razorpayOrderId: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  orderId: number;
  orderNumber: string;
  prefill: { name: string; email: string; contact: string | null };
}

export interface CheckoutResponse {
  order: OrderDetail;
  razorpay: RazorpayCheckout | null;
  replayed: boolean;
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  message: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface Review {
  id: number;
  rating: number;
  title: string;
  comment: string | null;
  authorName: string;
  verifiedPurchase: boolean;
  mine: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewSummary {
  averageRating: number;
  reviewCount: number;
  distribution: Record<string, number>;
}

export interface ReviewEligibility {
  canReview: boolean;
  reason: string | null;
  existingReview: Review | null;
}

export interface SearchInterpretation {
  category: string | null;
  categorySlug: string | null;
  brand: string | null;
  minPrice: number | null;
  maxPrice: number | null;
  minRating: number | null;
  inStockOnly: boolean;
  features: string[];
  useCases: string[];
  filtersRelaxed: boolean;
}

export interface SmartSearchResponse {
  query: string;
  interpretation: SearchInterpretation;
  engine: 'semantic' | 'keyword';
  items: { product: ProductSummary; score: number; matchReasons: string[] }[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SearchSuggestions {
  products: { id: number; name: string; slug: string; imageUrl: string | null }[];
  categories: { id: number; name: string; slug: string; imageUrl: string | null }[];
  brands: { id: number; name: string; slug: string; imageUrl: string | null }[];
  recentSearches: string[];
}

export interface Recommendations {
  strategy: string;
  items: { product: ProductSummary; reason: string }[];
}

export interface PublicSettings {
  storeName: string;
  supportEmail: string;
  currency: string;
  taxRate: number;
  pricesIncludeTax: boolean;
  shippingFee: number;
  freeShippingThreshold: number;
  codEnabled: boolean;
  codMaxOrderAmount: number | null;
  onlinePaymentsEnabled: boolean;
  razorpayKeyId: string | null;
}

// ---- Admin ------------------------------------------------------------------------------

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export interface AdminProductSummary {
  id: number;
  name: string;
  slug: string;
  imageUrl: string | null;
  brand: Ref;
  category: Ref;
  status: ProductStatus;
  featured: boolean;
  price: number;
  salePrice: number;
  discountPercent: number;
  totalAvailable: number;
  stockStatus: StockStatus;
  variantCount: number;
  soldCount: number;
  averageRating: number;
  updatedAt: string;
}

export interface AdminVariant {
  id: number;
  sku: string;
  name: string;
  attributes: Record<string, string>;
  priceOverride: number | null;
  listPrice: number;
  salePrice: number;
  active: boolean;
  quantity: number;
  reserved: number;
  available: number;
  lowStockThreshold: number;
  hasOrders: boolean;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  brand: Ref;
  category: Ref;
  shortDescription: string | null;
  description: string;
  price: number;
  discountPercent: number;
  salePrice: number;
  status: ProductStatus;
  featured: boolean;
  averageRating: number;
  reviewCount: number;
  soldCount: number;
  tags: string[];
  specifications: Specification[];
  images: ProductImage[];
  variants: AdminVariant[];
  createdAt: string;
  updatedAt: string;
}

export interface InventoryItem {
  variantId: number;
  sku: string;
  variantName: string;
  productId: number;
  productName: string;
  categoryName: string;
  variantActive: boolean;
  quantity: number;
  reserved: number;
  available: number;
  lowStockThreshold: number;
  stockStatus: StockStatus;
  updatedAt: string;
}

export interface StockMovement {
  id: number;
  type: string;
  quantityChange: number;
  reservedChange: number;
  quantityAfter: number;
  reservedAfter: number;
  orderId: number | null;
  actorUserId: number | null;
  note: string | null;
  createdAt: string;
}

export interface AdminOrderSummary {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  itemCount: number;
  createdAt: string;
  placedAt: string | null;
}

export interface AdminPayment {
  id: number;
  orderId: number;
  orderNumber: string;
  orderStatus: OrderStatus;
  customerName: string;
  customerEmail: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  currency: string;
  providerMethod: string | null;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  failureReason: string | null;
  refundId: string | null;
  refundedAmount: number | null;
  refundPending: boolean;
  paidAt: string | null;
  createdAt: string;
}

export interface AdminCustomerSummary {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  status: 'ACTIVE' | 'DISABLED';
  emailVerified: boolean;
  orders: number;
  totalSpent: number;
  lastOrderAt: string | null;
  createdAt: string;
}

export interface AdminCustomerDetail {
  customer: AdminCustomerSummary;
  addresses: Address[];
  recentOrders: OrderSummary[];
  lastLoginAt: string | null;
}

export interface Coupon {
  id: number;
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
  usedCount: number;
  active: boolean;
  createdAt: string;
}

export interface AdminReview {
  id: number;
  productId: number;
  productName: string;
  userId: number;
  userName: string;
  userEmail: string;
  rating: number;
  title: string;
  comment: string | null;
  status: 'PUBLISHED' | 'HIDDEN';
  moderationNote: string | null;
  createdAt: string;
}

export interface AuditLog {
  id: number;
  actorUserId: number | null;
  actorEmail: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface StoreSettings {
  storeName: string;
  supportEmail: string;
  currency: string;
  taxRate: number;
  pricesIncludeTax: boolean;
  shippingFee: number;
  freeShippingThreshold: number;
  defaultLowStockThreshold: number;
  codEnabled: boolean;
  codMaxOrderAmount: number | null;
  razorpayEnabled: boolean;
  razorpayConfigured: boolean;
  pendingPaymentTimeoutMinutes: number;
  updatedAt: string;
}

export interface SeriesPoint {
  period: string;
  revenue: number;
  orders: number;
  units: number;
}

export interface Dashboard {
  totals: {
    totalSales: number;
    orders: number;
    customers: number;
    products: number;
    lowStockProducts: number;
    pendingOrders: number;
    completedOrders: number;
  };
  last30Days: {
    revenue: number;
    revenueChange: number | null;
    orders: number;
    ordersChange: number | null;
    averageOrderValue: number;
    averageOrderValueChange: number | null;
    newCustomers: number;
    newCustomersChange: number | null;
  };
  salesLast30Days: SeriesPoint[];
  orderStatusDistribution: Record<string, number>;
  recentOrders: AdminOrderSummary[];
  lowStock: InventoryItem[];
}

export interface SalesAnalytics {
  from: string;
  to: string;
  granularity: string;
  revenue: number;
  orders: number;
  units: number;
  averageOrderValue: number;
  revenueChange: number | null;
  series: SeriesPoint[];
  orderStatusDistribution: Record<string, number>;
  paymentMethods: { method: string; orders: number; revenue: number }[];
}

export interface ProductRow {
  productId: number;
  name: string;
  units: number;
  revenue: number;
  views: number;
  conversionRate: number | null;
  available: number | null;
}

export interface ProductAnalytics {
  from: string;
  to: string;
  topProducts: ProductRow[];
  slowMoving: ProductRow[];
}

export interface CategoryAnalytics {
  from: string;
  to: string;
  /** revenueShare is a fraction of the period's revenue (0-1). */
  categories: { categoryId: number; name: string; units: number; revenue: number; orders: number; revenueShare: number }[];
}

export interface CustomerAnalytics {
  from: string;
  to: string;
  totalCustomers: number;
  newCustomers: number;
  purchasingCustomers: number;
  repeatPurchaseRate: number | null;
  newCustomersByDay: { day: string; count: number }[];
  topCustomers: { userId: number; name: string; email: string; orders: number; spent: number }[];
}

export interface InventoryAnalytics {
  products: {
    productId: number;
    name: string;
    available: number;
    unitsLast30Days: number;
    dailyVelocity: number;
    daysOfCover: number | null;
    status: string;
  }[];
}

export interface Insights {
  from: string;
  to: string;
  historical: { type: string; severity: string; title: string; message: string; productId: number | null; metric: number | null }[];
  predictions: {
    available: boolean;
    status: string;
    model: string | null;
    horizonDays: number | null;
    leadTimeDays: number | null;
    generatedAt: string;
    message: string;
    items: {
      productId: number;
      name: string | null;
      status: string;
      predictedUnits: number | null;
      lower: number | null;
      upper: number | null;
      dailyRate: number | null;
      trend: string | null;
      available: number | null;
      daysOfCover: number | null;
      restockQuantity: number | null;
      note: string | null;
    }[];
  };
}
