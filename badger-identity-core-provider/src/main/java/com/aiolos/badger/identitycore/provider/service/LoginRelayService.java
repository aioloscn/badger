package com.aiolos.badger.identitycore.provider.service;

import com.aiolos.badger.identitycore.provider.security.CiamUserDetailsService;
import com.aiolos.badger.sms.api.SmsApi;
import com.aiolos.badger.user.api.UserApi;
import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.common.model.response.CommonResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * 短信登录协同服务。
 * 认证中心通过 Dubbo 调用短信与用户服务完成验证码发送与校验，
 * 然后将用户加载为 Spring Security 的 UserDetails。
 */
@Service
public class LoginRelayService {

    private final CiamUserDetailsService ciamUserDetailsService;
    @DubboReference
    private SmsApi smsApi;
    @DubboReference
    private UserApi userApi;

    public LoginRelayService(CiamUserDetailsService ciamUserDetailsService) {
        this.ciamUserDetailsService = ciamUserDetailsService;
    }

    /**
     * 发送短信验证码。
     * 实际调用链路：identity-core -> Dubbo SmsApi -> badger-sms-provider。
     */
    public CommonResponse sendSmsCode(String phone) {
        if (StringUtils.isBlank(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        return smsApi.sendSms(phone.trim());
    }

    /**
     * 使用手机号+验证码完成认证。
     * 先通过 Dubbo 调用用户服务完成验证码登录校验，
     * 再加载本地 UserDetails，供后续安全上下文写入会话。
     */
    public UserDetails authenticateBySms(String phone, String code) {
        if (StringUtils.isAnyBlank(phone, code)) {
            throw new IllegalArgumentException("手机号或验证码不能为空");
        }
        UserDTO userDTO = userApi.authenticateBySms(phone.trim(), code.trim());
        if (userDTO == null || userDTO.getUserId() == null) {
            throw new IllegalArgumentException("短信登录失败");
        }
        return ciamUserDetailsService.loadUserByUsername(phone.trim());
    }
}
