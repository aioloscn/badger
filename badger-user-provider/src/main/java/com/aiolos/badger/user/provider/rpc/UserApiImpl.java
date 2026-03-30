package com.aiolos.badger.user.provider.rpc;

import cn.hutool.core.collection.CollectionUtil;
import com.aiolos.badger.model.po.User;
import com.aiolos.badger.user.api.UserApi;
import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import com.aiolos.badger.user.provider.service.BadgerUserService;
import com.aiolos.common.util.ConvertBeanUtil;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DubboService
public class UserApiImpl implements UserApi {

    @Resource
    private BadgerUserService badgerUserService;

    @Override
    public UserDTO getUserById(Long userId) {
        return ConvertBeanUtil.convert(badgerUserService.getUserById(userId), UserDTO.class);
    }

    @Override
    public void insertUser(UserDTO userDTO) {
        badgerUserService.insertUser(userDTO);
    }

    @Override
    public void updateUserInfo(UserDTO userDTO) {
        badgerUserService.updateUserInfo(userDTO);
    }

    @Override
    public Map<Long, UserDTO> batchQueryUserInfo(List<Long> userIds) {
        Map<Long, UserVO> userVOMap = badgerUserService.batchQueryUserInfo(userIds);
        if (CollectionUtil.isEmpty(userVOMap)) {
            return Maps.newHashMap();
        }
        return userVOMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> ConvertBeanUtil.convert(entry.getValue(), UserDTO.class)));
    }

    @Override
    public UserDTO getUserByUsername(String username) {
        User user = badgerUserService.getUserByPhoneFromDb(username);
        if (user == null) {
            return null;
        }
        return ConvertBeanUtil.convert(user, UserDTO.class);
    }

    @Override
    public UserDTO authenticateBySms(String phone, String code) {
        LoginBO loginBO = new LoginBO();
        loginBO.setPhone(phone);
        loginBO.setCode(code);
        UserVO userVO = badgerUserService.login(loginBO, null);
        return ConvertBeanUtil.convert(userVO, UserDTO.class);
    }
}
