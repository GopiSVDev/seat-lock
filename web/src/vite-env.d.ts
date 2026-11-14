/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_OIDC_AUTHORITY?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
