import { WebStorageStateStore, type User } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

export type LoginState = { returnTo?: string };

const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;

export const oidcConfig: AuthProviderProps = {
  // The issuer the backend trusts (JWT_ISSUER_URI), baked in at build time
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? '',
  client_id: 'seatlock-web',
  redirect_uri: `${origin}/auth/callback`,
  post_logout_redirect_uri: `${origin}/`,
  response_type: 'code',
  scope: 'openid profile',
  // Tokens live 5 minutes; renew with the refresh token a minute before they end
  automaticSilentRenew: true,
  accessTokenExpiringNotificationTimeInSeconds: 60,
  // A tab keeps its login across reloads but not forever; nothing in localStorage
  userStore: typeof window === 'undefined' ? undefined : new WebStorageStateStore({ store: window.sessionStorage }),
  onSigninCallback: (user: User | void) => {
    const returnTo = (user?.state as LoginState | undefined)?.returnTo ?? '/';
    window.history.replaceState({}, document.title, returnTo);
    window.dispatchEvent(new PopStateEvent('popstate'));
  },
};
