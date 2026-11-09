/** Accepts only same-origin relative paths as post-login destinations (prevents open redirects). */
export function safeRedirect(target: string | null | undefined, fallback = '/'): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return fallback;
  }
  return target;
}

/** Sign-in page URL that returns the user to `returnTo` afterwards. */
export function loginPath(returnTo?: string): string {
  return returnTo && returnTo !== '/' ? `/login?redirect=${encodeURIComponent(returnTo)}` : '/login';
}
