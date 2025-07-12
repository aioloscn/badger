package com.aiolos.badger.user.api;

import com.aiolos.badger.user.dto.UserDTO;

import java.util.List;
import java.util.Map;

public interface UserApi {

    UserDTO getUserById(Long userId);

    void insertUser(UserDTO userDTO);

    void updateUserInfo(UserDTO userDTO);

    Map<Long, UserDTO> batchQueryUserInfo(List<Long> userIds);
}
