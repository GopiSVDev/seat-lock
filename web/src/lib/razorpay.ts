// Razorpay Checkout (Standard web integration). Card details stay inside Razorpay's own window.
export type RazorpaySuccess = { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string };
export type RazorpayFailure = { error: { code?: string; description?: string; reason?: string } };

type RazorpayOptions = {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  timeout: number;
  theme?: { color?: string; backdrop_color?: string };
  prefill?: { name?: string };
  handler: (result: RazorpaySuccess) => void;
  modal?: { ondismiss?: () => void; escape?: boolean; confirm_close?: boolean };
};

type RazorpayInstance = { open: () => void; on: (event: 'payment.failed', cb: (r: RazorpayFailure) => void) => void };
type RazorpayConstructor = new (options: RazorpayOptions) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

const SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';
let loading: Promise<RazorpayConstructor> | null = null;

export function loadRazorpay(): Promise<RazorpayConstructor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SCRIPT;
    script.async = true;
    script.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay did not load')));
    script.onerror = () => {
      loading = null;
      reject(new Error("Couldn't load Razorpay. Check your connection and try again."));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** Seconds the widget may stay open: it closes a minute before the payment window ends. */
export function widgetTimeout(paymentExpiresAt: string, now = Date.now()): number {
  return Math.floor((Date.parse(paymentExpiresAt) - now) / 1000) - 60;
}

export async function openRazorpay(options: Omit<RazorpayOptions, 'theme'> & { onFailed: (r: RazorpayFailure) => void }): Promise<void> {
  const Razorpay = await loadRazorpay();
  const { onFailed, ...rest } = options;
  const widget = new Razorpay({ ...rest, theme: { color: '#d6361a', backdrop_color: 'rgba(20, 20, 20, 0.6)' } });
  widget.on('payment.failed', onFailed);
  widget.open();
}
