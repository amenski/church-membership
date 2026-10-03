package io.github.membertracker;

import io.github.membertracker.domain.enumeration.UserRole;
import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full login / refresh / logout flow through the real security chain on in-memory H2, default profile. */
@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:authflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** The value of the XSRF-TOKEN cookie the browser got from its first GET. */
    private String xsrf;

    @BeforeEach
    void createUser() throws Exception {
        xsrf = xsrfCookieValue(mockMvc.perform(get("/api/users/me")).andReturn().getResponse());
        userRepository.save(new User(Email.of(EMAIL), passwordEncoder.encode(PASSWORD), UserRole.MEMBER));
    }

    @AfterEach
    void deleteUser() {
        userJpaRepository.findByEmail(EMAIL).ifPresent(userJpaRepository::delete);
    }

    /** What the web app does: echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. */
    private MockHttpServletRequestBuilder withXsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(XSRF_COOKIE, xsrf)).header(XSRF_HEADER, xsrf);
    }

    private static String xsrfSetCookie(MockHttpServletResponse response) {
        return headerStartingWith(response.getHeaders(HttpHeaders.SET_COOKIE), XSRF_COOKIE);
    }

    private static String xsrfCookieValue(MockHttpServletResponse response) {
        return valueOf(xsrfSetCookie(response));
    }

    private static final String LOGIN_BODY = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(withXsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isOk())
            .andReturn().getResponse();
    }

    private static String headerStartingWith(List<String> headers, String name) {
        return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElseThrow();
    }

    private static boolean clearsXsrfCookie(String setCookie) {
        return setCookie.startsWith(XSRF_COOKIE + "=") && (setCookie.contains("Max-Age=0") || valueOf(setCookie).isEmpty());
    }

    private static String valueOf(String setCookie) {
        String pair = setCookie.substring(0, setCookie.indexOf(';'));
        return pair.substring(pair.indexOf('=') + 1);
    }

    @Test
    void fullFlow() throws Exception {
        // login
        MockHttpServletResponse loginResponse = login();
        List<String> setCookies = loginResponse.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        String sid = headerStartingWith(setCookies, "sid");
        String sidRefresh = headerStartingWith(setCookies, "sid_refresh");
        assertThat(sid).contains("Path=/;");
        assertThat(sidRefresh).contains("Path=/api/auth");
        String access = valueOf(sid);
        String refresh = valueOf(sidRefresh);

        // access cookie works
        mockMvc.perform(get("/api/users/me").cookie(new Cookie("sid", access)))
            .andExpect(status().isOk());

        // refresh with the refresh cookie
        MockHttpServletResponse refreshed = mockMvc.perform(withXsrf(post("/api/auth/refresh"))
                .cookie(new Cookie("sid_refresh", refresh)))
            .andExpect(status().isNoContent())
            .andReturn().getResponse();
        List<String> newCookies = refreshed.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(newCookies).hasSize(2);
        assertThat(headerStartingWith(newCookies, "sid_refresh")).contains("Path=/api/auth");

        // an access token is not a refresh token
        mockMvc.perform(withXsrf(post("/api/auth/refresh")).cookie(new Cookie("sid_refresh", access)))
            .andExpect(status().isBadRequest());

        // a refresh token is not an access token: the request stays unauthenticated
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refresh))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Authentication required"));

        // logout expires both cookies
        List<String> cleared = mockMvc.perform(withXsrf(post("/api/auth/logout")))
            .andExpect(status().isNoContent())
            .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cleared).hasSize(2);
        assertThat(headerStartingWith(cleared, "sid")).contains("Max-Age=0");
        String clearedRefresh = headerStartingWith(cleared, "sid_refresh");
        assertThat(clearedRefresh).contains("Max-Age=0").contains("Path=/api/auth");

        // after logout the browser holds no cookie
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void noCredentialsGives401Problem() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.detail").value("Authentication required"))
            .andExpect(jsonPath("$.path").value("/api/users/me"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameError() throws Exception {
        String unknown = mockMvc.perform(withXsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();
        String wrong = mockMvc.perform(withXsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Wrong-pass1!\"}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        String unknownDetail = JsonPath.read(unknown, "$.detail");
        assertThat(unknownDetail).isEqualTo("Invalid email or password").isEqualTo(JsonPath.<String>read(wrong, "$.detail"));
    }

    @Test
    void firstGetSetsTheReadableXsrfCookieEvenOn401() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse();

        String setCookie = xsrfSetCookie(response);
        assertThat(setCookie).contains("Path=/").doesNotContain("HttpOnly");
        // The servlet cookie carries SameSite as an attribute (Tomcat writes it; the mock's header text omits it)
        Cookie cookie = response.getCookie(XSRF_COOKIE);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(valueOf(setCookie)).isNotBlank();
    }

    @Test
    void loginWithoutTheHeaderIsRejectedWith403Problem() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));
    }

    @Test
    void loginWithCookieButNoHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie(XSRF_COOKIE, xsrf))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));
    }

    @Test
    void headerThatDoesNotMatchTheCookieIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie(XSRF_COOKIE, xsrf))
                .header(XSRF_HEADER, "some-other-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));
    }

    @Test
    void writesAfterLoginNeedTheHeaderToo() throws Exception {
        String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));
        String body = "{\"firstName\":\"Flo\"}";

        mockMvc.perform(put("/api/users/me/profile").cookie(new Cookie("sid", access))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Invalid or missing CSRF token"));

        mockMvc.perform(withXsrf(put("/api/users/me/profile")).cookie(new Cookie("sid", access))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("sid", access)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAuthenticatedRequestKeepsTheXsrfCookieSoConsecutiveWritesSucceed() throws Exception {
        String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));

        // an authenticated GET that carries the cookie must not clear it
        List<String> setCookies = mockMvc.perform(withXsrf(get("/api/users/me")).cookie(new Cookie("sid", access)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).noneMatch(AuthFlowIntegrationTest::clearsXsrfCookie);

        // two writes in a row with the same cookie/header pair and no GET in between
        for (String name : new String[] {"Flo", "Flora"}) {
            MockHttpServletResponse response = mockMvc.perform(withXsrf(put("/api/users/me/profile"))
                    .cookie(new Cookie("sid", access))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(AuthFlowIntegrationTest::clearsXsrfCookie);
        }
    }

    @Test
    void getsNeverNeedTheHeader() throws Exception {
        String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));

        mockMvc.perform(get("/api/users/me").cookie(new Cookie("sid", access)))
            .andExpect(status().isOk());
    }

    @Test
    void sessionOfADeletedUserAnswers401AndSignInStillWorks() throws Exception {
        String otherEmail = "other-flow@example.com";
        userRepository.save(new User(Email.of(otherEmail), passwordEncoder.encode(PASSWORD), UserRole.MEMBER));
        try {
            String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));
            userJpaRepository.findByEmail(EMAIL).ifPresent(userJpaRepository::delete);

            mockMvc.perform(get("/api/users/me").cookie(new Cookie("sid", access)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication required"));

            mockMvc.perform(withXsrf(post("/api/auth/login")).cookie(new Cookie("sid", access))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + otherEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        } finally {
            userJpaRepository.findByEmail(otherEmail).ifPresent(userJpaRepository::delete);
        }
    }

    @Test
    void changingThePasswordThenSigningInWithTheNewOne() throws Exception {
        String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));
        String newPassword = "Newpass1-x";

        mockMvc.perform(withXsrf(put("/api/users/me/password")).cookie(new Cookie("sid", access))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password changed successfully"));

        mockMvc.perform(withXsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + newPassword + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(withXsrf(post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void aWrongCurrentPasswordIsReportedAsSuch() throws Exception {
        String access = valueOf(headerStartingWith(login().getHeaders(HttpHeaders.SET_COOKIE), "sid"));

        mockMvc.perform(withXsrf(put("/api/users/me/password")).cookie(new Cookie("sid", access))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"nope\",\"newPassword\":\"Newpass1-x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }
}
