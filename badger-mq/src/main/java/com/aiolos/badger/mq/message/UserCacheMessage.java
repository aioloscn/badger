package com.aiolos.badger.mq.message;

import com.aiolos.badger.enums.UserCacheEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserCacheMessage implements Serializable {
    
    private Long userId;
    private UserCacheEnum userCacheEnum;
}
