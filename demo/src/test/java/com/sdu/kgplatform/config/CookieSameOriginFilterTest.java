package com.sdu.kgplatform.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookieSameOriginFilterTest {

    private final CookieSameOriginFilter filter = new CookieSameOriginFilter("JSESSIONID");

    @Test
    void blocksCrossOriginCookieWriteRequest() throws Exception {
        MockHttpServletRequest request = writeRequest("/api/graph/1");
        request.setServerName("app.example.com");
        request.setServerPort(443);
        request.setScheme("https");
        request.addHeader("Origin", "https://evil.example");
        request.setCookies(new Cookie("JSESSIONID", "abc"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsSameOriginCookieWriteRequest() throws Exception {
        MockHttpServletRequest request = writeRequest("/api/graph/1");
        request.setServerName("app.example.com");
        request.setServerPort(443);
        request.setScheme("https");
        request.addHeader("Origin", "https://app.example.com");
        request.setCookies(new Cookie("JSESSIONID", "abc"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void allowsReadRequestWithCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/graph/public");
        request.setCookies(new Cookie("JSESSIONID", "abc"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void allowsWriteRequestWithoutSessionCookie() throws Exception {
        MockHttpServletRequest request = writeRequest("/api/feedback");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest writeRequest(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
