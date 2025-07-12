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
 * 用户tag
 * </p>
 *
 * @author Aiolos
 * @since 2025-03-27
 */
@Getter
@Setter
@TableName("user_tag")
public class UserTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    @TableId("user_id")
    private Long userId;

    /**
     * 标签记录字段
     */
    @TableField("tag_info_01")
    private Long tagInfo01;

    /**
     * 标签记录字段
     */
    @TableField("tag_info_02")
    private Long tagInfo02;

    /**
     * 标签记录字段
     */
    @TableField("tag_info_03")
    private Long tagInfo03;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public static final String USER_ID = "user_id";

    public static final String TAG_INFO_01 = "tag_info_01";

    public static final String TAG_INFO_02 = "tag_info_02";

    public static final String TAG_INFO_03 = "tag_info_03";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";
}
