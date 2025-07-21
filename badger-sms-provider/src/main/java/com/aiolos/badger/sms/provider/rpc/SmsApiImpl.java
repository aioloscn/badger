package com.aiolos.badger.sms.provider.rpc;

import com.aiolos.badger.sms.api.SmsApi;
import com.aiolos.badger.sms.provider.service.SmsService;
import com.aiolos.common.model.response.CommonResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@DubboService
public class SmsApiImpl implements SmsApi {
    
    @Resource
    private SmsService smsService;
    
    @Transactional
    @Override
    public CommonResponse sendSms(String phone) {
        return smsService.sendSms(phone);
    }
}
