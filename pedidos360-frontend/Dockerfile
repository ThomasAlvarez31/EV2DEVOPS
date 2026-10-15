# --- Etapa de build ---
FROM node:20-alpine AS build
WORKDIR /app
# Configuración de Angular a usar. Por defecto "production" (usa environment.prod.ts
# = despliegue real en AWS). docker-compose.yml pasa NG_BUILD_CONFIG=docker para el
# demo local (usa environment.docker.ts = login real de Microsoft + URLs localhost).
ARG NG_BUILD_CONFIG=production
ENV NG_BUILD_CONFIG=$NG_BUILD_CONFIG
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build -- --configuration=$NG_BUILD_CONFIG

# --- Etapa de servidor (Nginx) ---
FROM nginx:alpine
COPY --from=build /app/dist/pedidos360-frontend /usr/share/nginx/html
# Plantilla: el entrypoint de nginx la procesa con envsubst y la escribe en
# /etc/nginx/conf.d/default.conf, reemplazando SOLO las variables definidas
# (MS_*_URL -> a donde va cada prefijo de API; en multi-EC2 son las IPs).
COPY nginx.conf /etc/nginx/templates/default.conf.template
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
