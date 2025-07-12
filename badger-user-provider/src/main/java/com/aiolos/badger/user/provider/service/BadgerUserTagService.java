package com.aiolos.badger.user.provider.service;

import com.aiolos.badger.enums.UserTagEnum;

public interface BadgerUserTagService {

    boolean setTag(Long userId, UserTagEnum userTagEnum);
    
    boolean cancelTag(Long userId, UserTagEnum userTagEnum);
    
    boolean checkTag(Long userId, UserTagEnum userTagEnum);
}
