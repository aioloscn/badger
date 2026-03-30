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

@Getter
@Setter
@TableName("schema_change_audit")
public class SchemaChangeAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("logic_table")
    private String logicTable;

    @TableField("column_name")
    private String columnName;

    @TableField("ddl_template")
    private String ddlTemplate;

    @TableField("rollback_sqls")
    private String rollbackSqls;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("report_payload")
    private String reportPayload;

    @TableField("status")
    private String status;

    @TableField("status_message")
    private String statusMessage;

    @TableField("dry_run")
    private Integer dryRun;

    @TableField("executed")
    private Integer executed;

    @TableField("full_sync_confirmed")
    private Integer fullSyncConfirmed;

    @TableField("physical_table_count")
    private Integer physicalTableCount;

    @TableField("sharding_config_source")
    private String shardingConfigSource;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("finish_time")
    private LocalDateTime finishTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
