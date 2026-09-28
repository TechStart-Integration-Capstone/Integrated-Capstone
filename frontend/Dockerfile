# ── PayPink SPA — Nginx static file server ───────────────────────────────────
#
# Serves the frontend SPA on port 80 inside the Docker network.
# Exposed on host port 3001 (3000 is taken by Grafana).
#
# All /api/* requests are proxied to api-gateway:8080 by nginx.conf,
# so the browser only ever talks to this container — no CORS issues.

FROM nginx:1.25-alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY nginx.conf /etc/nginx/nginx.conf

# Copy the SPA static files
COPY index.html          /usr/share/nginx/html/index.html
COPY src/css/style.css   /usr/share/nginx/html/src/css/style.css
COPY src/js/app.js       /usr/share/nginx/html/src/js/app.js

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
