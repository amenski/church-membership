package io.github.membertracker.infrastructure.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SpaFallbackFilterTest {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml,*/*;q=0.8";

    private static MockHttpServletRequest request(String method, String path, String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/members", "/members/3", "/my-dues", "/a/b/c/"})
    void clientRoutesAreForwardedToTheIndexPage(String path) throws Exception {
        MockHttpServletRequest request = request("GET", path, BROWSER_ACCEPT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SpaFallbackFilter().doFilter(request, response, chain);

        assertThat(response.getForwardedUrl()).isEqualTo("/index.html");
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/api/", "/api/members", "/api/nope", "/API/members", "/v3/api-docs", "/swagger-ui",
        "/swagger-ui/index.html", "/error", "/assets/missing.js", "/favicon-x.png", "/index.html", "/members.json"})
    void everythingElseGoesOnDown(String path) throws Exception {
        MockHttpServletRequest request = request("GET", path, BROWSER_ACCEPT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SpaFallbackFilter().doFilter(request, response, chain);

        assertThat(response.getForwardedUrl()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void apiPrefixLookalikeIsStillAPage() {
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/apiary", BROWSER_ACCEPT))).isTrue();
    }

    @Test
    void headIsForwardedButOtherMethodsAreNot() {
        assertThat(SpaFallbackFilter.isPageRequest(request("HEAD", "/members", BROWSER_ACCEPT))).isTrue();
        for (String method : new String[] {"POST", "PUT", "DELETE", "OPTIONS", "PATCH"}) {
            assertThat(SpaFallbackFilter.isPageRequest(request(method, "/members", BROWSER_ACCEPT))).as(method).isFalse();
        }
    }

    @Test
    void acceptHeaderDecides() {
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", null))).isTrue();
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", "*/*"))).isTrue();
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", "text/html"))).isTrue();
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", "application/json"))).isFalse();
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", "image/png"))).isFalse();
        assertThat(SpaFallbackFilter.isPageRequest(request("GET", "/members", "not a media type"))).isFalse();
    }
}
