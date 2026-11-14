# Build the static site with Node, serve it with nginx (no Node at runtime)
FROM node:24.11.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
ARG VITE_OIDC_AUTHORITY
RUN test -n "$VITE_OIDC_AUTHORITY" || (echo 'VITE_OIDC_AUTHORITY build arg is required' && exit 1)
COPY . .
RUN npm run build

FROM nginx:1.28.0-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
