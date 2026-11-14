import type { CSSProperties, ReactNode } from 'react';
import { errorMessage } from '../api/client';
import { AlertIcon, CheckIcon, InfoIcon } from './Icons';

export function Loading({ text = 'Loading' }: { text?: string }) {
  return (
    <div className="state" role="status">
      <p>{text}…</p>
    </div>
  );
}

export function ErrorState({ error, onRetry, title = 'Something went wrong' }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <div className="state" role="alert">
      <span className="state-icon">
        <AlertIcon size={22} />
      </span>
      <h2 className="state-title">{title}</h2>
      <p>{errorMessage(error)}</p>
      {onRetry && (
        <button type="button" className="btn btn-outline btn-sm" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, icon, children }: { title: string; icon?: ReactNode; children?: ReactNode }) {
  return (
    <div className="state">
      {icon && <span className="state-icon">{icon}</span>}
      <h2 className="state-title">{title}</h2>
      {children}
    </div>
  );
}

export type Tone = 'info' | 'error' | 'ok' | 'warn';

export function Notice({ tone = 'info', title, children }: { tone?: Tone; title?: string; children?: ReactNode }) {
  return (
    <div className="notice" data-tone={tone} role={tone === 'error' ? 'alert' : 'status'}>
      {tone === 'ok' ? <CheckIcon size={18} /> : tone === 'info' ? <InfoIcon /> : <AlertIcon />}
      <div>
        {title && <strong>{title}</strong>}
        {children && <div>{children}</div>}
      </div>
    </div>
  );
}

export function Skeleton({ width = '100%', height = 16, style }: { width?: number | string; height?: number | string; style?: CSSProperties }) {
  return <div className="skeleton" aria-hidden="true" style={{ width, height, ...style }} />;
}
