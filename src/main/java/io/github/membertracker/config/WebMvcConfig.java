package io.github.membertracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Spring MVC configuration for serving the Vue.js frontend as static resources.
 * <p>
 * This configuration enables:
 * 1. Serving static frontend files from the classpath:/static/ directory
 * 2. SPA routing fallback - returns index.html for non-existent routes to allow Vue Router to handle client-side routing
 * 3. CORS configuration for development mode (allows frontend dev server on different port)
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
        // Serve static resources from classpath:/static/ with SPA fallback
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        } else {
                            // SPA fallback: return index.html for non-existent resources
                            // This allows Vue Router to handle client-side routing
                            return new ClassPathResource("/static/index.html");
                        }
                    }
                });
    }
}