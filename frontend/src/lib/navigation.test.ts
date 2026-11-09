import { describe, expect, it } from 'vitest';
import { loginPath, safeRedirect } from './navigation';

describe('safeRedirect', () => {
  it('keeps same-origin relative paths', () => {
    expect(safeRedirect('/orders/12?placed=1')).toBe('/orders/12?placed=1');
  });

  it.each(['https://evil.example', '//evil.example/path', '/\\evil.example', 'javascript:alert(1)', '', null, undefined])(
    'rejects %s (open redirect)', (target) => {
      expect(safeRedirect(target, '/fallback')).toBe('/fallback');
    });
});

describe('loginPath', () => {
  it('encodes the return path', () => {
    expect(loginPath('/checkout?step=2')).toBe('/login?redirect=%2Fcheckout%3Fstep%3D2');
  });

  it('omits the home page as a return target', () => {
    expect(loginPath('/')).toBe('/login');
  });
});
