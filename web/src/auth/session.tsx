import { useEffect, useMemo, useRef } from 'react';
import { useAuth } from 'react-oidc-context';
import { setTokenSource } from '../api/client';
import { rolesFromToken } from './token';

export type Session = {
  ready: boolean;
  loggedIn: boolean;
  name: string | null;
  roles: string[];
  isAdmin: boolean;
  login: (returnTo?: string) => void;
  logout: () => void;
};

const here = () => window.location.pathname + window.location.search;

export function useSession(): Session {
  const auth = useAuth();
  const token = auth.user?.access_token ?? null;
  return useMemo(() => {
    const roles = token ? rolesFromToken(token) : [];
    const profile = auth.user?.profile;
    return {
      ready: !auth.isLoading,
      loggedIn: auth.isAuthenticated,
      name: (profile?.given_name as string | undefined) ?? (profile?.preferred_username as string | undefined) ?? null,
      roles,
      isAdmin: roles.includes('ADMIN'),
      login: (returnTo = here()) => void auth.signinRedirect({ state: { returnTo } }),
      logout: () => void auth.signoutRedirect(),
    };
  }, [auth, token]);
}

/** Hands the API client the current token and a way to renew it after a 401. */
export function TokenBridge() {
  const auth = useAuth();
  const authRef = useRef(auth);
  useEffect(() => {
    authRef.current = auth;
  });
  useEffect(() => {
    setTokenSource({
      get: () => authRef.current.user?.access_token ?? null,
      renew: async () => {
        try {
          const user = await authRef.current.signinSilent();
          if (user?.access_token) return user.access_token;
        } catch {
          // refresh token gone or expired: log in again below
        }
        await authRef.current.signinRedirect({ state: { returnTo: here() } });
        return null;
      },
    });
  }, []);
  return null;
}
