package com.aiolos.badger.sms.provider.service;

import com.aiolos.common.model.response.CommonResponse;

public interface SmsService {

    CommonResponse sendSms(String phone);
}
