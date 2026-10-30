# The HISS web client (wasmJs) served by nginx, which also proxies /api to the backend so the
# page and the API share one origin -- see deploy/nginx.conf and README's "Deploying the web
# client". Build context is the repo root.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY . .
RUN ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon --console=plain
# A blank hiss-api-base-url makes the app call its own origin (see ApiBaseUrl.wasmJs.kt).
RUN dist=composeApp/build/dist/wasmJs/productionExecutable \
    && sed -i 's|<head>|<head>\n    <meta name="hiss-api-base-url" content="">|' "$dist/index.html" \
    && grep -q 'name="hiss-api-base-url"' "$dist/index.html" \
    && cp -r "$dist" /dist

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /dist /usr/share/nginx/html
