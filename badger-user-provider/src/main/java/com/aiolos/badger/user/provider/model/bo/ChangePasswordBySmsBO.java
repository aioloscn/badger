package com.aiolos.badger.user.provider.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ChangePasswordBySmsBO {

    @Schema(description = "短信验证码")
    private String code;

    @Schema(description = "新密码")
    private String newPassword;
}
