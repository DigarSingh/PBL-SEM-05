// Payloads pushed by the backend over STOMP (see com.shopsmart.realtime).

export interface NotificationMessage {
  id: number;
  type: string;
  title: string;
  message: string;
  link: string | null;
  createdAt: string;
}

export interface OrderUpdateMessage {
  type: 'ORDER_PLACED' | 'ORDER_STATUS_CHANGED' | 'PAYMENT_FAILED' | 'PAYMENT_REFUNDED' | string;
  orderId: number;
  orderNumber: string;
  status: string;
  statusLabel: string;
  paymentStatus: string | null;
  message: string;
  at: string;
}

export interface InventoryAlertMessage {
  variantId: number;
  productId: number;
  productName: string;
  sku: string;
  available: number;
  threshold: number;
  at: string;
}
