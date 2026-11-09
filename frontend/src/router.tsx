import type { ComponentType } from 'react';
import { createBrowserRouter } from 'react-router';
import { AppRoot } from '@/app/AppRoot';
import { Spinner } from '@/components/ui/Feedback';
import { AccountLayout } from '@/features/account/AccountLayout';
import { AdminLayout } from '@/features/admin/AdminLayout';
import { GuestOnly, RequireAdmin, RequireAuth } from '@/features/auth/guards';
import { StoreLayout } from '@/features/layout/StoreLayout';
import NotFoundPage from '@/pages/NotFoundPage';
import { RouteErrorPage } from '@/pages/RouteErrorPage';

type PageModule = { default: ComponentType };

/** Code-split route: the page's module is downloaded on first visit. */
function page(load: () => Promise<PageModule>) {
  return async () => ({ Component: (await load()).default });
}

export const router = createBrowserRouter([
  {
    element: <AppRoot />,
    errorElement: <RouteErrorPage />,
    // Shown only while the first page's code downloads.
    hydrateFallbackElement: <Spinner className="min-h-screen" label="Loading ShopSmart" />,
    children: [
      {
        element: <StoreLayout />,
        children: [
          {
            errorElement: <RouteErrorPage />,
            children: [
              { index: true, lazy: page(() => import('@/features/catalog/HomePage')) },
              { path: 'shop', lazy: page(() => import('@/features/catalog/ShopPage')) },
              { path: 'categories/:slug', lazy: page(() => import('@/features/catalog/ShopPage')) },
              { path: 'products/:slug', lazy: page(() => import('@/features/catalog/ProductPage')) },
              { path: 'search', lazy: page(() => import('@/features/search/SearchPage')) },
              {
                element: <GuestOnly />,
                children: [
                  { path: 'login', lazy: page(() => import('@/features/auth/LoginPage')) },
                  { path: 'register', lazy: page(() => import('@/features/auth/RegisterPage')) },
                  { path: 'forgot-password', lazy: page(() => import('@/features/auth/ForgotPasswordPage')) },
                ],
              },
              { path: 'reset-password', lazy: page(() => import('@/features/auth/ResetPasswordPage')) },
              { path: 'verify-email', lazy: page(() => import('@/features/auth/VerifyEmailPage')) },
              {
                element: <RequireAuth />,
                children: [
                  { path: 'cart', lazy: page(() => import('@/features/cart/CartPage')) },
                  { path: 'checkout', lazy: page(() => import('@/features/checkout/CheckoutPage')) },
                  {
                    element: <AccountLayout />,
                    children: [
                      { path: 'orders', lazy: page(() => import('@/features/orders/OrdersPage')) },
                      { path: 'orders/:orderId', lazy: page(() => import('@/features/orders/OrderDetailPage')) },
                      { path: 'wishlist', lazy: page(() => import('@/features/wishlist/WishlistPage')) },
                      { path: 'addresses', lazy: page(() => import('@/features/account/AddressesPage')) },
                      { path: 'notifications', lazy: page(() => import('@/features/notifications/NotificationsPage')) },
                      { path: 'profile', lazy: page(() => import('@/features/account/ProfilePage')) },
                    ],
                  },
                ],
              },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
      {
        path: 'admin',
        element: <RequireAdmin><AdminLayout /></RequireAdmin>,
        children: [
          {
            errorElement: <RouteErrorPage />,
            children: [
              { index: true, lazy: page(() => import('@/features/admin/DashboardPage')) },
              { path: 'orders', lazy: page(() => import('@/features/admin/orders/AdminOrdersPage')) },
              { path: 'orders/:orderId', lazy: page(() => import('@/features/admin/orders/AdminOrderDetailPage')) },
              { path: 'products', lazy: page(() => import('@/features/admin/products/AdminProductsPage')) },
              { path: 'products/new', lazy: page(() => import('@/features/admin/products/AdminProductEditorPage')) },
              { path: 'products/:productId', lazy: page(() => import('@/features/admin/products/AdminProductEditorPage')) },
              { path: 'catalog', lazy: page(() => import('@/features/admin/catalog/AdminCatalogPage')) },
              { path: 'inventory', lazy: page(() => import('@/features/admin/inventory/AdminInventoryPage')) },
              { path: 'customers', lazy: page(() => import('@/features/admin/customers/AdminCustomersPage')) },
              { path: 'payments', lazy: page(() => import('@/features/admin/payments/AdminPaymentsPage')) },
              { path: 'coupons', lazy: page(() => import('@/features/admin/coupons/AdminCouponsPage')) },
              { path: 'reviews', lazy: page(() => import('@/features/admin/reviews/AdminReviewsPage')) },
              { path: 'analytics', lazy: page(() => import('@/features/admin/analytics/AdminAnalyticsPage')) },
              { path: 'settings', lazy: page(() => import('@/features/admin/settings/AdminSettingsPage')) },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
]);
