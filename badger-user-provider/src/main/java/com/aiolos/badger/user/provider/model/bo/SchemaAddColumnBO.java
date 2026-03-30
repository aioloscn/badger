package com.aiolos.badger.user.provider.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SchemaAddColumnBO {

    @Schema(description = "逻辑表名，例如 user")
    private String logicTable;

    @Schema(description = "新增字段名，例如 password")
    private String columnName;

    @Schema(description = "字段类型定义，例如 varchar(128)")
    private String columnType;

    @Schema(description = "是否允许为空，默认 true")
    private Boolean nullable;

    @Schema(description = "默认值，不填则不拼接 DEFAULT")
    private String defaultValue;

    @Schema(description = "默认值是否按 SQL 表达式原样拼接，默认 false")
    private Boolean defaultValueAsExpression;

    @Schema(description = "字段注释")
    private String comment;

    @Schema(description = "字段插入位置，填写 after 的字段名")
    private String afterColumn;

    @Schema(description = "是否仅预演不执行，默认 false")
    private Boolean dryRun;

    @Schema(description = "从库同步确认超时时间，默认 60 秒")
    private Integer syncTimeoutSeconds;

    @Schema(description = "从库同步轮询间隔毫秒，默认 2000")
    private Integer syncCheckIntervalMillis;
}
