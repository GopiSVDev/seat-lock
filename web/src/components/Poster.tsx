import { useState } from 'react';

const TONES = ['#2b3a4a', '#4a2f2a', '#2f4337', '#4b3b1f', '#3a2f45', '#1f4146', '#4a2636', '#33363b'];

type PosterProps = {
  id: number;
  title: string;
  posterUrl?: string | null;
  size?: 'card' | 'hero' | 'thumb';
  eager?: boolean;
};

/** The film's poster; a plain titled card when there is no image or it fails to load. */
export function Poster({ id, title, posterUrl, size = 'card', eager = false }: PosterProps) {
  const [failed, setFailed] = useState(false);
  if (posterUrl && !failed) {
    return (
      <div className={`poster poster-${size}`}>
        <img src={posterUrl} alt="" loading={eager ? 'eager' : 'lazy'} decoding="async" onError={() => setFailed(true)} />
      </div>
    );
  }
  return (
    <div className={`poster poster-${size} poster-blank`} style={{ background: TONES[Math.abs(id) % TONES.length] }}>
      {size !== 'thumb' && <span>{title}</span>}
    </div>
  );
}
