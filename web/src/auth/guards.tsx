import { useEffect, type ReactNode } from 'react';
import { useSession } from './session';

function Waiting({ text }: { text: string }) {
  return (
    <div className="state" role="status">
      <p>{text}…</p>
    </div>
  );
}

/** Logged-in users only; everyone else is sent to the login page and brought back here. */
export function RequireLogin({ children }: { children: ReactNode }) {
  const session = useSession();
  const { ready, loggedIn, login } = session;
  useEffect(() => {
    if (ready && !loggedIn) login();
  }, [ready, loggedIn, login]);
  if (!ready || !loggedIn) return <Waiting text="Redirecting to sign in" />;
  return <>{children}</>;
}

/** A page for one role. Hides the page only: the API still checks the role on every call. */
export function RequireRole({ role, children }: { role: string; children: ReactNode }) {
  const session = useSession();
  if (!session.ready) return <Waiting text="Loading" />;
  if (!session.loggedIn) return <RequireLogin>{children}</RequireLogin>;
  if (!session.roles.includes(role)) {
    return (
      <div className="state">
        <h1 className="state-title">You don’t have access to this page</h1>
        <p>Admin pages need an admin account.</p>
      </div>
    );
  }
  return <>{children}</>;
}
