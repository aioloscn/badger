package com.aiolos.badger.identitycore.provider.controller;

import com.aiolos.badger.identitycore.provider.service.LoginRelayService;
import com.aiolos.common.model.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;

@Slf4j
@Controller
public class SmsLoginController {

    private final LoginRelayService loginRelayService;
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SmsLoginController(LoginRelayService loginRelayService) {
        this.loginRelayService = loginRelayService;
    }

    @GetMapping("/login/sms")
    public ModelAndView smsLoginPage(@RequestParam(value = "redirect", required = false) String redirect) {
        ModelAndView mv = new ModelAndView("login-sms");
        mv.addObject("redirect", StringUtils.defaultString(redirect));
        return mv;
    }

    @PostMapping("/login/sms-code")
    @ResponseBody
    public CommonResponse sendSmsCode(@RequestParam("phone") String phone) {
        return loginRelayService.sendSmsCode(phone);
    }

    @PostMapping("/login/sms")
    public String smsLogin(@RequestParam("phone") String phone,
                           @RequestParam("code") String code,
                           @RequestParam(value = "redirect", required = false) String redirect,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        UserDetails userDetails = loginRelayService.authenticateBySms(phone, code);
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && StringUtils.isNotBlank(savedRequest.getRedirectUrl())) {
            return "redirect:" + savedRequest.getRedirectUrl();
        }
        if (StringUtils.isNotBlank(redirect)) {
            if (isLoginPath(redirect)) {
                return "redirect:/";
            }
            return "redirect:" + redirect;
        }
        return "redirect:/";
    }

    private boolean isLoginPath(String redirect) {
        try {
            URI uri = URI.create(redirect.trim());
            String path = uri.getPath();
            return "/login".equals(path);
        } catch (Exception ignored) {
            return false;
        }
    }
}
