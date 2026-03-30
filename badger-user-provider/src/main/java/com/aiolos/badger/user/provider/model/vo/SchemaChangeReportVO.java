package com.aiolos.badger.user.provider.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SchemaChangeReportVO {

    @Schema(description = "审计记录ID")
    private Long auditId;

    @Schema(description = "逻辑表名")
    private String logicTable;

    @Schema(description = "字段名")
    private String columnName;

    @Schema(description = "DDL 模板")
    private String ddlTemplate;

    @Schema(description = "回滚 SQL 模板")
    private String rollbackSqlTemplate;

    @Schema(description = "分片配置来源")
    private String shardingConfigSource;

    @Schema(description = "物理表数量")
    private Integer physicalTableCount;

    @Schema(description = "是否仅预演")
    private Boolean dryRun;

    @Schema(description = "是否真正执行过主库 DDL")
    private Boolean executed;

    @Schema(description = "主从同步是否确认完成")
    private Boolean fullSyncConfirmed;

    @Schema(description = "主库执行结果")
    private List<MasterExecutionResult> masterResults = new ArrayList<>();

    @Schema(description = "预生成回滚 SQL")
    private List<String> rollbackSqls = new ArrayList<>();

    @Schema(description = "从库校验结果")
    private List<SlaveValidationResult> slaveResults = new ArrayList<>();

    @Schema(description = "告警信息")
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class MasterExecutionResult {

        private String dataSourceName;
        private String jdbcUrl;
        private String tableName;
        private String status;
        private String message;
        private Boolean columnExistsBefore;
        private Boolean ddlExecuted;
        private Long costMs;
    }

    @Data
    public static class SlaveValidationResult {

        private String dataSourceName;
        private String jdbcUrl;
        private String tableName;
        private String status;
        private String message;
        private Integer attempt;
        private ReplicaStatus replicaStatus;
        private ColumnSnapshot masterColumn;
        private ColumnSnapshot slaveColumn;
    }

    @Data
    public static class ReplicaStatus {

        private String mode;
        private Boolean ioRunning;
        private Boolean sqlRunning;
        private Long secondsBehindMaster;
        private String message;
    }

    @Data
    public static class ColumnSnapshot {

        private String columnType;
        private Boolean nullable;
        private String defaultValue;
        private String comment;
        private Integer ordinalPosition;
    }
}
