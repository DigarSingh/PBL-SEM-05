import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { session } from './lib/session';
import './index.css';

// Restore a signed-in session from the HttpOnly refresh cookie as early as possible.
void session.restore();

const container = document.getElementById('root');
if (!container) {
  throw new Error('The #root element is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
