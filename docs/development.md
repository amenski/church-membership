# Development and Deployment

*Last checked against the code: 5 October 2026.*

How to run MemberTracker locally, build it, and deploy it.

## Prerequisites

- Java 17 (set by the Gradle toolchain)
- Node.js 20 and npm. Gradle downloads Node 20.9.0 for its own frontend build.
- MySQL 8 on `localhost:3306` with a database named `felege_selam`. The `dev` profile connects as user `root`, password `password`; see [Configuration](#configuration). The quickest way is the Docker Compose setup in [MySQL with Docker Compose](#mysql-with-docker-compose) (needs Docker).

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

**Sign in:** self-registration is disabled. The `dev` profile loads sample data, including two users (`admin@membertracker.com` and `testuser@membertracker.com`) from `src/main/resources/db/sql/002.sample-data.sql`, but the passwords written in that file's comments do not match the stored hashes (sign-in with them fails), so set a password first: [Create a login](#create-a-login). A production database starts empty instead: see [First start](#first-start).

> `./gradlew :frontend:vueRunDev` runs `npm run dev`, the same as the npm command above. There is no lint script.

### Other commands

| Command | What it does |
|---------|--------------|
| `./gradlew test` | Backend tests, including full-context smoke tests on an in-memory H2 database (no MySQL needed) |
| `cd frontend && npm run build` | Frontend production build into `frontend/dist` |
| `cd frontend && npm run preview` | Serve the frontend build locally |

### Coverage

| Command | Report |
|---------|--------|
| `./gradlew test` (JaCoCo report runs after the tests; `./gradlew jacocoTestReport` alone also runs `test` first) | `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml` |
| `cd frontend && npm run coverage` (Vitest with `@vitest/coverage-v8`) | `frontend/coverage/index.html` |

Neither command enforces a threshold, so coverage never fails a build.

### API documentation

Swagger works only with the `dev` profile.

CSRF protection is on: "Try it out" on POST, PUT and DELETE needs the `X-XSRF-TOKEN` header, set to the value of the `XSRF-TOKEN` cookie from the browser (any GET, such as `/api/users/me`, sets it). GETs work without it.

1. Start the backend with `./gradlew bootRun` (it uses the `dev` profile by default).
2. In the same browser, sign in first with `POST /api/auth/login` so the `sid` cookie is set. Swagger's requests then carry the cookie.
3. Open http://localhost:8080/swagger-ui.html (it redirects to the Swagger UI; the smoke test checks this).

The raw spec is at `/v3/api-docs`. Swagger and the spec are off in every other profile on purpose (the paths return 404).

### Frontend notes

- Styling is Tailwind CSS v4 only (`frontend/src/assets/styles/tailwind.css`); there is no Bootstrap. The build and dev commands are unchanged (`npm run dev`, `npm run build`, `npm test`).
- Fonts (`@fontsource/ibm-plex-sans`, `@fontsource/noto-sans-ethiopic`) are npm packages bundled by Vite, so the app loads nothing from a CDN or any third-party host. Icons are inline SVG in `frontend/src/components/Icon.vue`; there is no icon font. Tokens, density and the component list are in [design.md](design.md).

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

### MySQL with Docker Compose

`docker-compose.yml` at the repo root runs MySQL 8.4 with the settings the `dev` profile expects: user `root`, password `password`, database `felege_selam`, UTC time zone, `utf8mb4`. The database files are kept in a folder on your machine (`./data/mysql` by default), not inside the container, so the data stays when the container is removed. The unit and integration tests run on H2 and cannot catch MySQL type mismatches, so check payments and a send on a real MySQL before releasing.

```bash
docker compose up -d --wait   # first start downloads the image, creates the database, and returns when it is healthy
./gradlew bootRun             # dev profile; Liquibase creates the schema and loads the sample data (context dev)
```

| Command | What it does |
|---------|--------------|
| `docker compose ps` | Show the container and its health (`healthy` once MySQL accepts connections) |
| `docker compose logs mysql` | Show the MySQL log (add `-f` to follow it) |
| `docker compose stop` | Stop the container. Data and container stay; `docker compose start` runs it again |
| `docker compose down` | Remove the container and network. **The data stays** in `./data/mysql` and the next `up -d` uses it |
| `rm -rf ./data/mysql` (after `down`) | Delete all the data. The next `up -d` starts with an empty database |

Settings are optional. To change them, copy `.env.example` to `.env` (git ignores it):

| Variable | Default | Meaning |
|----------|---------|---------|
| `MYSQL_ROOT_PASSWORD` | `password` | Root password. The `dev` profile has `password` built in, so if you change it, start the app with `SPRING_DATASOURCE_PASSWORD` set to the same value |
| `MYSQL_PORT` | `3306` | Port on your machine. The container listens only on `127.0.0.1` |
| `MYSQL_DATA_DIR` | `./data/mysql` | Folder that holds the database files |

The password and database name apply only when the data folder is empty (first start). Changing `MYSQL_ROOT_PASSWORD` later does not change the password stored in an existing data folder.

The defaults are for local development only. On a server, set a strong `MYSQL_ROOT_PASSWORD` and a `MYSQL_DATA_DIR` outside the repo, and give the app its own database login instead of root: [deploy-linux.md](deploy-linux.md#5-database-server). The `data/` folder holds real database files and is ignored by git: never commit it. On macOS with Docker Desktop, the data folder must be under a path Docker may share. The repo folder is, since Docker Desktop shares your home folder by default.

#### Moving existing data

If you already have data in another MySQL container (for example one started with `docker run`), dump it and load it into the compose one. Both containers cannot use port 3306 at the same time, so either stop the old one first or start the new one on another port (`MYSQL_PORT=3307 docker compose up -d`; the app then needs `SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/felege_selam?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'`, because the `dev` profile has port 3306 built in).

```bash
# 1. Dump from the old container (here called old-mysql; use its name and password)
docker exec old-mysql mysqldump -uroot -ppassword --single-transaction --routines felege_selam > backup.sql

# 2. Start the compose one and wait for "healthy"
docker compose up -d --wait

# 3. Load the dump into the compose container
docker exec -i membertracker-mysql mysql -uroot -ppassword felege_selam < backup.sql
```

Check the row counts, then stop or remove the old container when you are sure. `backup.sql` holds real data: keep it out of git (do not commit it).

If the app has already run against the compose database, Liquibase has created the tables, and the dump would hit "table already exists". Restore into an empty database instead: `docker exec membertracker-mysql mysql -uroot -ppassword -e "DROP DATABASE felege_selam; CREATE DATABASE felege_selam"`, then step 3.

#### Backups

- Dump (works while the database runs): `docker exec membertracker-mysql mysqldump -uroot -ppassword --single-transaction --routines felege_selam > backup.sql`
- Copy the folder: only after `docker compose stop` (or `down`). A copy of a running database can be corrupt.

On a server, `deploy/backup-mysql.sh` does this every night and keeps the newest 14 ([deploy-linux.md](deploy-linux.md#11-back-up-server)). A backup on the same disk does not protect against losing the disk; keep a copy somewhere else.

#### Create a login

For local development only. The sample users in `002.sample-data.sql` (loaded by the `dev` profile) have hashes that do not match the passwords in their comments, so give one of them a password. A production database has no sample users: use [First start](#first-start) there. Generate a BCrypt hash:

```bash
htpasswd -bnBC 12 "" 'YourPassword1!' | tr -d ':\n' | sed 's/^\$2y/\$2a/'
```

then set it (in `docker exec -it membertracker-mysql mysql -uroot -ppassword felege_selam`):

```sql
UPDATE users SET password = '<hash>' WHERE email = 'admin@membertracker.com';
```

## Build

```bash
./gradlew bootJar
```

This builds the frontend (`:frontend:vueBuild`), copies it into the JAR's `/static`, and writes **`target/membertracker.jar`**. The JAR contains the frontend and the API at `/api/*`, and needs nothing else to serve a browser, with or without Caddy. The pages are served at the site root, to a signed-out browser too: `/`, `/index.html` and `/assets/**` (see [authentication.md](authentication.md#public-paths)). A reload on a client route such as `/members` returns the app, and a missing file answers 404. The build also copies nothing else to the root (the favicon is inline in `index.html`); a file you add to `frontend/public` is served only to a signed-in browser until its path is added to the permit list in `SecurityConfig`.

## Deploy

A step-by-step checklist for one Linux machine (everything in Docker, or the jar under systemd; install, database, Caddy, firewall, backups, updates, rollback) is in [deploy-linux.md](deploy-linux.md). The sections below are the reference.

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

### First start

A production database starts **empty**: no users, members, payments or messages. The sample data (`002.sample-data.sql`) is tagged with the Liquibase context `dev`. The default profile runs the context `prod` (`spring.liquibase.contexts=prod`) and skips it, while the `dev` profile runs the context `dev` and loads it. Every other migration runs in both. (Do not clear `spring.liquibase.contexts` in production: with no context at all Liquibase runs every changeset, the sample data included.)

Registration is disabled, so the first administrator comes from the environment. Set both variables in the env file for the first start:

```bash
BOOTSTRAP_ADMIN_EMAIL=owner@example.org
BOOTSTRAP_ADMIN_PASSWORD=<a strong password>
```

1. Start the app. Liquibase creates the schema, then the app creates one enabled `ADMIN` with that email and the password, BCrypt-hashed, and logs `Created the first administrator owner@example.org` (the password is never logged).
2. Sign in with that email and password, then **remove the `BOOTSTRAP_ADMIN_PASSWORD` line** from the env file and restart. The variables are used only while the users table is empty, so leaving them is harmless, but a password in a file is a risk you do not need.
3. Change the password under Profile if you like. Other accounts are added in SQL for now (there is no user admin screen; see [authentication.md](authentication.md#endpoints)).

The password follows the same rule as every password: 8 to 72 characters (bytes), with an uppercase letter, a lowercase letter, a digit and a special character. The email must be a valid address. If either is invalid, or only one of the two is set while the users table is empty, the app refuses to start with a message that names the variable (`BOOTSTRAP_ADMIN_PASSWORD is not strong enough: ...`) and never prints the password. With no users and neither variable set the app starts and logs a warning that nobody can sign in; set both and restart.

### Environment variables

The default profile has no secret defaults and fails to start if a required variable is missing: a missing `JWT_SECRET` stops with `auth.jwt-secret (JWT_SECRET) must be at least 32 characters`, while a missing `DB_USERNAME` or `DB_PASSWORD` is not named: the text `${DB_USERNAME}` is used as the value and the database answers `Access denied for user '${DB_USERNAME}'`. The `dev` profile carries local-only values, so `./gradlew bootRun` works without any setup. The production JAR needs the environment; `deploy/membertracker.env.example` lists every variable.

| Variable | Required | Meaning |
|----------|:--------:|---------|
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Token signing key, at least 32 characters. Startup fails with a clear message if it is shorter |
| `BOOTSTRAP_ADMIN_EMAIL` | first start only | Email of the first administrator. Used only while the users table is empty; see [First start](#first-start) |
| `BOOTSTRAP_ADMIN_PASSWORD` | first start only | Password of the first administrator (8 to 72 characters with upper, lower, digit and special). **Remove it after the first start** |
| `DB_URL` | no | JDBC URL. Default `jdbc:mysql://localhost:3306/felege_selam?serverTimezone=UTC` |
| `COOKIE_SECURE` | no | Default `true`: auth cookies are sent only over HTTPS. The `dev` profile sets `false` (local http). Set `false` only for a plain-http test |
| `TRUSTED_PROXIES` | no | Regex of the peers allowed to set the client IP through `X-Forwarded-For` (used by the sign-in throttle). Default loopback only (`127\.0\.0\.1\|::1\|0:0:0:0:0:0:0:1`), right for Caddy on the same host. If the proxy is another container, set it to the proxy's address or subnet regex. `docker-compose.server.yml` sets its own default: loopback plus `172.16.0.0/12` and `192.168.0.0/16`, because a published port and other containers show up as Docker bridge addresses |
| `SERVER_ADDRESS` | no | Address to listen on. Default `127.0.0.1` (loopback, behind Caddy). Set `0.0.0.0` inside a container |
| `COOKIE_SAMESITE`, `COOKIE_DOMAIN`, `ACCESS_TTL`, `REFRESH_TTL` | no | See [authentication.md](authentication.md#configuration) |
| Mail variables | no | See [email.md](email.md#configuration) |

### systemd

The unit is `deploy/membertracker.service` (user `membertracker`, settings from `/opt/membertracker/membertracker.env`, `Restart=on-failure`, heap `-Xms512m -Xmx1024m`, a few hardening options). The settings file is `deploy/membertracker.env.example`. Install both as in [deploy-linux.md](deploy-linux.md#7-install-the-service-and-start-it-server). systemd does not allow a comment after a value on the same line, in a unit or in an env file: put comments on their own line.

```bash
sudo systemctl enable --now membertracker
journalctl -u membertracker -f
```

### Docker

The app has an image and a server stack: `Dockerfile` (multi-stage: the JDK builds the jar, front end included, then only the JRE and the jar go into the image, which runs as a non-root user with a health check on `/`), `docker-compose.server.yml` (MySQL and the app, MySQL with no published port and the app on `127.0.0.1` only, read-only root filesystem, no capabilities, 768 MB limit) and `deploy/server.env.example` (every variable of that stack). The steps, with checks, updates, rollback and backups, are in [deploy-linux.md](deploy-linux.md#docker-recommended-if-the-server-already-runs-docker). The app reads the same environment variables as the jar (table above); the compose file sets `DB_URL`, `SERVER_ADDRESS` and a `TRUSTED_PROXIES` that fits Docker networks.

To build the image alone: `docker build -t membertracker:local .`. The build needs the internet and no Java or Node on your machine: the Gradle build downloads its own Node. The image keeps the dependency downloads in their own layers, so a code change rebuilds in about 15 seconds.

### Production checklist

- [ ] HTTPS via Caddy (see [HTTPS](#https-caddy-reverse-proxy)), app bound to 127.0.0.1 (`SERVER_ADDRESS`, the default) and `COOKIE_SECURE` left at its default `true`
- [ ] Port 8080 is never published to an untrusted network (the app trusts `X-Forwarded-For` only from `TRUSTED_PROXIES`)
- [ ] `JWT_SECRET` set to a random value, for example `openssl rand -base64 48`. At least 32 characters is enforced (the default profile has no default and will not start without it). If an old build with the published default secret ever ran in production, rotate the secret now: the old default is in the git history
- [ ] `deploy/Caddyfile` in use: it sends HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` (no camera, microphone or geolocation) and a 1 MB request body limit. `Content-Security-Policy-Report-Only` is report-only from the time the frontend loaded fonts and icons from CDNs; they are now npm packages served from the app itself, so the policy can be enforced (rename to `Content-Security-Policy`) after a check that no page logs a violation
- [ ] `DB_USERNAME` and `DB_PASSWORD` set from the environment (not `root/password`); the default JDBC URL has no `useSSL=false`
- [ ] CORS origins: hard-coded to localhost in **both** `SecurityConfig` and `WebMvcConfig`. Change them only if the page is served from another address than the API. With the jar behind Caddy, or on `http://host:8080`, the page and the API share one origin and a sign-in works with the lists unchanged (a request that names a foreign `Origin` gets 403)
- [ ] SQL logging stays off: `spring.jpa.show-sql=false` in the default profile (only `dev` turns it on)
- [ ] Don't enable the `dev` profile in production (it turns on Swagger and SQL logging, and loads the sample users, members and payments)
- [ ] First start: `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` set, the administrator can sign in, then **`BOOTSTRAP_ADMIN_PASSWORD` removed** from the env file (see [First start](#first-start))
- [ ] Mail settings provided (see [email.md](email.md))
- [ ] Daily database backups (`deploy/backup-mysql.sh` from cron, a copy kept off the machine, a restore tried once: [deploy-linux.md](deploy-linux.md#11-back-up-server))
- [ ] JVM memory set, for example `-Xms512m -Xmx1024m` (`deploy/membertracker.service` sets it)

Open security items are tracked in [functionality-audit.md](functionality-audit.md) and [todo.md](todo.md).

## Configuration

| File | Contents |
|------|----------|
| `src/main/resources/application.properties` | Default (production) config: database, auth, mail, church info, Liquibase context `prod`, the first-administrator variables. Secrets come from the environment, no defaults |
| `src/main/resources/application-dev.properties` | `dev` profile: local-only database and JWT values, SQL logging, Swagger, Liquibase context `dev` (loads the sample data) |
| `frontend/vite.config.js` | Dev server port 3000 and `/api` proxy |
| `src/main/java/.../infrastructure/config/SecurityConfig.java` | Security filter chain (the permit list), SPA fallback registration and CORS for the API |
| `src/main/java/.../infrastructure/config/WebMvcConfig.java` | Cache headers for the built pages (`/index.html` no-cache, `/assets/**` one year immutable), plus a second CORS mapping |

Spring also maps environment variables onto any property (for example `SERVER_PORT`, `LOGGING_LEVEL_ROOT`, `LOGGING_FILE_NAME`). Variables for auth and mail are listed in [authentication.md](authentication.md#configuration) and [email.md](email.md#configuration).

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Port already in use | Backend: `server.port`. Frontend: `server.port` in `vite.config.js`. |
| Cannot connect to the database | Check MySQL is running, the `felege_selam` database exists, and the credentials are right |
| Startup stops with `BOOTSTRAP_ADMIN_...` | The first-administrator value is missing or invalid (the message names the variable). Fix it, or unset both variables if the database already has users |
| Nobody can sign in on a new production database | The users table is empty and `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` were not set. Set both and restart (see [First start](#first-start)) |
| Migration fails on startup | Check the `DATABASECHANGELOG` table and the startup log. Never edit a changeset that has already run; add a new `NNN.*.sql` file. |
| CORS error in development | Use http://localhost:3000 (the Vite proxy) rather than calling `:8080` directly |
| `Module not found` in the frontend | Run `npm install` in `frontend/` |
