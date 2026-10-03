# Development and Deployment

*Last checked against the code: 3 October 2026.*

How to run MemberTracker locally, build it, and deploy it.

## Prerequisites

- Java 17 (set by the Gradle toolchain)
- Node.js 20 and npm. Gradle downloads Node 20.9.0 for its own frontend build.
- MySQL 8 on `localhost:3306` with a database named `felege_selam`. The defaults are user `root`, password `password`; see [Configuration](#configuration).

## Run locally

Use two terminals.

**Terminal 1 — backend on http://localhost:8080:**

```bash
./gradlew bootRun        # or: ./gradlew dev
```

Liquibase runs the migrations on startup. DevTools restarts the app when Java files change.

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
| `./gradlew test` | Backend tests |
| `cd frontend && npm run build` | Frontend production build into `frontend/dist` |
| `cd frontend && npm run preview` | Serve the frontend build locally |

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

## Build

```bash
./gradlew bootJar
```

This builds the frontend (`:frontend:vueBuild`), copies it into the JAR's `/static`, and writes **`target/membertracker.jar`**. The JAR serves the frontend at `/` and the API at `/api/*`.

## Deploy

### Run the JAR

```bash
java -jar target/membertracker.jar \
  --spring.datasource.url=jdbc:mysql://db-host:3306/felege_selam \
  --spring.datasource.username=app_user \
  --spring.datasource.password=change-me
```

### systemd

`/etc/systemd/system/membertracker.service`:

```ini
[Unit]
Description=MemberTracker
After=mysql.service

[Service]
User=appuser
WorkingDirectory=/opt/membertracker
EnvironmentFile=/opt/membertracker/membertracker.env
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
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/felege_selam
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: change-me
      JWT_SECRET: change-me
      COOKIE_SECURE: "true"
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

- [ ] HTTPS in front of the app (reverse proxy or Spring SSL), and `COOKIE_SECURE=true`
- [ ] `JWT_SECRET` set. The default in `application.properties` is public.
- [ ] Database credentials from the environment, not `root/password`. Remove `useSSL=false` from the JDBC URL.
- [ ] CORS origins changed in **both** `SecurityConfig` and `WebMvcConfig`. They are hard-coded to localhost.
- [ ] SQL logging turned off: `spring.jpa.show-sql=false`. It is `true` in both properties files.
- [ ] Mail settings provided (see [email.md](email.md))
- [ ] Daily database backups
- [ ] JVM memory set, for example `-Xms512m -Xmx1024m`

Open security items are tracked in [functionality-audit.md](functionality-audit.md) and [todo.md](todo.md).

## Configuration

| File | Contents |
|------|----------|
| `src/main/resources/application.properties` | Default config: database, auth, mail, church info |
| `src/main/resources/application-dev.properties` | `dev` profile overrides |
| `frontend/vite.config.js` | Dev server port 3000 and `/api` proxy |
| `src/main/java/.../infrastructure/config/SecurityConfig.java` | Security filter chain and CORS for the API |
| `src/main/java/.../infrastructure/config/WebMvcConfig.java` | Static file serving with SPA fallback, plus a second CORS mapping |

Spring maps environment variables onto properties: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SERVER_PORT`, `LOGGING_LEVEL_ROOT`, `LOGGING_FILE_NAME`. Variables for auth and mail are listed in [authentication.md](authentication.md#configuration) and [email.md](email.md#configuration).

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Port already in use | Backend: `server.port`. Frontend: `server.port` in `vite.config.js`. |
| Cannot connect to the database | Check MySQL is running, the `felege_selam` database exists, and the credentials are right |
| Migration fails on startup | Check the `DATABASECHANGELOG` table and the startup log. Never edit a changeset that has already run; add a new `NNN.*.sql` file. |
| CORS error in development | Use http://localhost:3000 (the Vite proxy) rather than calling `:8080` directly |
| `Module not found` in the frontend | Run `npm install` in `frontend/` |
