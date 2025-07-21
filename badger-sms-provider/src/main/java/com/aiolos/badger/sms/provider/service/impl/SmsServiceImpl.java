package com.aiolos.badger.sms.provider.service.impl;

import com.aiolos.badger.common.redis.SmsRedisKeyBuilder;
import com.aiolos.badger.enums.MsgSendResultEnum;
import com.aiolos.badger.model.po.SmsRecord;
import com.aiolos.badger.service.SmsRecordService;
import com.aiolos.badger.sms.provider.config.SmsThreadPoolManager;
import com.aiolos.badger.sms.provider.service.SmsService;
import com.aiolos.common.enums.error.ErrorEnum;
import com.aiolos.common.exception.util.ExceptionUtil;
import com.aiolos.common.model.response.CommonResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    @Resource
    private SmsRecordService smsRecordService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private SmsRedisKeyBuilder smsRedisKeyBuilder;

    @Transactional
    @Override
    public CommonResponse sendSms(String phone) {
        if (StringUtils.isBlank(phone) || phone.length() != 11) {
            return CommonResponse.error(MsgSendResultEnum.PARAM_ERROR.getCode(), MsgSendResultEnum.PARAM_ERROR.getDesc());
        }

        if (!redisTemplate.opsForValue().setIfAbsent(smsRedisKeyBuilder.buildPreventRepeatSendingKey(phone), 1, 60, TimeUnit.SECONDS)) {
            ExceptionUtil.throwException(ErrorEnum.REPEAT_SENDING_SMS_CODE);
        }

        // 生成6位验证码，有效期5分钟，1分钟内不能重复发送，存储到redis
        int code = RandomUtils.secure().randomInt(100000, 999999);
        String smsRedisKey = smsRedisKeyBuilder.buildSmsLoginCodeKey(phone);
        redisTemplate.opsForValue().set(smsRedisKey, code, 300, TimeUnit.SECONDS);

        // 发送短信
        SmsThreadPoolManager.commonAsyncPool.execute(() -> {
            // TODO 接收SmsUtil发送结果，然后保存记录
            // 同一个类内部的非事务方法调用本类中的事务方法，事务失效
            // 数据库操作不能放线程池中，不然主线程抛出异常无法回滚异步线程中的数据库操作
            log.info("已向手机号: {} 发送验证码: {}", phone, code);
        });
        SmsRecord smsRecord = new SmsRecord();
        smsRecord.setPhone(phone);
        smsRecord.setCode(code);
        smsRecordService.save(smsRecord);
        return CommonResponse.ok(MsgSendResultEnum.SUCCESS);
    }
}
