package com.aiolos.badger.identitycore.provider.security;

import com.aiolos.badger.user.api.UserApi;
import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.common.enums.error.ErrorEnum;
import com.aiolos.common.exception.util.ExceptionUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * C端用户认证服务
 */
@Service
public class CiamUserDetailsService implements UserDetailsService {

    @DubboReference
    private UserApi userApi;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 通过 Dubbo 调用 badger-user-api 查询真实的数据库记录
        UserDTO userDTO = userApi.getUserByUsername(username);

        if (userDTO == null || userDTO.getUserId() == null) {
            ExceptionUtil.throwException(ErrorEnum.USER_DOES_NOT_EXIST);
        }
        String encodedPassword = userDTO.getPassword();
        // 短信登录场景允许用户未设置密码；这里给一个不可用于真实登录的占位密文，
        // 以便短信认证链路可正常构建 UserDetails，同时避免用户名密码登录绕过。
        if (StringUtils.isBlank(encodedPassword)) {
            encodedPassword = "{noop}SMS_LOGIN_ONLY_NO_PASSWORD";
        }

        // 将数据库中的记录转换为 Spring Security 需要的 UserDetails 对象
        // 注意：这里的 username 我们统一使用 userId 的字符串形式，以便后续签发 JWT 时 subject 是 userId
        return User.builder()
                .username(String.valueOf(userDTO.getUserId()))
                .password(encodedPassword) // 数据库中存储的密文密码，如果用的是明文，前面需要加 {noop}，如果用的是 BCrypt 则不需要
                .roles("USER")
                .build();
    }
}
