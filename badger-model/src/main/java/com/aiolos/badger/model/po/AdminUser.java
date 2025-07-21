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
 * 运营管理平台的admin级别用户
 * </p>
 *
 * @author Aiolos
 * @since 2025-07-21
 */
@Getter
@Setter
@TableName("admin_user")
public class AdminUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    /**
     * 人脸入库图片信息，该信息保存到mongoDB的gridFS中
     */
    @TableField("face_id")
    private String faceId;

    /**
     * 管理人员的姓名
     */
    @TableField("admin_name")
    private String adminName;

    /**
     * 创建时间 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static final String ID = "id";

    public static final String USERNAME = "username";

    public static final String PASSWORD = "password";

    public static final String FACE_ID = "face_id";

    public static final String ADMIN_NAME = "admin_name";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";
}
