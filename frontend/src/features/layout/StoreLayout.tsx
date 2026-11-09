import { Outlet } from 'react-router';
import { ConnectionBanner } from './ConnectionBanner';
import { Footer } from './Footer';
import { Header } from './Header';

export function StoreLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only z-50 rounded-lg bg-white px-4 py-2 focus:not-sr-only focus:fixed focus:left-4 focus:top-4">
        Skip to content
      </a>
      <Header />
      <ConnectionBanner />
      <main id="main" className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
