package com.aiolos.badger.user.provider.service;

import com.aiolos.badger.identitycore.dto.AccountTokenDTO;
import com.aiolos.badger.model.po.User;
import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface BadgerUserService {

    UserVO login(LoginBO loginBO, HttpServletResponse response);

    AccountTokenDTO refreshToken(String refreshToken);

    void logout(HttpServletRequest request, HttpServletResponse response);

    UserVO getUserById(Long userId);

    void insertUser(UserDTO userDTO);

    void updateUserInfo(UserDTO userDTO);

    void changePasswordBySms(Long userId, String code, String newPassword);

    Map<Long, UserVO> batchQueryUserInfo(List<Long> userIds);

    UserVO queryByPhone(String phone);

    User getUserByPhoneFromDb(String phone);
}
