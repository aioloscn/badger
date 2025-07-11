package com.aiolos.badgeridentitycore.api;

import com.aiolos.badgeridentitycore.dto.AccountDTO;

public interface AccountTokenApi {

    String createToken(Long userId);

    AccountDTO getUserByToken(String token);

    Long getOrCreateAnonymousId(String deviceId);
}
