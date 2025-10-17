# Backend Cookie Auth (Stateless JWT in HttpOnly Cookies)

Decision Log (summary)
- Status: Accepted
- Last updated: 2025-09-05
- Decision: Use two HttpOnly cookies (`sid`, `sid_refresh`) for browser auth; keep `Authorization: Bearer` as a temporary fallback for mobile/legacy.
- Rationale: Keep tokens out of JS, support SSR/SPA, remain stateless, and fit Clean Architecture.
- Ops/Security: `SameSite=Lax` by default; `Secure=true` in prod; if cross‑site is required, use `SameSite=None; Secure` and enable CSRF token cookie.
- Rollout: Enabled in dev/staging first; production after CORS origins restricted and cookie flags set. Deprecate Bearer for web later.


Goal
- Use stateless JWT in two HttpOnly cookies: access (`sid`) and refresh (`sid_refresh`).
- Keep tokens out of JS, enable SSR/SPA via cookies, preserve Clean Architecture.

What You’ll Change
- Security config: stateless, CORS with credentials, optional CSRF token cookie for cross‑site.
- JWT filter: read JWT from cookie first, then `Authorization` header (for transition).
- Auth endpoints: `/v1/auth/login`, `/v1/auth/refresh`, `/v1/auth/logout` set/rotate/clear cookies.
- Config: cookie names/flags, TTLs, allowed origins.

Cookie Design
- Access: `sid`, HttpOnly, `Path=/`, short TTL (15–30m).
- Refresh: `sid_refresh`, HttpOnly, `Path=/v1/auth`, long TTL (15–30d).
- Prod flags: `Secure=true`, `SameSite=Lax` (same‑site). If cross‑site is required, use `SameSite=None; Secure` AND CSRF token.
- Optional: `Domain=.example.com` for subdomain sharing.

Config (application.yml)
Add these properties; wire via `@ConfigurationProperties` or `@Value` in infra:

```yaml
auth:
  cookies:
    enabled: true
    access-name: sid
    refresh-name: sid_refresh
    secure: ${COOKIE_SECURE:true}
    same-site: ${COOKIE_SAMESITE:Lax}   # Lax|Strict|None
    domain: ${COOKIE_DOMAIN:}           # e.g. .example.com
  access-ttl-seconds: ${ACCESS_TTL:1800}    # 30m
  refresh-ttl-seconds: ${REFRESH_TTL:2592000} # 30d
```

Also ensure CORS origins are restricted and credentials are allowed:

```yaml
app:
  cors:
    allowed-origins: "https://app.example.com,https://admin.example.com"
```

SecurityConfig
- Keep `SessionCreationPolicy.STATELESS`.
- CORS: allow credentials and restrict origins. Include headers used for CSRF when enabled.
- CSRF: 
  - Same‑site only: you may disable CSRF for API routes.
  - Cross‑site: use `CookieCsrfTokenRepository.withHttpOnlyFalse()` and require `X-CSRF-Token` on non‑GET.
- Register the JWT cookie filter before `UsernamePasswordAuthenticationFilter`.

Minimal CORS tweak (add allowCredentials):

```java
configuration.setAllowCredentials(true);
configuration.setAllowedHeaders(List.of("authorization","content-type","x-csrf-token"));
```

JWT Cookie Filter
Update `JwtAuthenticationFilter` to read `sid` cookie first (fallback to `Authorization`). Example:

```java
private final String accessCookieName = "sid"; // inject via config

private String getJwtFromRequest(HttpServletRequest request) {
    if (request.getCookies() != null) {
        for (Cookie c : request.getCookies()) {
            if (accessCookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
    }
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
        return bearer.substring(7);
    }
    return null;
}
```

Cookie Builder (utility)
Use `ResponseCookie` to set flags consistently:

```java
ResponseCookie buildCookie(String name, String value, long maxAgeSeconds, String path,
                           boolean secure, String sameSite, @Nullable String domain) {
    ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .path(path)
        .maxAge(maxAgeSeconds)
        .sameSite(sameSite);
    if (domain != null && !domain.isBlank()) b.domain(domain);
    return b.build();
}
```

Auth Endpoints (controller)
- `POST /v1/auth/login`: validate credentials via use case; issue access+refresh cookies; return `204 No Content` (optional: also return token JSON during migration).
- `POST /v1/auth/refresh`: read `sid_refresh`, validate/rotate, set new cookies; `204`.
- `POST /v1/auth/logout`: clear both cookies with `Max-Age=0` on their paths; `204`.

Example set/clear cookies:

```java
ResponseCookie access = buildCookie(accessName, accessJwt, accessTtl, "/", secure, sameSite, domain);
ResponseCookie refresh = buildCookie(refreshName, refreshJwt, refreshTtl, "/v1/auth", secure, sameSite, domain);
return ResponseEntity.noContent()
    .header(HttpHeaders.SET_COOKIE, access.toString())
    .header(HttpHeaders.SET_COOKIE, refresh.toString())
    .build();

// Clear
ResponseCookie clear(String name, String path) {
    return ResponseCookie.from(name, "").httpOnly(true).secure(secure).path(path).maxAge(0).sameSite(sameSite)
        .domain(Optional.ofNullable(domain).orElse(""))
        .build();
}
```

CSRF (when cross‑site)
- Enable cookie CSRF token and require header on non‑safe methods:

```java
http.csrf(csrf -> csrf
   .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
// Frontend must echo `X-CSRF-Token` header from cookie value on POST/PUT/PATCH/DELETE
```

User Endpoint
- `/v1/users/me` already exists; it will use the `Authentication` set by the cookie filter.

Testing (manual)
- Login: `curl -i -c jar.txt -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"u","password":"p"}'` → two `Set-Cookie`.
- Authenticated: `curl -b jar.txt http://localhost:8080/api/v1/users/me` → 200.
- Refresh: `curl -i -b jar.txt -c jar.txt -X POST http://localhost:8080/api/v1/auth/refresh` → new cookies.
- Logout: `curl -i -b jar.txt -X POST http://localhost:8080/api/v1/auth/logout` → cookies cleared; `/v1/users/me` → 401.

Rollout
- Keep `Authorization: Bearer` support temporarily (filter fallback) to avoid breaking clients; remove later.

Pitfalls
- Always `Secure` in prod; for `localhost` in dev set `auth.cookies.secure=false`.
- For cross‑subdomain apps, set `Domain=.example.com` and `SameSite=None; Secure` AND enforce CSRF token.
- Keep access TTL short; rotate refresh; consider tracking `jti` on refresh if revocation is required later.

Touchpoints
- `SecurityConfig` (CORS allowCredentials, optional CSRF repo, register filter)
- `JwtAuthenticationFilter` (cookie lookup)
- `AuthController` (login/refresh/logout responses set cookies)
- `application.yml` (new properties)

## Out of Scope: Mobile
- Web uses HttpOnly cookies; keep `Authorization: Bearer` supported for native/mobile.
- Keep `/v1/auth/login` returning JSON (e.g., `accessToken`, `expiresIn`) in addition to setting cookies for web.
- Optionally add a JSON token refresh endpoint for mobile; cookie‑based `/v1/auth/refresh` remains for web.
- CSRF is a browser concern only; not required for native clients.
- Mobile should store tokens in Keychain/Keystore and use short access TTL + refresh rotation.
