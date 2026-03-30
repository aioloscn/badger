package com.aiolos.badger.user.api;

import com.aiolos.badger.user.dto.UserDTO;

import java.util.List;
import java.util.Map;

public interface UserApi {

    UserDTO getUserById(Long userId);

    void insertUser(UserDTO userDTO);

    void updateUserInfo(UserDTO userDTO);

    Map<Long, UserDTO> batchQueryUserInfo(List<Long> userIds);

    /**
     * 根据用户名（手机号）查询用户信息，用于登录认证；会返回加密后的密码
     *
     * @param username 用户名（手机号）
     * @return 用户信息，如果不存在则返回 null
     */
    UserDTO getUserByUsername(String username);

    UserDTO authenticateBySms(String phone, String code);
}
