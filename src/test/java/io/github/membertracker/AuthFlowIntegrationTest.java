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

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUser() {
        userRepository.save(new User(Email.of(EMAIL), passwordEncoder.encode(PASSWORD), UserRole.MEMBER));
    }

    @AfterEach
    void deleteUser() {
        userJpaRepository.findByEmail(EMAIL).ifPresent(userJpaRepository::delete);
    }

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse();
    }

    private static String headerStartingWith(List<String> headers, String name) {
        return headers.stream().filter(h -> h.startsWith(name + "=")).findFirst().orElseThrow();
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
        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("sid_refresh", refresh)))
            .andExpect(status().isNoContent())
            .andReturn().getResponse();
        List<String> newCookies = refreshed.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(newCookies).hasSize(2);
        assertThat(headerStartingWith(newCookies, "sid_refresh")).contains("Path=/api/auth");

        // an access token is not a refresh token
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("sid_refresh", access)))
            .andExpect(status().isBadRequest());

        // a refresh token is not an access token: the request stays unauthenticated.
        // (An unauthenticated request currently answers 403, not 401: no authentication entry point is configured.)
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refresh))
            .andExpect(status().isForbidden());

        // logout expires both cookies
        List<String> cleared = mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent())
            .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cleared).hasSize(2);
        assertThat(headerStartingWith(cleared, "sid")).contains("Max-Age=0");
        String clearedRefresh = headerStartingWith(cleared, "sid_refresh");
        assertThat(clearedRefresh).contains("Max-Age=0").contains("Path=/api/auth");
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameError() throws Exception {
        String unknown = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();
        String wrong = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Wrong-pass1!\"}"))
            .andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();

        String unknownDetail = JsonPath.read(unknown, "$.detail");
        assertThat(unknownDetail).isEqualTo("Invalid email or password").isEqualTo(JsonPath.<String>read(wrong, "$.detail"));
    }
}
