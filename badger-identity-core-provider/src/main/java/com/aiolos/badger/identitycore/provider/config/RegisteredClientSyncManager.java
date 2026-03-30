package com.aiolos.badger.identitycore.provider.config;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class RegisteredClientSyncManager {

    private final RegisteredClientRepository registeredClientRepository;
    private final AuthServerProperties authServerProperties;

    public RegisteredClientSyncManager(RegisteredClientRepository registeredClientRepository, AuthServerProperties authServerProperties) {
        this.registeredClientRepository = registeredClientRepository;
        this.authServerProperties = authServerProperties;
    }

    @PostConstruct
    public void init() {
        syncClients();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (event == null || event.getKeys() == null || event.getKeys().isEmpty()) {
            return;
        }
        boolean changed = event.getKeys().stream()
                .anyMatch(key -> StringUtils.startsWith(key, "auth.clients"));
        if (changed) {
            syncClients();
        }
    }

    public void syncClients() {
        List<AuthServerProperties.Client> clients = authServerProperties.getClients();
        if (clients == null || clients.isEmpty()) {
            return;
        }
        for (AuthServerProperties.Client client : clients) {
            if (client == null || StringUtils.isBlank(client.getClientId())) {
                continue;
            }
            RegisteredClient existing = registeredClientRepository.findByClientId(client.getClientId());
            RegisteredClient.Builder builder = existing == null
                    ? RegisteredClient.withId(UUID.randomUUID().toString())
                    : RegisteredClient.from(existing);
            builder.clientId(client.getClientId());
            builder.clientName(StringUtils.defaultIfBlank(client.getClientName(), client.getClientId()));
            if (StringUtils.isNotBlank(client.getClientSecret())) {
                builder.clientSecret(client.getClientSecret());
            }
            builder.clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.add(resolveClientAuthenticationMethod(client.getClientAuthenticationMethod()));
            });
            builder.authorizationGrantTypes(grantTypes -> {
                grantTypes.clear();
                for (String grantType : client.getAuthorizationGrantTypes()) {
                    grantTypes.add(resolveGrantType(grantType));
                }
            });
            builder.redirectUris(redirectUris -> {
                redirectUris.clear();
                redirectUris.addAll(client.getRedirectUris());
            });
            builder.scopes(scopes -> {
                scopes.clear();
                scopes.addAll(new HashSet<>(client.getScopes()));
            });
            builder.clientSettings(ClientSettings.builder()
                    .requireProofKey(client.isRequireProofKey())
                    .requireAuthorizationConsent(client.isRequireAuthorizationConsent())
                    .build());
            registeredClientRepository.save(builder.build());
        }
    }

    private ClientAuthenticationMethod resolveClientAuthenticationMethod(String method) {
        if (StringUtils.isBlank(method)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        String normalized = method.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        if ("none".equals(normalized)) {
            return ClientAuthenticationMethod.NONE;
        }
        if ("client_secret_post".equals(normalized)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }
        return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
    }

    private AuthorizationGrantType resolveGrantType(String grantType) {
        String normalized = StringUtils.defaultString(grantType).trim().toLowerCase(Locale.ROOT).replace("-", "_");
        if ("refresh_token".equals(normalized)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        if ("client_credentials".equals(normalized)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        }
        return AuthorizationGrantType.AUTHORIZATION_CODE;
    }
}
