package com.aiolos.badger.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 用户手机关联表
 * </p>
 *
 * @author Aiolos
 * @since 2025-07-12
 */
@Getter
@Setter
@TableName("user_phone_mapping")
public class UserPhoneMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    @TableField("create_time")
    private LocalDateTime createTime;

    public static final String ID = "id";

    public static final String USER_ID = "user_id";

    public static final String PHONE = "phone";

    public static final String CREATE_TIME = "create_time";
}
