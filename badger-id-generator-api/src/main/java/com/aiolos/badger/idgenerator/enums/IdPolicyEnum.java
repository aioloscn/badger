package com.aiolos.badger.idgenerator.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IdPolicyEnum {

    USER_ID_POLICY(1, "用户ID策略"),
    ;
    
    private Integer primaryKey;
    private String desc;
}
