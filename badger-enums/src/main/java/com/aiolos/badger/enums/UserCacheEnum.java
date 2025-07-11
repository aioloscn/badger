package com.aiolos.badger.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserCacheEnum {

    USER_INFO_CACHE(1, "用户信息缓存"),
    USER_TAG_CACHE(2, "用户标签缓存"),
    ;
    
    private Integer code;
    private String desc;
}
