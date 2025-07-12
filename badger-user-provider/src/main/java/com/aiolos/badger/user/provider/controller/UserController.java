package com.aiolos.badger.user.provider.controller;

import com.aiolos.badger.user.provider.model.bo.LoginBO;
import com.aiolos.badger.user.provider.model.vo.UserVO;
import com.aiolos.badger.user.provider.service.BadgerUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
