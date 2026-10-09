# ==============================================================================
# PayPink Mobile App — Production Multi-Stage Flutter Web Dockerfile
# ==============================================================================
# Stage 1: Build Flutter Web application
FROM ghcr.io/cirruslabs/flutter:stable AS build

WORKDIR /app

# Enable web support and pre-fetch dependencies
RUN flutter config --enable-web

COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

COPY . .
RUN flutter build web --release --no-wasm-dry-run --no-tree-shake-icons

# Stage 2: Serve compiled web artifact using lightweight Nginx Alpine
FROM nginx:1.25-alpine

# Remove default configuration and add PayPink configuration
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy build output from Stage 1
COPY --from=build /app/build/web /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
