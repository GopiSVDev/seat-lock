import '@fontsource-variable/onest/wght.css';
// base styles first, so page styles can override them
import './styles/base.css';
import { App } from './App';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from 'react-oidc-context';
import { BrowserRouter } from 'react-router';
import { ApiError } from './api/client';
import { oidcConfig } from './auth/config';
import { TokenBridge } from './auth/session';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // client errors (404, 400) won't change on a retry
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <AuthProvider {...oidcConfig}>
    <TokenBridge />
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </AuthProvider>,
);
