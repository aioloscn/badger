package com.aiolos.badger.identitycore.api;

import com.aiolos.badger.identitycore.dto.AccountDTO;
import com.aiolos.badger.identitycore.dto.AccountTokenDTO;

public interface AccountTokenApi {

    AccountTokenDTO createToken(Long userId);

    AccountTokenDTO refreshToken(String refreshToken);

    AccountDTO getUserByToken(String token);

    Long getOrCreateAnonymousId(String deviceId);
}
