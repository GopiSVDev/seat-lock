type IconProps = { size?: number; label?: string };
const svgProps = (size: number, label?: string, strokeWidth = 1.8) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  role: label ? 'img' : undefined,
  'aria-label': label,
  'aria-hidden': label ? undefined : true,
  focusable: false,
});

export const PinIcon = ({ size = 18, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21Z" />
    <circle cx="12" cy="9.5" r="2.5" />
  </svg>
);
export const SearchIcon = ({ size = 18, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
);
export const CheckIcon = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2.4)}>
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </svg>
);
export const ChevronDown = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <path d="m6 9 6 6 6-6" />
  </svg>
);
export const ChevronRight = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <path d="m9 6 6 6-6 6" />
  </svg>
);
export const BackIcon = ({ size = 18, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <path d="M19 12H5M11 6l-6 6 6 6" />
  </svg>
);
export const PlusIcon = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <path d="M12 5v14M5 12h14" />
  </svg>
);
export const MinusIcon = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <path d="M5 12h14" />
  </svg>
);
export const ClockIcon = ({ size = 16, label }: IconProps) => (
  <svg {...svgProps(size, label, 2)}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);
export const InfoIcon = ({ size = 18, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 11v5M12 8h.01" />
  </svg>
);
export const AlertIcon = ({ size = 18, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <path d="M10.3 4.2 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0Z" />
    <path d="M12 9.5v4M12 17h.01" />
  </svg>
);
export const TicketIcon = ({ size = 20, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4Z" />
    <path d="M14 6v12" strokeDasharray="2 2.5" />
  </svg>
);
export const FilmIcon = ({ size = 20, label }: IconProps) => (
  <svg {...svgProps(size, label)}>
    <rect x="3" y="4" width="18" height="16" rx="2" />
    <path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4" />
  </svg>
);
export const SeatMark = ({ size = 18 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable={false}>
    <path d="M6 5.5A2.5 2.5 0 0 1 8.5 3h7A2.5 2.5 0 0 1 18 5.5V12H6Z" />
    <path d="M3 11.5A1.5 1.5 0 0 1 4.5 10 1.5 1.5 0 0 1 6 11.5V14h12v-2.5a1.5 1.5 0 0 1 3 0V17a1 1 0 0 1-1 1h-1v2.2a.8.8 0 0 1-1.6 0V18H6.6v2.2a.8.8 0 0 1-1.6 0V18H4a1 1 0 0 1-1-1Z" />
  </svg>
);
