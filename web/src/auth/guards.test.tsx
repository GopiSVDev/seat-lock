import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { RequireLogin, RequireRole } from './guards';
import { rolesFromToken } from './token';

const auth = vi.hoisted(() => ({ current: {} as Record<string, unknown> }));
vi.mock('react-oidc-context', () => ({ useAuth: () => auth.current }));

const tokenWith = (roles: string[]) => `h.${btoa(JSON.stringify({ realm_access: { roles } })).replace(/=+$/, '')}.s`;
const signinRedirect = vi.fn().mockResolvedValue(undefined);
const as = (roles: string[] | null) => {
  auth.current = {
    isLoading: false,
    isAuthenticated: roles !== null,
    user: roles ? { access_token: tokenWith(roles), profile: { given_name: 'Asha' } } : null,
    signinRedirect,
    signoutRedirect: vi.fn(),
  };
};

afterEach(() => signinRedirect.mockClear());

describe('auth guard', () => {
  it('opens admin pages for ADMIN', () => {
    as(['CUSTOMER', 'ADMIN']);
    render(<RequireRole role="ADMIN">Admin pages</RequireRole>);
    expect(screen.getByText('Admin pages')).toBeInTheDocument();
  });

  it('keeps customers out of admin pages', () => {
    as(['CUSTOMER']);
    render(<RequireRole role="ADMIN">Admin pages</RequireRole>);
    expect(screen.queryByText('Admin pages')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /don’t have access/ })).toBeInTheDocument();
  });

  it('sends a logged-out visitor to log in and back to the same page', () => {
    as(null);
    window.history.pushState({}, '', '/bookings?period=PAST');
    render(<RequireLogin>My bookings</RequireLogin>);
    expect(screen.queryByText('My bookings')).not.toBeInTheDocument();
    expect(signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/bookings?period=PAST' } });
  });

  it('reads realm roles from the access token and survives junk', () => {
    expect(rolesFromToken(tokenWith(['ADMIN']))).toEqual(['ADMIN']);
    expect(rolesFromToken('not-a-token')).toEqual([]);
  });
});
