# Development & Deployment Guide

This guide covers both development and production deployment of the Member Tracker application.

## Table of Contents

- [Development Mode](#development-mode)
- [Production Deployment](#production-deployment)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)

---

## Development Mode

Run the application in development mode with hot reload for both backend and frontend.

### Prerequisites

- Java 17 or higher
- Node.js and npm
- MySQL database running

### Development Setup

The application uses a modern full-stack architecture:
- **Backend**: Spring Boot (Java) - Port 8080
- **Frontend**: Vue.js 3 - Port 8081

### Running the Application

#### Option 1: Two Terminal Approach (Recommended)

**Terminal 1 - Backend:**
```bash
./gradlew bootRun
```
- Runs Spring Boot application on http://localhost:8080
- Auto-reloads when Java files change (Spring Boot DevTools)

**Terminal 2 - Frontend:**
```bash
cd frontend
npm run serve
```
- Runs Vue.js dev server on http://localhost:8081
- Hot Module Replacement for instant updates

**Access the application at:** http://localhost:8081

#### Option 2: Using Gradle Tasks

**Backend:**
```bash
./gradlew dev
```

**Frontend:**
```bash
./gradlew :frontend:vueRunDev
```

### How It Works

1. **Frontend Dev Server (Port 8081)** serves Vue.js app with hot reload
2. **Backend API (Port 8080)** runs Spring Boot with DevTools auto-reload
3. **API Proxy** in `vue.config.js` forwards `/api/*` requests to backend
4. **CORS** configured in `WebMvcConfig.java` allows cross-origin requests

### Development Features

- **Frontend Hot Reload**: Instant browser refresh on `.vue` file changes
- **Backend Hot Reload**: Automatic restart on `.java` file changes
- **API Proxy**: Seamless API calls without CORS issues
- **No Build Required**: Work directly with source files

### Other Development Commands

**Run Tests:**
```bash
./gradlew test
```

**Lint Frontend Code:**
```bash
cd frontend
npm run lint
```

**Frontend Build Only:**
```bash
cd frontend
npm run build
```

---

## Production Deployment

### Building for Production

The application can be packaged as a single executable JAR file containing both backend and frontend.

**Build Production JAR:**
```bash
./gradlew bootJar
```

This command:
1. Builds the Vue.js frontend (`npm run build`)
2. Packages frontend into `/static` directory
3. Compiles Spring Boot backend
4. Creates `target/membertracker.jar` (single executable JAR)

### Deployment Options

#### Option 1: Direct JAR Execution

**Run the JAR:**
```bash
java -jar target/membertracker.jar
```

**Run with custom configuration:**
```bash
java -jar target/membertracker.jar \
  --spring.datasource.url=jdbc:mysql://production-db:3306/membertracker \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=prod_password \
  --server.port=8080
```

**Run as background service:**
```bash
nohup java -jar target/membertracker.jar > app.log 2>&1 &
```

#### Option 2: SystemD Service (Linux)

Create `/etc/systemd/system/membertracker.service`:

```ini
[Unit]
Description=Member Tracker Application
After=mysql.service

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/membertracker
ExecStart=/usr/bin/java -jar /opt/membertracker/membertracker.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Enable and start:**
```bash
sudo systemctl enable membertracker
sudo systemctl start membertracker
sudo systemctl status membertracker
```

#### Option 3: Docker Deployment

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/membertracker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build and run:**
```bash
./gradlew bootJar
docker build -t membertracker:latest .
docker run -d -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/membertracker \
  -e SPRING_DATASOURCE_USERNAME=user \
  -e SPRING_DATASOURCE_PASSWORD=password \
  --name membertracker \
  membertracker:latest
```

#### Option 4: Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  app:
    image: membertracker:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/membertracker
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: password
    depends_on:
      - db
    restart: unless-stopped

  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: membertracker
    volumes:
      - mysql-data:/var/lib/mysql
    restart: unless-stopped

volumes:
  mysql-data:
```

**Run:**
```bash
docker-compose up -d
```

### Production Checklist

- [ ] Set production database credentials
- [ ] Configure proper logging (external log file)
- [ ] Set up database backups
- [ ] Configure reverse proxy (Nginx/Apache) if needed
- [ ] Enable HTTPS/SSL
- [ ] Set appropriate JVM memory limits (`-Xmx`, `-Xms`)
- [ ] Disable CORS for production (or restrict to your domain)
- [ ] Set `spring.profiles.active=production`
- [ ] Configure monitoring and health checks

### Accessing the Application

Once deployed, access the application at:
- **Local**: http://localhost:8080
- **Production**: http://your-domain.com

The frontend is served at the root path `/` and API endpoints are at `/api/*`.

---

## Configuration

### Configuration Files

- `build.gradle` - Backend dependencies and tasks
- `frontend/package.json` - Frontend dependencies
- `frontend/vue.config.js` - Vue.js dev server and proxy configuration
- `src/main/java/io/github/membertracker/config/WebMvcConfig.java` - CORS configuration
- `src/main/resources/application.properties` - Spring Boot configuration

### Environment Variables (Production)

Override configuration using environment variables:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/membertracker
SPRING_DATASOURCE_USERNAME=your_user
SPRING_DATASOURCE_PASSWORD=your_password

# Server
SERVER_PORT=8080

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_FILE_NAME=/var/log/membertracker/app.log
```

---

## Troubleshooting

**Port Already in Use:**
- Backend: Change port in `application.properties`: `server.port=8080`
- Frontend: Change port in `vue.config.js`: `devServer.port`

**Database Connection Issues:**
- Verify MySQL is running
- Check credentials in `application.properties`

**Module Not Found Errors:**
- Run `npm install` in the frontend directory

**CORS Errors (Development):**
- Verify CORS configuration in `WebMvcConfig.java`
- Ensure frontend proxy is configured in `vue.config.js`

**Production Issues:**
- Check logs: `tail -f app.log` or `journalctl -u membertracker -f`
- Verify database connectivity
- Ensure correct file permissions
- Check firewall settings for port 8080

**Performance Issues:**
- Increase JVM memory: `java -Xmx1024m -Xms512m -jar membertracker.jar`
- Enable JVM optimizations: `-XX:+UseG1GC`
- Monitor with: `jconsole` or `jvisualvm`

**Database Migration Issues:**
- Liquibase migrations run automatically on startup
- Check migration status in `DATABASECHANGELOG` table
- Review logs for migration errors
