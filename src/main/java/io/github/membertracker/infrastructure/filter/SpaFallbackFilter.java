package io.github.membertracker.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lets a browser reload or open a client route such as /login, /members or /members/3: the request is
 * forwarded to /index.html and the Vue router decides what to show.
 * <p>
 * Only a page request is forwarded: GET or HEAD, not under /api, /v3 (API docs), /swagger-ui or /error,
 * no file extension in the last path segment, and the client accepts text/html. So a missing file such as
 * /assets/missing.js keeps its 404, and an unknown /api path keeps its JSON 401 or 404.
 * <p>
 * It runs inside the security chain, after the security headers and the CSRF cookie are set and before the
 * authorization check. A forward is not filtered again, so the forwarded /index.html is served without
 * widening the permit list.
 */
public class SpaFallbackFilter extends OncePerRequestFilter {

    private static final String INDEX = "/index.html";
    private static final List<String> NOT_PAGES = List.of("/api", "/v3", "/swagger-ui", "/error");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPageRequest(request)) {
            request.getRequestDispatcher(INDEX).forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isPageRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !isReserved(path) && !hasExtension(path) && acceptsHtml(request.getHeader("Accept"));
    }

    private static boolean isReserved(String path) {
        return NOT_PAGES.stream().anyMatch(prefix -> path.regionMatches(true, 0, prefix, 0, prefix.length())
            && (path.length() == prefix.length() || path.charAt(prefix.length()) == '/'));
    }

    private static boolean hasExtension(String path) {
        return path.substring(path.lastIndexOf('/') + 1).indexOf('.') >= 0;
    }

    /** A missing Accept header, text/html, text/* and any type all count; a browser navigation sends text/html. */
    private static boolean acceptsHtml(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream().anyMatch(type -> type.isCompatibleWith(MediaType.TEXT_HTML));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
