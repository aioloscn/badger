package com.aiolos.badgerenums.exceptions;

import com.aiolos.common.enums.errors.CommonError;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ServiceExceptionEnum implements CommonError {
    
    GET_ID_SECTION_ERROR(11001, "获取id段失败"),
    ;
    
    
    private Integer errCode;
    private String errMsg;

    @Override
    public CommonError setErrMsg(String s) {
        return null;
    }
}
