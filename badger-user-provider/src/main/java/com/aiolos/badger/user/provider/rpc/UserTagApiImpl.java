package com.aiolos.badger.user.provider.rpc;

import com.aiolos.badger.enums.UserTagEnum;
import com.aiolos.badger.user.api.UserTagApi;
import com.aiolos.badger.user.provider.service.BadgerUserTagService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class UserTagApiImpl implements UserTagApi {
    
    @Resource
    private BadgerUserTagService badgerUserTagService;
    
    @Override
    public boolean setTag(Long userId, UserTagEnum userTagEnum) {
        return badgerUserTagService.setTag(userId, userTagEnum);
    }

    @Override
    public boolean cancelTag(Long userId, UserTagEnum userTagEnum) {
        return badgerUserTagService.cancelTag(userId, userTagEnum);
    }

    @Override
    public boolean checkTag(Long userId, UserTagEnum userTagEnum) {
        return badgerUserTagService.checkTag(userId, userTagEnum);
    }
}
