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
 * 
 * </p>
 *
 * @author Aiolos
 * @since 2025-03-22
 */
@Getter
@Setter
@TableName("id_generate_config")
public class IdGenerateConfigPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 描述
     */
    @TableField("remark")
    private String remark;

    /**
     * 当前id所在阶段的阈值
     */
    @TableField("next_threshold")
    private Long nextThreshold;

    /**
     * 初始化值
     */
    @TableField("init_num")
    private Long initNum;

    /**
     * 当前id所在阶段的开始值
     */
    @TableField("current_start")
    private Long currentStart;

    /**
     * id递增区间
     */
    @TableField("step")
    private Integer step;

    /**
     * 是否有序（0无序，1有序）
     */
    @TableField("is_seq")
    private Byte isSeq;

    /**
     * 业务前缀码，如果没有则返回时不携带
     */
    @TableField("id_prefix")
    private String idPrefix;

    /**
     * 乐观锁版本号
     */
    @TableField("version")
    private Integer version;

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

    public static final String ID = "id";

    public static final String REMARK = "remark";

    public static final String NEXT_THRESHOLD = "next_threshold";

    public static final String INIT_NUM = "init_num";

    public static final String CURRENT_START = "current_start";

    public static final String STEP = "step";

    public static final String IS_SEQ = "is_seq";

    public static final String ID_PREFIX = "id_prefix";

    public static final String VERSION = "version";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";
}
