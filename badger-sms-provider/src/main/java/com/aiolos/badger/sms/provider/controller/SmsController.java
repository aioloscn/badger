package com.aiolos.badger.sms.provider.controller;

import com.aiolos.badger.sms.provider.service.SmsService;
import com.aiolos.common.model.response.CommonResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sms")
@Tag(name = "短信服务")
public class SmsController {

    @Resource
    private SmsService smsService;

    @PostMapping("/send-sms")
    CommonResponse sendSms(String phone) {
        return smsService.sendSms(phone);
    }
}
