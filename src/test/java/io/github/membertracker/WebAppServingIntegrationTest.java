package io.github.membertracker;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The jar serves its own web app to a browser that is not signed in. Runs against a real Tomcat on in-memory H2
 * (MockMvc does not perform a forward), with the tiny fixture in src/test/resources/static in place of the
 * built frontend.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:webapp;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
class WebAppServingIntegrationTest {

    private static final String EMAIL = "webapp@example.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void createUser() {
        userRepository.save(new User(Email.of(EMAIL), passwordEncoder.encode(PASSWORD), UserRole.MEMBER));
    }

    @AfterEach
    void deleteUser() {
        userJpaRepository.findByEmail(EMAIL).ifPresent(userJpaRepository::delete);
    }

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, BodyPublishers.noBody());
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> browserGet(String path) throws Exception {
        return send("GET", path, "Accept", BROWSER_ACCEPT);
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/login", "/members", "/members/3", "/my-dues", "/payments/new/"})
    void signedOutPagesAnswerTheIndexPage(String path) throws Exception {
        HttpResponse<String> response = browserGet(path);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).startsWith("text/html");
        assertThat(response.body()).contains("<title>Test page</title>");
    }

    @Test
    void curlStyleAcceptAllAlsoGetsThePage() throws Exception {
        HttpResponse<String> response = send("GET", "/members", "Accept", "*/*");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).startsWith("text/html");
    }

    @Test
    void headRequestForAClientRouteIsAnswered() throws Exception {
        HttpResponse<String> response = send("HEAD", "/members", "Accept", BROWSER_ACCEPT);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).startsWith("text/html");
    }

    @Test
    void signedOutAssetIsServedWithALongImmutableCache() throws Exception {
        HttpResponse<String> response = browserGet("/assets/app-test123.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contentType(response)).containsIgnoringCase("javascript");
        assertThat(header(response, "Cache-Control")).contains("max-age=31536000", "public", "immutable");
        assertThat(response.body()).contains("test asset");
    }

    @Test
    void indexPageMustBeRevalidated() throws Exception {
        for (String path : new String[] {"/", "/index.html", "/login"}) {
            assertThat(header(browserGet(path), "Cache-Control")).as(path).contains("no-cache").doesNotContain("max-age=31536000");
        }
    }

    @Test
    void missingAssetIsNotFoundNotThePage() throws Exception {
        HttpResponse<String> response = browserGet("/assets/missing.js");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Test page");
    }

    @Test
    void fileWithAnExtensionOutsideTheWebAppIsNeverThePage() throws Exception {
        // Signed out it is not on the permit list; the point is that it is not index.html either.
        HttpResponse<String> response = browserGet("/favicon-x.png");

        assertThat(response.statusCode()).isIn(401, 404);
        assertThat(response.body()).doesNotContain("Test page");
    }

    @Test
    void signedOutApiStaysJson401() throws Exception {
        for (String path : new String[] {"/api/members", "/api/nope", "/api/nope/deeper", "/api"}) {
            HttpResponse<String> response = browserGet(path);

            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(contentType(response)).as(path).startsWith("application/problem+json");
        }
    }

    @Test
    void apiDocsAreNotTurnedIntoThePage() throws Exception {
        for (String path : new String[] {"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui"}) {
            HttpResponse<String> response = browserGet(path);

            assertThat(response.body()).as(path).doesNotContain("Test page");
        }
    }

    @Test
    void postToAClientRouteIsNotForwarded() throws Exception {
        HttpResponse<String> response = send("POST", "/members", "Accept", BROWSER_ACCEPT);

        assertThat(response.statusCode()).isIn(401, 403);
        assertThat(response.body()).doesNotContain("Test page");
    }

    @Test
    void jsonOnlyClientIsNotGivenThePage() throws Exception {
        HttpResponse<String> response = send("GET", "/members", "Accept", "application/json");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(contentType(response)).startsWith("application/problem+json");
    }

    @Test
    void pageResponseCarriesTheSecurityHeaders() throws Exception {
        for (String path : new String[] {"/", "/login", "/index.html", "/assets/app-test123.js"}) {
            HttpResponse<String> response = browserGet(path);

            assertThat(header(response, "X-Content-Type-Options")).as(path).isEqualTo("nosniff");
            assertThat(header(response, "X-Frame-Options")).as(path).isEqualTo("DENY");
        }
    }

    @Test
    void pageResponseIssuesTheCsrfCookie() throws Exception {
        for (String path : new String[] {"/", "/login", "/index.html"}) {
            assertThat(xsrfSetCookie(browserGet(path))).as(path).isPresent();
        }
    }

    @Test
    void signInAfterTheFirstPageLoadWorks() throws Exception {
        String xsrf = valueOf(xsrfSetCookie(browserGet("/login")).orElseThrow());

        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=" + xsrf)
            .header("X-XSRF-TOKEN", xsrf)
            .POST(BodyPublishers.ofString("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .build();
        HttpResponse<String> loginResponse = client.send(login, BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);

        String sid = loginResponse.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("sid=")).map(WebAppServingIntegrationTest::valueOf).findFirst().orElseThrow();
        HttpResponse<String> me = send("GET", "/api/users/me", "Cookie", "sid=" + sid);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(contentType(me)).startsWith("application/json");
    }

    @Test
    void signInWithoutTheCsrfHeaderIsStillRefused() throws Exception {
        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .build();

        HttpResponse<String> response = client.send(login, BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private static Optional<String> xsrfSetCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst();
    }

    private static String valueOf(String setCookie) {
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        return pair.substring(pair.indexOf('=') + 1);
    }
}
