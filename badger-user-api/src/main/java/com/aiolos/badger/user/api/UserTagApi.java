package com.aiolos.badger.user.api;

import com.aiolos.badger.enums.UserTagEnum;

public interface UserTagApi {

    boolean setTag(Long userId, UserTagEnum userTagEnum);

    boolean cancelTag(Long userId, UserTagEnum userTagEnum);

    boolean checkTag(Long userId, UserTagEnum userTagEnum);
}
