package com.aiolos.badger.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 短信记录表
 * </p>
 *
 * @author Aiolos
 * @since 2025-04-24
 */
@Getter
@Setter
@TableName("sms_record")
public class SmsRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 验证码
     */
    @TableField("code")
    private Integer code;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 发送时间
     */
    @TableField("send_time")
    private LocalDateTime sendTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public static final String ID = "id";

    public static final String CODE = "code";

    public static final String PHONE = "phone";

    public static final String SEND_TIME = "send_time";

    public static final String UPDATE_TIME = "update_time";
}
