# MemberTracker app image: Spring Boot jar with the Vue front end inside it.
#
#   docker build -t membertracker:local .
#
# Run it with MySQL through docker-compose.server.yml (docs/deploy-linux.md).
# Two stages: the build stage has the JDK and builds the jar (the Gradle build downloads its own
# Node, so nothing but this Dockerfile and internet access is needed); the runtime stage has only
# the JRE and the jar, and runs as an unprivileged user.
#
# Built on the machine that runs it, the image matches its CPU (amd64 or arm64). To build for
# another CPU: docker build --platform linux/arm64 -t membertracker:local .

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Layer 1: Gradle itself, the Node plugin, Node, and the npm packages. Only the build files are
# copied, so this layer is reused until a build file or package.json changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY frontend/build.gradle frontend/package.json frontend/
RUN ./gradlew --no-daemon :frontend:npmInstall

# Layer 2: the Java libraries, downloaded with no source code present (the init script only
# resolves the classpaths), so the real build below only compiles.
COPY docker/resolve-deps.init.gradle docker/
RUN ./gradlew --no-daemon -I docker/resolve-deps.init.gradle :resolveDeps

# Layer 3: the sources. A change here reruns only this step and the ones after it.
COPY frontend frontend
COPY src src
RUN ./gradlew bootJar -x test --no-daemon


FROM eclipse-temurin:17-jre AS runtime

# An unprivileged user with no shell and no home: nothing in the container needs one.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build /workspace/target/membertracker.jar /app/app.jar

# MaxRAMPercentage: the heap follows the container's memory limit instead of the host's RAM.
# ExitOnOutOfMemoryError: a JVM that ran out of memory is not trusted to carry on, so it exits
# and the restart policy starts a clean one. (No java.security.egd: Java 17 on Linux already
# reads /dev/urandom for the default SecureRandom, so the flag changes nothing.)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Inside a container the app must listen on every interface to be reachable from outside it.
ENV SERVER_ADDRESS=0.0.0.0

USER app
EXPOSE 8080

# The home page is served without a sign-in and answers 200 once Liquibase is done and Tomcat is
# up. The start period covers Liquibase on a first start; failures inside it do not count.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS -o /dev/null http://127.0.0.1:8080/ || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
