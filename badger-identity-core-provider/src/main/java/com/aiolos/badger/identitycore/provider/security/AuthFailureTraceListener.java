package com.aiolos.badger.identitycore.provider.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthFailureTraceListener {

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication() == null ? null : event.getAuthentication().getPrincipal();
        String principalText = principal == null ? null : String.valueOf(principal);
        String exType = event.getException() == null ? null : event.getException().getClass().getSimpleName();
        String exMsg = event.getException() == null ? null : event.getException().getMessage();
        log.warn("AuthFailure principal={} exception_type={} exception_message={}", principalText, exType, exMsg);
    }
}
