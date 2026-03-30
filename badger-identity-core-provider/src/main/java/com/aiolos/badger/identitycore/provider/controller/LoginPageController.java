package com.aiolos.badger.identitycore.provider.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    /**
     * 交给视图解析器去渲染 templates/login.html
     * 必须是 @Controller
     * @return
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
