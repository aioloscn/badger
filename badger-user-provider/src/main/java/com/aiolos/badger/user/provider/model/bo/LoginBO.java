package com.aiolos.badger.user.provider.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class LoginBO {
    
    @Schema(description = "手机号")
    private String phone;
    @Schema(description = "短信验证码")
    private String code;
}
