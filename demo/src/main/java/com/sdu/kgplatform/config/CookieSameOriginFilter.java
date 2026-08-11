package com.sdu.kgplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class CookieSameOriginFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final String sessionCookieName;

    public CookieSameOriginFilter(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!UNSAFE_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        return path.startsWith("/assets/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/libs/")
                || path.startsWith("/uploads/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!hasSessionCookie(request) || isSameOrigin(request) || isLocalRequestWithoutOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        reject(request, response);
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (sessionCookieName.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return matchesRequestOrigin(origin, request);
        }

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return matchesRequestOrigin(referer, request);
        }

        return false;
    }

    private boolean matchesRequestOrigin(String headerValue, HttpServletRequest request) {
        try {
            Origin headerOrigin = Origin.fromUri(new URI(headerValue));
            Origin requestOrigin = Origin.fromRequest(request);
            return headerOrigin.equals(requestOrigin);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isLocalRequestWithoutOrigin(HttpServletRequest request) {
        if (hasHeader(request, "Origin") || hasHeader(request, "Referer")) {
            return false;
        }

        String host = firstHeaderValue(request, "X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        return isLoopbackHost(stripPort(host));
    }

    private boolean hasHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank();
    }

    private String firstHeaderValue(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private String stripPort(String host) {
        String value = host.trim();
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            return closingBracket >= 0 ? value.substring(1, closingBracket) : value;
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && value.indexOf(':', colon + 1) >= 0) {
            return value;
        }
        return colon >= 0 ? value.substring(0, colon) : value;
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || "127.0.0.1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized)
                || "::1".equals(normalized);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/user/api/")) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"cross-origin cookie write request blocked\"}");
            return;
        }
        response.sendError(HttpStatus.FORBIDDEN.value(), "Cross-origin cookie write request blocked");
    }

    private record Origin(String scheme, String host, int port) {
        private static Origin fromUri(URI uri) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                throw new IllegalArgumentException("Missing origin components");
            }
            return new Origin(scheme.toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT), normalizedPort(uri.getScheme(), uri.getPort()));
        }

        private static Origin fromRequest(HttpServletRequest request) {
            String scheme = firstForwardedValue(request, "X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) {
                scheme = request.getScheme();
            }

            String forwardedHost = firstForwardedValue(request, "X-Forwarded-Host");
            String host = forwardedHost;
            int port = -1;
            if (host == null || host.isBlank()) {
                host = request.getServerName();
                port = request.getServerPort();
            } else {
                HostAndPort hostAndPort = splitHostAndPort(host);
                host = hostAndPort.host();
                port = hostAndPort.port();
            }

            String forwardedPort = firstForwardedValue(request, "X-Forwarded-Port");
            if (forwardedPort != null && !forwardedPort.isBlank()) {
                port = Integer.parseInt(forwardedPort.trim());
            }

            return new Origin(scheme.toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT), normalizedPort(scheme, port));
        }

        private static String firstForwardedValue(HttpServletRequest request, String name) {
            String value = request.getHeader(name);
            if (value == null) {
                return null;
            }
            int comma = value.indexOf(',');
            return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
        }

        private static HostAndPort splitHostAndPort(String value) {
            String host = value.trim();
            if (host.startsWith("[")) {
                int closingBracket = host.indexOf(']');
                if (closingBracket < 0) {
                    return new HostAndPort(host, -1);
                }
                int port = -1;
                if (host.length() > closingBracket + 2 && host.charAt(closingBracket + 1) == ':') {
                    port = Integer.parseInt(host.substring(closingBracket + 2));
                }
                return new HostAndPort(host.substring(1, closingBracket), port);
            }

            int colon = host.lastIndexOf(':');
            if (colon > 0 && host.indexOf(':') == colon) {
                return new HostAndPort(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1)));
            }
            return new HostAndPort(host, -1);
        }

        private static int normalizedPort(String scheme, int port) {
            if (port > 0) {
                return port;
            }
            return "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
    }

    private record HostAndPort(String host, int port) {
    }
}
