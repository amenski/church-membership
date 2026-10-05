package io.github.membertracker.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Spring MVC configuration for serving the Vue.js frontend that the build copies into the jar's classpath:/static/.
 * <p>
 * The built pages sit at the site root, because the built index.html loads /assets/...:
 * 1. /index.html is never cached without revalidation, so a new release shows up at once.
 * 2. /assets/** carries a content hash in every file name, so it is cached for a year and marked immutable.
 * 3. Client routes (/login, /members, ...) are forwarded to /index.html by SpaFallbackFilter in the security chain.
 * 4. CORS configuration for development mode (allows the frontend dev server on a different port).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Enable CORS for development - allows Vue.js dev server to communicate with backend
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8080", "http://localhost:8081", "http://localhost:8082")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
