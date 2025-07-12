package com.aiolos.badger.model.po;

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
 * 用户表
 * </p>
 *
 * @author Aiolos
 * @since 2025-03-04
 */
@Getter
@Setter
@TableName("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    @TableId("user_id")
    private Long userId;

    /**
     * 昵称
     */
    @TableField("nick_name")
    private String nickName;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 真实姓名
     */
    @TableField("true_name")
    private String trueName;

    /**
     * 性别 0男, 1女
     */
    @TableField("sex")
    private Byte sex;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 出生时间
     */
    @TableField("born_time")
    private LocalDateTime bornTime;

    /**
     * 工作地
     */
    @TableField("work_city")
    private Integer workCity;

    /**
     * 出生地
     */
    @TableField("born_city")
    private Integer bornCity;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public static final String USER_ID = "user_id";

    public static final String NICK_NAME = "nick_name";

    public static final String AVATAR = "avatar";

    public static final String TRUE_NAME = "true_name";

    public static final String SEX = "sex";
    
    public static final String PHONE = "phone";

    public static final String BORN_TIME = "born_time";

    public static final String WORK_CITY = "work_city";

    public static final String BORN_CITY = "born_city";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";
}
