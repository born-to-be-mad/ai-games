# Stage 1 — build React app
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (layer cache)
COPY package.json package-lock.json* ./
RUN npm ci --silent

# Copy source and build
COPY index.html ./
COPY vite.config.ts tsconfig.json ./
COPY public ./public
COPY src ./src
RUN npm run build

# Stage 2 — serve with Nginx
FROM nginx:alpine

# Remove default Nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom config and built assets
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/build /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
