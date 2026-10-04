# Development and Deployment

*Last checked against the code: 3 October 2026.*

How to run MemberTracker locally, build it, and deploy it.

## Prerequisites

- Java 17 (set by the Gradle toolchain)
- Node.js 20 and npm. Gradle downloads Node 20.9.0 for its own frontend build.
- MySQL 8 on `localhost:3306` with a database named `felege_selam`. The `dev` profile connects as user `root`, password `password`; see [Configuration](#configuration).

## Run locally

Use two terminals.

**Terminal 1 — backend on http://localhost:8080:**

```bash
./gradlew bootRun        # or: ./gradlew dev
```

`bootRun` uses the `dev` profile automatically (unless `SPRING_PROFILES_ACTIVE` is set), so the local-only database and JWT values in `application-dev.properties` apply. Liquibase runs the migrations on startup. DevTools restarts the app when Java files change.

**Terminal 2 — frontend on http://localhost:3000:**

```bash
cd frontend
cp .env.example .env.development   # first time only
npm install                        # first time only
npm run dev
```

Open **http://localhost:3000**. Vite proxies `/api/*` to the backend (`frontend/vite.config.js`).

**Sign in:** self-registration is disabled. Seed users are in `src/main/resources/db/sql/002.sample-data.sql`. The passwords given in its comments have not been checked against the hashes.

> **Broken Gradle tasks:** `./gradlew :frontend:vueRunDev` runs `npm run serve`, and `:frontend:vueLint` runs `npm run lint`. Neither script exists in `package.json`, which has only `dev`, `build` and `preview`. Use the npm commands above.

### Other commands

| Command | What it does |
|---------|--------------|
| `./gradlew test` | Backend tests, including full-context smoke tests on an in-memory H2 database (no MySQL needed) |
| `cd frontend && npm run build` | Frontend production build into `frontend/dist` |
| `cd frontend && npm run preview` | Serve the frontend build locally |

### API documentation

Swagger works only with the `dev` profile.

CSRF protection is on: "Try it out" on POST, PUT and DELETE needs the `X-XSRF-TOKEN` header, set to the value of the `XSRF-TOKEN` cookie from the browser (any GET, such as `/api/users/me`, sets it). GETs work without it.

1. Start the backend with `./gradlew bootRun` (it uses the `dev` profile by default).
2. In the same browser, sign in first with `POST /api/auth/login` so the `sid` cookie is set. Swagger's requests then carry the cookie.
3. Open http://localhost:8080/swagger-ui.html (it redirects to the Swagger UI; the smoke test checks this).

The raw spec is at `/v3/api-docs`. Swagger and the spec are off in every other profile on purpose (the paths return 404).

### Frontend notes

- Styling is Tailwind CSS v4 only (`frontend/src/assets/styles/tailwind.css`); there is no Bootstrap. The build and dev commands are unchanged (`npm run dev`, `npm run build`, `npm test`).
- Fonts (`@fontsource/*`) and icons (`bootstrap-icons`) are npm packages bundled by Vite, so the app loads nothing from a CDN or any third-party host.

### Frontend environment variables

From `frontend/.env.example`:

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` (code fallback: `/api`) | API base URL |
| `VITE_API_TIMEOUT` | `30000` | Request timeout in ms |
| `VITE_API_RETRY_ATTEMPTS` | `3` | Retries for network and 5xx errors |
| `VITE_SESSION_TIMEOUT` | `3600000` | Client inactivity timeout in ms |
| `VITE_CSP_ENABLED` | `false` | Content Security Policy toggle |
| `VITE_APP_TITLE`, `VITE_APP_VERSION` | | Display only |

### Run against a real MySQL

The unit and integration tests run on H2 and cannot catch MySQL type mismatches, so check payments and a send on a real MySQL before releasing.

```bash
docker run --rm -d --name mt-demo -p 3306:3306 -e MYSQL_ROOT_PASSWORD=password -e MYSQL_DATABASE=felege_selam mysql:8
./gradlew bootRun    # dev profile; Liquibase creates the schema and sample data
```

The seeded user hashes in `002.sample-data.sql` do not match their comments, so create a login yourself. Generate a BCrypt hash:

```bash
htpasswd -bnBC 12 "" 'YourPassword1!' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```

then set it:

```sql
UPDATE users SET password = '<hash>' WHERE email = 'admin@membertracker.com';
```

## Build

```bash
./gradlew bootJar
```

This builds the frontend (`:frontend:vueBuild`), copies it into the JAR's `/static`, and writes **`target/membertracker.jar`**. The JAR serves the frontend at `/` and the API at `/api/*`.

## Deploy

### HTTPS (Caddy reverse proxy)

1. Install Caddy: <https://caddyserver.com/docs/install>
2. Copy `deploy/Caddyfile` to `/etc/caddy/Caddyfile` and set your domain.
3. Point DNS at the server and open ports 80 and 443.
4. Run the app with the defaults: it listens on `127.0.0.1` (`SERVER_ADDRESS`) and sends Secure cookies (`COOKIE_SECURE=true`).
5. Reload Caddy: `sudo systemctl reload caddy`

Caddy renews certificates automatically, and `server.forward-headers-strategy=framework` makes Spring see the original https scheme and client IP.

### Run the JAR

```bash
java -jar target/membertracker.jar \
  --spring.datasource.url=jdbc:mysql://db-host:3306/felege_selam \
  --spring.datasource.username=app_user \
  --spring.datasource.password=change-me
```

### Environment variables

The default profile has no secret defaults: it refuses to start if a required variable is missing. The `dev` profile carries local-only values, so `./gradlew bootRun` works without any setup. The production JAR needs the environment.

| Variable | Required | Meaning |
|----------|:--------:|---------|
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Token signing key, at least 32 characters. Startup fails with a clear message if it is shorter |
| `DB_URL` | no | JDBC URL. Default `jdbc:mysql://localhost:3306/felege_selam?serverTimezone=UTC` |
| `COOKIE_SECURE` | no | Default `true`: auth cookies are sent only over HTTPS. The `dev` profile sets `false` (local http). Set `false` only for a plain-http test |
| `TRUSTED_PROXIES` | no | Regex of the peers allowed to set the client IP through `X-Forwarded-For` (used by the sign-in throttle). Default loopback only (`127\.0\.0\.1\|::1\|0:0:0:0:0:0:0:1`), right for Caddy on the same host. If the proxy is another container, set it to the proxy's address or subnet regex |
| `SERVER_ADDRESS` | no | Address to listen on. Default `127.0.0.1` (loopback, behind Caddy). Set `0.0.0.0` inside a container |
| `COOKIE_SAMESITE`, `COOKIE_DOMAIN`, `ACCESS_TTL`, `REFRESH_TTL` | no | See [authentication.md](authentication.md#configuration) |
| Mail variables | no | See [email.md](email.md#configuration) |

### systemd

`/etc/systemd/system/membertracker.service`:

```ini
[Unit]
Description=MemberTracker
After=mysql.service

[Service]
User=appuser
WorkingDirectory=/opt/membertracker
EnvironmentFile=/opt/membertracker/membertracker.env   # DB_USERNAME, DB_PASSWORD, JWT_SECRET
ExecStart=/usr/bin/java -jar /opt/membertracker/membertracker.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now membertracker
journalctl -u membertracker -f
```

### Docker

The repo has no Dockerfile yet. A minimal one:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/membertracker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

With MySQL, using `docker-compose.yml`:

```yaml
services:
  app:
    image: membertracker:latest
    ports: ["127.0.0.1:8080:8080"]
    environment:
      DB_URL: jdbc:mysql://db:3306/felege_selam?serverTimezone=UTC
      DB_USERNAME: root
      DB_PASSWORD: change-me
      JWT_SECRET: replace-with-at-least-32-random-characters
      SERVER_ADDRESS: 0.0.0.0   # inside the container
      TRUSTED_PROXIES: 172\.20\.0\.\d+   # the proxy's address or subnet on the compose network (example)
    depends_on: [db]
    restart: unless-stopped
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: change-me
      MYSQL_DATABASE: felege_selam
    volumes: [mysql-data:/var/lib/mysql]
    restart: unless-stopped
volumes:
  mysql-data:
```

### Production checklist

- [ ] HTTPS via Caddy (see [HTTPS](#https-caddy-reverse-proxy)), app bound to 127.0.0.1 (`SERVER_ADDRESS`, the default) and `COOKIE_SECURE` left at its default `true`
- [ ] Port 8080 is never published to an untrusted network (the app trusts `X-Forwarded-For` only from `TRUSTED_PROXIES`)
- [ ] `JWT_SECRET` set to a random value, for example `openssl rand -base64 48`. At least 32 characters is enforced (the default profile has no default and will not start without it). If an old build with the published default secret ever ran in production, rotate the secret now: the old default is in the git history
- [ ] `deploy/Caddyfile` in use: it sends HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` (no camera, microphone or geolocation) and a 1 MB request body limit. `Content-Security-Policy-Report-Only` is report-only from the time the frontend loaded fonts and icons from CDNs; they are now npm packages served from the app itself, so the policy can be enforced (rename to `Content-Security-Policy`) after a check that no page logs a violation
- [ ] `DB_USERNAME` and `DB_PASSWORD` set from the environment (not `root/password`); the default JDBC URL has no `useSSL=false`
- [ ] CORS origins changed in **both** `SecurityConfig` and `WebMvcConfig`. They are hard-coded to localhost.
- [ ] SQL logging stays off: `spring.jpa.show-sql=false` in the default profile (only `dev` turns it on)
- [ ] Don't enable the `dev` profile in production (it turns on Swagger and SQL logging)
- [ ] Mail settings provided (see [email.md](email.md))
- [ ] Daily database backups
- [ ] JVM memory set, for example `-Xms512m -Xmx1024m`

Open security items are tracked in [functionality-audit.md](functionality-audit.md) and [todo.md](todo.md).

## Configuration

| File | Contents |
|------|----------|
| `src/main/resources/application.properties` | Default (production) config: database, auth, mail, church info. Secrets come from the environment, no defaults |
| `src/main/resources/application-dev.properties` | `dev` profile: local-only database and JWT values, SQL logging, Swagger |
| `frontend/vite.config.js` | Dev server port 3000 and `/api` proxy |
| `src/main/java/.../infrastructure/config/SecurityConfig.java` | Security filter chain and CORS for the API |
| `src/main/java/.../infrastructure/config/WebMvcConfig.java` | Static file serving with SPA fallback, plus a second CORS mapping |

Spring also maps environment variables onto any property (for example `SERVER_PORT`, `LOGGING_LEVEL_ROOT`, `LOGGING_FILE_NAME`). Variables for auth and mail are listed in [authentication.md](authentication.md#configuration) and [email.md](email.md#configuration).

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Port already in use | Backend: `server.port`. Frontend: `server.port` in `vite.config.js`. |
| Cannot connect to the database | Check MySQL is running, the `felege_selam` database exists, and the credentials are right |
| Migration fails on startup | Check the `DATABASECHANGELOG` table and the startup log. Never edit a changeset that has already run; add a new `NNN.*.sql` file. |
| CORS error in development | Use http://localhost:3000 (the Vite proxy) rather than calling `:8080` directly |
| `Module not found` in the frontend | Run `npm install` in `frontend/` |
