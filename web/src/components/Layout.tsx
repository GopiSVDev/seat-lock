import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useSession } from '../auth/session';
import { useCity } from '../hooks/useCity';
import { ChevronDown, PinIcon, SeatMark } from './Icons';

export function Layout() {
  const [city] = useCity();
  const location = useLocation();
  const returnTo = encodeURIComponent(location.pathname + location.search);

  return (
    <>
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <header className="site-header">
        <div className="container">
          <div className="header-bar">
            <Link to="/" className="logo" aria-label="SeatLock home">
              <span className="logo-mark">
                <SeatMark size={18} />
              </span>
              <span className="logo-word">seatlock</span>
            </Link>
            <Nav className="nav" />
            <div className="header-end">
              <Link to={`/city?returnTo=${returnTo}`} className="city-button" aria-label={city ? `City: ${city.name}. Change city` : 'Select city'} style={{ textDecoration: 'none' }}>
                <PinIcon size={17} />
                <span>{city ? city.name : 'Select city'}</span>
                <ChevronDown size={14} />
              </Link>
              <Account />
            </div>
          </div>
          <Nav className="subnav" />
        </div>
      </header>
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container">
          <span>© {new Date().getFullYear()} SeatLock</span>
          <span>Payments run in Razorpay test mode. No real money is charged.</span>
        </div>
      </footer>
    </>
  );
}

function Nav({ className }: { className: string }) {
  return (
    <nav className={className} aria-label={className === 'nav' ? 'Main' : 'Main (compact)'}>
      <NavLink to="/" end>
        Movies
      </NavLink>
      <NavLink to="/theatres">Cinemas</NavLink>
    </nav>
  );
}

function Account() {
  const session = useSession();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const location = useLocation();

  useEffect(() => setOpen(false), [location.pathname]);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!session.loggedIn) {
    return (
      <button type="button" className="btn btn-dark btn-sm" onClick={() => session.login()} disabled={!session.ready}>
        Sign in
      </button>
    );
  }
  const initial = (session.name ?? '?').charAt(0).toUpperCase();
  return (
    <div className="account" ref={ref}>
      <button type="button" className="avatar" aria-haspopup="menu" aria-expanded={open} aria-label="Account" onClick={() => setOpen((o) => !o)}>
        {initial}
      </button>
      {open && (
        <div className="menu" role="menu">
          <div className="menu-head">
            <strong>{session.name ? `Hi, ${session.name}` : 'Signed in'}</strong>
          </div>
          <Link role="menuitem" to="/bookings">
            My bookings
          </Link>
          {session.isAdmin && (
            <Link role="menuitem" to="/admin">
              Admin
            </Link>
          )}
          <button role="menuitem" type="button" onClick={session.logout}>
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
