package io.github.membertracker.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenAPI openApi = new OpenApiConfig().openApi(new AuthProperties());

    @Test
    void hasTitleAndVersion() {
        assertEquals("MemberTracker API", openApi.getInfo().getTitle());
        assertEquals("1.0", openApi.getInfo().getVersion());
    }

    @Test
    void definesCookieAuthSchemeWithTheAccessCookieName() {
        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get("cookieAuth");

        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.COOKIE, scheme.getIn());
        assertEquals("sid", scheme.getName());
    }

    @Test
    void appliesCookieAuthGlobally() {
        assertTrue(openApi.getSecurity().stream().anyMatch(r -> r.containsKey("cookieAuth")));
    }
}
