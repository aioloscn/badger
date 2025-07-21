package com.aiolos.badger.sms.api;

import com.aiolos.common.model.response.CommonResponse;

public interface SmsApi {

    /**
     * 发送短信
     * @param phone
     * @return
     */
    CommonResponse sendSms(String phone);
}
