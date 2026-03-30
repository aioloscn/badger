package com.aiolos.badger.identitycore.provider.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.util.Set;

@Controller
public class OAuthLogoutController {

    private static final Set<String> ALLOWED_REDIRECT_ORIGINS = Set.of(
            "https://plaza.aiolos.com",
            "https://live.aiolos.com",
            "https://www.aiolos.com",
            "https://localhost"
    );

    @GetMapping("/oauth2/front-logout")
    public String frontLogout(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:" + resolveRedirectUri(redirectUri);
    }

    private String resolveRedirectUri(String redirectUri) {
        if (StringUtils.isBlank(redirectUri)) {
            return "/login?logout=1";
        }
        try {
            URI uri = URI.create(redirectUri.trim());
            String scheme = StringUtils.defaultString(uri.getScheme()).toLowerCase();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                return "/login?logout=1";
            }
            String host = StringUtils.defaultString(uri.getHost()).toLowerCase();
            if (StringUtils.isBlank(host)) {
                return "/login?logout=1";
            }
            String origin = scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            if (!ALLOWED_REDIRECT_ORIGINS.contains(origin)) {
                return "/login?logout=1";
            }
            return uri.toString();
        } catch (Exception ignored) {
            return "/login?logout=1";
        }
    }
}
