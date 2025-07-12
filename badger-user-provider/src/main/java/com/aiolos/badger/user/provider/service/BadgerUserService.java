package com.aiolos.badger.user.provider.service;

import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface BadgerUserService {

    UserVO login(LoginBO loginBO, HttpServletResponse response);

    UserVO getUserById(Long userId);

    void insertUser(UserDTO userDTO);

    void updateUserInfo(UserDTO userDTO);

    Map<Long, UserVO> batchQueryUserInfo(List<Long> userIds);

    UserVO queryByPhone(String phone);
}
