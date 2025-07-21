package com.aiolos.badger.user.provider.controller;

import com.aiolos.badger.user.dto.UserDTO;
import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import com.aiolos.badger.user.provider.service.BadgerUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Tag(name = "用户服务")
@AllArgsConstructor
public class UserController {

    private final BadgerUserService badgerUserService;

    @PostMapping("/login")
    public UserVO login(@RequestBody LoginBO loginBO, HttpServletResponse response) {
        return badgerUserService.login(loginBO, response);
    }

    @GetMapping("/get-user-by-id")
    public UserVO getUserById(Long userId) {
        return badgerUserService.getUserById(userId);
    }

    @PostMapping("/insert-user")
    public void insertUser(@RequestBody UserDTO userDTO) {
        badgerUserService.insertUser(userDTO);
    }

    @PostMapping("/update-user-info")
    public void updateUserInfo(@RequestBody UserDTO userDTO) {
        badgerUserService.updateUserInfo(userDTO);
    }

    @PostMapping("/batch-query-user-info")
    public Map<Long, UserVO> batchQueryUserInfo(@RequestBody List<Long> userIds) {
        return badgerUserService.batchQueryUserInfo(userIds);
    }
}
