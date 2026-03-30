package com.aiolos.badger.identitycore.provider.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class OAuthSessionTraceFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("/login".equals(path) || "/oauth2/authorize".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String sessionIdCookie = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    sessionIdCookie = cookie.getValue();
                    break;
                }
            }
        }
        String requestedSessionId = request.getRequestedSessionId();
        HttpSession before = request.getSession(false);
        String beforeSessionId = before == null ? null : before.getId();

        SetCookieCaptureResponseWrapper wrapper = new SetCookieCaptureResponseWrapper(response);
        filterChain.doFilter(request, wrapper);

        HttpSession after = request.getSession(false);
        String afterSessionId = after == null ? null : after.getId();
        log.info("OAuthTrace path={} method={} query={} referer={} jsessionid_cookie={} requested_session_id={} before_session_id={} after_session_id={} status={} set_cookie={}",
                request.getRequestURI(),
                request.getMethod(),
                request.getQueryString(),
                request.getHeader("Referer"),
                sessionIdCookie,
                requestedSessionId,
                beforeSessionId,
                afterSessionId,
                wrapper.getStatus(),
                wrapper.getSetCookies());
    }

    private static class SetCookieCaptureResponseWrapper extends HttpServletResponseWrapper {
        private final List<String> setCookies = new ArrayList<>();

        public SetCookieCaptureResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                setCookies.add(value);
            }
            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                setCookies.clear();
                setCookies.add(value);
            }
            super.setHeader(name, value);
        }

        public List<String> getSetCookies() {
            return setCookies;
        }
    }
}
