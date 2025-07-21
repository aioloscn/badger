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
 * 用户邮箱关联表
 * </p>
 *
 * @author Aiolos
 * @since 2025-07-21
 */
@Getter
@Setter
@TableName("user_email_mapping")
public class UserEmailMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    @TableField("create_time")
    private LocalDateTime createTime;

    public static final String ID = "id";

    public static final String USER_ID = "user_id";

    public static final String EMAIL = "email";

    public static final String CREATE_TIME = "create_time";
}
