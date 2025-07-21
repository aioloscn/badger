package com.aiolos.badger.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public enum MsgSendResultEnum implements Serializable {
    
    SUCCESS(1, "发送成功"),
    FAIL(2, "发送失败"),
    PARAM_ERROR(3, "消息参数异常");
    
    private Integer code;
    private String desc;
}
