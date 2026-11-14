import { useEffect } from 'react';

export function useDocumentTitle(title: string | null | undefined) {
  useEffect(() => {
    document.title = title ? `${title} | SeatLock` : 'SeatLock';
  }, [title]);
}
