package io.github.membertracker.infrastructure.filter;

import io.github.membertracker.infrastructure.config.AuthProperties;
import io.github.membertracker.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hs256-signing";
    private static final String EMAIL = "user@example.com";

    private final UserDetails user = new User(EMAIL, "pw", List.of());
    private JwtAuthenticationFilter filter;
    private AuthProperties props;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        props = new AuthProperties();
        props.setJwtSecret(SECRET);
        props.getCookies().setAccessName("sid");
        props.getCookies().setRefreshName("sid_refresh");
        filter = new JwtAuthenticationFilter();
        filter.setAuthProperties(props);
        filter.setUserDetailsService(username -> user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private String access() {
        return JwtUtils.generateAccessToken(user, SECRET, 60);
    }

    private String refresh() {
        return JwtUtils.generateRefreshToken(user, SECRET, 60);
    }

    @Test
    void accessTokenInCookieAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("sid", access()));

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    void accessTokenInBearerHeaderAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access());

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void refreshTokenInCookieDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("sid", refresh()));

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshTokenInBearerHeaderDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + refresh());

        run(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
