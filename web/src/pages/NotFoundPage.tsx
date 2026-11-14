import { Link } from 'react-router';
import { EmptyState } from '../components/States';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export function NotFoundPage() {
  useDocumentTitle('Page not found');
  return (
    <EmptyState title="Page not found">
      <p>The link may be broken, or the page may have moved.</p>
      <Link className="btn" to="/">
        Go to home
      </Link>
    </EmptyState>
  );
}
