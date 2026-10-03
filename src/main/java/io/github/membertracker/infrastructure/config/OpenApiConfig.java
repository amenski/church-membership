package io.github.membertracker.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI openApi(AuthProperties authProperties) {
        SecurityScheme cookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(authProperties.getCookies().getAccessName());

        return new OpenAPI()
                .info(new Info()
                        .title("MemberTracker API")
                        .description("Church membership and dues. "
                                + "Sign in via POST /api/auth/login; the browser keeps the cookie.")
                        .version("1.0"))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH, cookieScheme))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
    }
}
