package com.aiolos.badger.identitycore.api;

import com.aiolos.badger.identitycore.dto.AccountDTO;

public interface AccountTokenApi {

    String createToken(Long userId);

    AccountDTO getUserByToken(String token);

    Long getOrCreateAnonymousId(String deviceId);
}
