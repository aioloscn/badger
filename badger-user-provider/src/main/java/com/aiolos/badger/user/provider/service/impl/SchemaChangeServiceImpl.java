package com.aiolos.badger.user.provider.service.impl;

import com.aiolos.badger.model.po.SchemaChangeAudit;
import com.aiolos.badger.service.SchemaChangeAuditService;
import com.aiolos.badger.user.provider.model.bo.SchemaAddColumnBO;
import com.aiolos.badger.user.provider.model.vo.SchemaChangeReportVO;
import com.aiolos.badger.user.provider.service.SchemaChangeService;
import com.aiolos.common.util.UrlUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SchemaChangeServiceImpl implements SchemaChangeService {

    private static final String JDBC_SHARDING_NACOS_PREFIX = "jdbc:shardingsphere:nacos:";
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern COLUMN_TYPE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\s*\\([^)]*\\))?(\\s+(unsigned|UNSIGNED|zerofill|ZEROFILL|binary|BINARY))*$");
    private static final Pattern SIMPLE_RANGE_PATTERN = Pattern.compile("^(.*)\\$->\\{(\\d+)\\.\\.(\\d+)}$");
    private static final Pattern PAD_RANGE_PATTERN = Pattern.compile("^(.*)\\$->\\{\\((\\d+)\\.\\.(\\d+)\\)\\.collect\\{it\\.toString\\(\\)\\.padLeft\\((\\d+), '0'\\)}\\}$");
    private static final Pattern SAFE_SQL_EXPRESSION_PATTERN = Pattern.compile("^[A-Za-z0-9_(),\\s]+$");
    private static final String COLUMN_SNAPSHOT_SQL = """
            SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, ORDINAL_POSITION
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """;
    private static final String TABLE_EXISTS_SQL = """
            SELECT 1
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
            """;

    @Value("${spring.datasource.url:}")
    private String shardingDatasourceUrl;
    @Resource
    private SchemaChangeAuditService schemaChangeAuditService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public SchemaChangeReportVO addColumn(SchemaAddColumnBO request) {
        LocalDateTime startTime = LocalDateTime.now();
        SchemaAddColumnBO normalizedRequest = null;
        SchemaChangeReportVO report = new SchemaChangeReportVO();
        try {
            normalizedRequest = normalizeRequest(request);
            ParsedShardingConfig shardingConfig = loadShardingConfig();
            List<TableRoute> baseRoutes = resolveBaseRoutes(shardingConfig, normalizedRequest.getLogicTable());
            List<TableRoute> masterRoutes = resolveMasterRoutes(shardingConfig, baseRoutes);
            List<TableRoute> slaveRoutes = resolveSlaveRoutes(shardingConfig, baseRoutes);

            report.setLogicTable(normalizedRequest.getLogicTable());
            report.setColumnName(normalizedRequest.getColumnName());
            report.setDdlTemplate(buildDdlTemplate(normalizedRequest));
            report.setRollbackSqlTemplate(buildRollbackSqlTemplate(normalizedRequest));
            report.setShardingConfigSource(shardingConfig.getConfigDataId());
            report.setPhysicalTableCount(masterRoutes.size());
            report.setDryRun(Boolean.TRUE.equals(normalizedRequest.getDryRun()));
            report.setExecuted(false);
            report.setFullSyncConfirmed(slaveRoutes.isEmpty());

            if (Boolean.TRUE.equals(normalizedRequest.getDryRun())) {
                report.setMasterResults(buildPreviewResults(masterRoutes));
                report.setRollbackSqls(buildPlannedRollbackSqls(masterRoutes, normalizedRequest));
                if (slaveRoutes.isEmpty()) {
                    report.getWarnings().add("当前逻辑表未解析到从库，预演阶段无需主从同步校验");
                } else {
                    report.getWarnings().add("当前为预演模式，仅生成主库 DDL 计划，未执行从库同步确认");
                }
                return finalizeAndAudit(normalizedRequest, report, startTime, "DRY_RUN", "仅预演，未执行 DDL");
            }

            List<SchemaChangeReportVO.MasterExecutionResult> masterResults = executeMasterDdl(masterRoutes, normalizedRequest);
            report.setMasterResults(masterResults);
            report.setRollbackSqls(buildExecutedRollbackSqls(masterResults, normalizedRequest));
            boolean masterSuccess = masterResults.stream().noneMatch(item -> "FAILED".equals(item.getStatus()));
            boolean masterExecuted = masterResults.stream().anyMatch(item -> Boolean.TRUE.equals(item.getDdlExecuted()));
            report.setExecuted(masterExecuted);
            if (!masterSuccess) {
                report.setFullSyncConfirmed(false);
                report.getWarnings().add("存在主库执行失败的物理表，已停止从库同步确认");
                return finalizeAndAudit(normalizedRequest, report, startTime, "MASTER_FAILED", "主库 DDL 执行失败");
            }

            List<SchemaChangeReportVO.SlaveValidationResult> slaveResults = confirmSlaveSync(masterRoutes, slaveRoutes, normalizedRequest);
            report.setSlaveResults(slaveResults);
            boolean syncConfirmed = slaveResults.isEmpty() || slaveResults.stream().allMatch(item -> "PASS".equals(item.getStatus()));
            report.setFullSyncConfirmed(syncConfirmed);
            if (!syncConfirmed) {
                report.getWarnings().add("存在从库结构未对齐或复制状态异常的节点，请根据返回结果逐项排查");
            }
            if (slaveResults.isEmpty()) {
                report.getWarnings().add("当前逻辑表未解析到从库，系统仅完成主库 DDL 执行");
            }
            String status = syncConfirmed ? (masterExecuted ? "SUCCESS" : "NO_OP") : "SYNC_TIMEOUT";
            String statusMessage = syncConfirmed ? "结构变更执行完成" : "主库已完成，等待从库同步超时";
            return finalizeAndAudit(normalizedRequest, report, startTime, status, statusMessage);
        } catch (Exception ex) {
            if (normalizedRequest != null) {
                report.setLogicTable(StringUtils.defaultIfBlank(report.getLogicTable(), normalizedRequest.getLogicTable()));
                report.setColumnName(StringUtils.defaultIfBlank(report.getColumnName(), normalizedRequest.getColumnName()));
                report.setDdlTemplate(StringUtils.defaultIfBlank(report.getDdlTemplate(), buildDdlTemplate(normalizedRequest)));
                report.setRollbackSqlTemplate(StringUtils.defaultIfBlank(report.getRollbackSqlTemplate(), buildRollbackSqlTemplate(normalizedRequest)));
                if (report.getDryRun() == null) {
                    report.setDryRun(Boolean.TRUE.equals(normalizedRequest.getDryRun()));
                }
            }
            if (StringUtils.isNotBlank(ex.getMessage())) {
                report.getWarnings().add("执行异常: " + ex.getMessage());
            }
            finalizeAndAudit(normalizedRequest == null ? request : normalizedRequest, report, startTime, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private List<SchemaChangeReportVO.MasterExecutionResult> buildPreviewResults(List<TableRoute> masterRoutes) {
        List<SchemaChangeReportVO.MasterExecutionResult> results = new ArrayList<>();
        for (TableRoute route : masterRoutes) {
            SchemaChangeReportVO.MasterExecutionResult result = new SchemaChangeReportVO.MasterExecutionResult();
            result.setDataSourceName(route.getPhysicalDataSourceName());
            result.setJdbcUrl(route.getDataSourceConfig().getJdbcUrl());
            result.setTableName(route.getTableName());
            result.setStatus("PLANNED");
            result.setMessage("仅预演，未执行");
            result.setColumnExistsBefore(null);
            result.setDdlExecuted(false);
            result.setCostMs(0L);
            results.add(result);
        }
        return results;
    }

    private List<SchemaChangeReportVO.MasterExecutionResult> executeMasterDdl(List<TableRoute> masterRoutes, SchemaAddColumnBO request) {
        List<SchemaChangeReportVO.MasterExecutionResult> results = new ArrayList<>();
        for (TableRoute route : masterRoutes) {
            long startTime = System.currentTimeMillis();
            SchemaChangeReportVO.MasterExecutionResult result = new SchemaChangeReportVO.MasterExecutionResult();
            result.setDataSourceName(route.getPhysicalDataSourceName());
            result.setJdbcUrl(route.getDataSourceConfig().getJdbcUrl());
            result.setTableName(route.getTableName());
            result.setDdlExecuted(false);
            try (Connection connection = createConnection(route.getDataSourceConfig())) {
                ensureTableExists(connection, route.getTableName());
                SchemaChangeReportVO.ColumnSnapshot existingColumn = loadColumnSnapshot(connection, route.getTableName(), request.getColumnName());
                if (existingColumn != null) {
                    result.setStatus("SKIPPED");
                    result.setMessage("字段已存在，按最小改动原则跳过");
                    result.setColumnExistsBefore(true);
                    result.setDdlExecuted(false);
                } else {
                    String ddl = buildAlterSql(route.getTableName(), request);
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(ddl);
                    }
                    SchemaChangeReportVO.ColumnSnapshot snapshot = loadColumnSnapshot(connection, route.getTableName(), request.getColumnName());
                    if (snapshot == null) {
                        throw new IllegalStateException("DDL 执行后未查询到目标字段");
                    }
                    result.setStatus("EXECUTED");
                    result.setMessage("主库字段新增成功");
                    result.setColumnExistsBefore(false);
                    result.setDdlExecuted(true);
                }
            } catch (Exception ex) {
                log.error("执行主库字段新增失败, datasource={}, table={}", route.getPhysicalDataSourceName(), route.getTableName(), ex);
                result.setStatus("FAILED");
                result.setMessage(ex.getMessage());
                result.setColumnExistsBefore(false);
                result.setDdlExecuted(false);
            }
            result.setCostMs(System.currentTimeMillis() - startTime);
            results.add(result);
            if ("FAILED".equals(result.getStatus())) {
                break;
            }
        }
        return results;
    }

    private List<SchemaChangeReportVO.SlaveValidationResult> confirmSlaveSync(List<TableRoute> masterRoutes,
                                                                               List<TableRoute> slaveRoutes,
                                                                               SchemaAddColumnBO request) {
        if (slaveRoutes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, TableRoute> masterRouteMap = masterRoutes.stream()
                .collect(Collectors.toMap(TableRoute::tableKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        int timeoutSeconds = request.getSyncTimeoutSeconds();
        int intervalMillis = request.getSyncCheckIntervalMillis();
        int attempts = Math.max(1, (int) Math.ceil(Duration.ofSeconds(timeoutSeconds).toMillis() / (double) intervalMillis));
        List<SchemaChangeReportVO.SlaveValidationResult> lastResults = new ArrayList<>();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            lastResults = validateSlaveSchemas(masterRouteMap, slaveRoutes, request, attempt);
            boolean allPass = lastResults.stream().allMatch(item -> "PASS".equals(item.getStatus()));
            if (allPass) {
                return lastResults;
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        for (SchemaChangeReportVO.SlaveValidationResult item : lastResults) {
            if (!"PASS".equals(item.getStatus())) {
                item.setStatus("TIMEOUT");
                if (StringUtils.isBlank(item.getMessage())) {
                    item.setMessage("在超时时间内未确认到从库完成同步");
                } else if (!item.getMessage().contains("超时")) {
                    item.setMessage(item.getMessage() + "；在超时时间内未恢复");
                }
            }
        }
        return lastResults;
    }

    private List<SchemaChangeReportVO.SlaveValidationResult> validateSlaveSchemas(Map<String, TableRoute> masterRouteMap,
                                                                                   List<TableRoute> slaveRoutes,
                                                                                   SchemaAddColumnBO request,
                                                                                   int attempt) {
        List<SchemaChangeReportVO.SlaveValidationResult> results = new ArrayList<>();
        for (TableRoute slaveRoute : slaveRoutes) {
            SchemaChangeReportVO.SlaveValidationResult result = new SchemaChangeReportVO.SlaveValidationResult();
            result.setAttempt(attempt);
            result.setDataSourceName(slaveRoute.getPhysicalDataSourceName());
            result.setJdbcUrl(slaveRoute.getDataSourceConfig().getJdbcUrl());
            result.setTableName(slaveRoute.getTableName());
            TableRoute masterRoute = masterRouteMap.get(slaveRoute.tableKey());
            if (masterRoute == null) {
                result.setStatus("FAILED");
                result.setMessage("未找到对应主库路由");
                results.add(result);
                continue;
            }
            try (Connection masterConnection = createConnection(masterRoute.getDataSourceConfig());
                 Connection slaveConnection = createConnection(slaveRoute.getDataSourceConfig())) {
                SchemaChangeReportVO.ColumnSnapshot masterColumn = loadColumnSnapshot(masterConnection, masterRoute.getTableName(), request.getColumnName());
                SchemaChangeReportVO.ColumnSnapshot slaveColumn = loadColumnSnapshot(slaveConnection, slaveRoute.getTableName(), request.getColumnName());
                SchemaChangeReportVO.ReplicaStatus replicaStatus = loadReplicaStatus(slaveConnection);
                result.setMasterColumn(masterColumn);
                result.setSlaveColumn(slaveColumn);
                result.setReplicaStatus(replicaStatus);
                if (masterColumn == null) {
                    result.setStatus("FAILED");
                    result.setMessage("主库未查询到目标字段，无法继续校验");
                } else if (!columnEquals(masterColumn, slaveColumn)) {
                    result.setStatus("WAITING");
                    result.setMessage("从库字段结构尚未与主库对齐");
                } else if (Boolean.FALSE.equals(isReplicaHealthy(replicaStatus))) {
                    result.setStatus("WAITING");
                    result.setMessage("从库字段已存在，但复制状态未就绪");
                } else {
                    result.setStatus("PASS");
                    result.setMessage("从库字段结构与主库一致");
                }
            } catch (Exception ex) {
                log.error("校验从库结构失败, datasource={}, table={}", slaveRoute.getPhysicalDataSourceName(), slaveRoute.getTableName(), ex);
                result.setStatus("FAILED");
                result.setMessage(ex.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    private ParsedShardingConfig loadShardingConfig() {
        if (!shardingDatasourceUrl.startsWith(JDBC_SHARDING_NACOS_PREFIX)) {
            throw new IllegalStateException("当前数据源不是基于 Nacos 的 ShardingSphere 配置，无法解析物理库拓扑");
        }
        NacosTarget nacosTarget = parseNacosTarget(shardingDatasourceUrl);
        String rawYaml = fetchConfig(nacosTarget);
        String normalizedYaml = normalizeTaggedYaml(rawYaml);
        Map<String, Object> yamlMap = loadYamlAsMap(normalizedYaml);
        ParsedShardingConfig parsed = new ParsedShardingConfig();
        parsed.setConfigDataId(nacosTarget.getDataId());
        parsed.setDataSources(parsePhysicalDataSources(yamlMap));
        parseRules(yamlMap, parsed);
        if (parsed.getDataSources().isEmpty()) {
            throw new IllegalStateException("未解析到任何物理数据源配置");
        }
        return parsed;
    }

    private NacosTarget parseNacosTarget(String datasourceUrl) {
        String nacosPart = datasourceUrl.substring(JDBC_SHARDING_NACOS_PREFIX.length());
        String[] segments = nacosPart.split("\\?", 2);
        if (segments.length != 2) {
            throw new IllegalStateException("ShardingSphere Nacos URL 格式不正确");
        }
        String[] location = segments[0].split(":");
        if (location.length < 3) {
            throw new IllegalStateException("无法解析 Nacos 地址和 dataId");
        }
        NacosTarget target = new NacosTarget();
        target.setServerAddr(location[0] + ":" + location[1]);
        target.setDataId(location[2]);
        Map<String, String> params = UrlUtil.urlParse(segments[1]);
        target.setUsername(params.get("username"));
        target.setPassword(params.get("password"));
        target.setNamespace(params.get("namespace"));
        target.setGroup(params.getOrDefault("group", "DEFAULT_GROUP"));
        return target;
    }

    private String fetchConfig(NacosTarget target) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, target.getServerAddr());
        properties.setProperty(PropertyKeyConst.USERNAME, StringUtils.defaultString(target.getUsername()));
        properties.setProperty(PropertyKeyConst.PASSWORD, StringUtils.defaultString(target.getPassword()));
        properties.setProperty(PropertyKeyConst.NAMESPACE, StringUtils.defaultString(target.getNamespace()));
        try {
            ConfigService configService = NacosFactory.createConfigService(properties);
            String config = configService.getConfig(target.getDataId(), target.getGroup(), 5000);
            if (StringUtils.isBlank(config)) {
                throw new IllegalStateException("Nacos 中未读取到分片配置内容");
            }
            return config;
        } catch (NacosException ex) {
            throw new IllegalStateException("读取 Nacos 分片配置失败: " + ex.getMessage(), ex);
        }
    }

    private String normalizeTaggedYaml(String rawYaml) {
        return rawYaml.replaceAll("(?m)^(\\s*-\\s*)!(\\w+)\\s*$", "$1type: $2");
    }

    private Map<String, Object> loadYamlAsMap(String yamlContent) {
        YamlMapFactoryBean factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(new ByteArrayResource(yamlContent.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> yamlMap = factoryBean.getObject();
        if (yamlMap == null) {
            throw new IllegalStateException("分片 YAML 解析为空");
        }
        return yamlMap;
    }

    private Map<String, PhysicalDataSourceConfig> parsePhysicalDataSources(Map<String, Object> yamlMap) {
        Map<String, PhysicalDataSourceConfig> result = new LinkedHashMap<>();
        Map<String, Object> dataSources = asMap(yamlMap.get("dataSources"));
        for (Map.Entry<String, Object> entry : dataSources.entrySet()) {
            Map<String, Object> configMap = asMap(entry.getValue());
            PhysicalDataSourceConfig config = new PhysicalDataSourceConfig();
            config.setName(entry.getKey());
            config.setDriverClassName(stringValue(configMap.get("driverClassName")));
            config.setJdbcUrl(stringValue(configMap.get("jdbcUrl")));
            config.setUsername(stringValue(configMap.get("username")));
            config.setPassword(stringValue(configMap.get("password")));
            result.put(entry.getKey(), config);
        }
        return result;
    }

    private void parseRules(Map<String, Object> yamlMap, ParsedShardingConfig parsed) {
        List<?> rules = asList(yamlMap.get("rules"));
        for (Object ruleObject : rules) {
            Map<String, Object> ruleMap = asMap(ruleObject);
            String type = stringValue(ruleMap.get("type"));
            if ("READWRITE_SPLITTING".equals(type)) {
                Map<String, Object> dataSources = asMap(ruleMap.get("dataSources"));
                for (Map.Entry<String, Object> entry : dataSources.entrySet()) {
                    Map<String, Object> groupMap = asMap(entry.getValue());
                    Map<String, Object> staticStrategy = asMap(groupMap.get("staticStrategy"));
                    ReadwriteGroup group = new ReadwriteGroup();
                    group.setName(entry.getKey());
                    group.setWriteDataSourceName(stringValue(staticStrategy.get("writeDataSourceName")));
                    group.setReadDataSourceNames(asList(staticStrategy.get("readDataSourceNames")).stream()
                            .map(this::stringValue)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toList()));
                    parsed.getReadwriteGroups().put(group.getName(), group);
                }
            }
            if ("SINGLE".equals(type)) {
                parsed.setDefaultDataSource(stringValue(ruleMap.get("defaultDataSource")));
            }
            if ("SHARDING".equals(type)) {
                Map<String, Object> tables = asMap(ruleMap.get("tables"));
                for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                    Object tableValue = tableEntry.getValue();
                    if (tableValue instanceof Map<?, ?>) {
                        Map<String, Object> tableConfig = asMap(tableValue);
                        parsed.getTableActualNodes().put(tableEntry.getKey(), stringValue(tableConfig.get("actualDataNodes")));
                        continue;
                    }
                    parsed.getTableActualNodes().put(tableEntry.getKey(), stringValue(tableValue));
                }
            }
        }
    }

    private List<TableRoute> resolveBaseRoutes(ParsedShardingConfig config, String logicTable) {
        String actualNodes = config.getTableActualNodes().get(logicTable);
        if (StringUtils.isBlank(actualNodes)) {
            if (StringUtils.isBlank(config.getDefaultDataSource())) {
                throw new IllegalStateException("逻辑表 " + logicTable + " 未配置分片规则，也没有 defaultDataSource");
            }
            TableRoute route = new TableRoute();
            route.setRouteAlias(config.getDefaultDataSource());
            route.setTableName(logicTable);
            return Collections.singletonList(route);
        }
        List<TableRoute> routes = new ArrayList<>();
        for (String node : splitActualNodes(actualNodes)) {
            String[] parts = node.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("actualDataNodes 配置无法解析: " + node);
            }
            List<String> tables = expandTableExpression(parts[1]);
            for (String table : tables) {
                TableRoute route = new TableRoute();
                route.setRouteAlias(parts[0]);
                route.setTableName(table);
                routes.add(route);
            }
        }
        if (routes.isEmpty()) {
            throw new IllegalStateException("逻辑表 " + logicTable + " 未解析到任何物理表");
        }
        return deduplicateRoutes(routes);
    }

    private List<TableRoute> resolveMasterRoutes(ParsedShardingConfig config, List<TableRoute> baseRoutes) {
        List<TableRoute> results = new ArrayList<>();
        for (TableRoute baseRoute : baseRoutes) {
            ReadwriteGroup group = config.getReadwriteGroups().get(baseRoute.getRouteAlias());
            String physicalDataSourceName = group == null ? baseRoute.getRouteAlias() : group.getWriteDataSourceName();
            PhysicalDataSourceConfig dataSourceConfig = config.getDataSources().get(physicalDataSourceName);
            if (dataSourceConfig == null) {
                throw new IllegalStateException("未找到主库数据源配置: " + physicalDataSourceName);
            }
            TableRoute route = baseRoute.copy();
            route.setPhysicalDataSourceName(physicalDataSourceName);
            route.setDataSourceConfig(dataSourceConfig);
            results.add(route);
        }
        return deduplicateRoutes(results);
    }

    private List<TableRoute> resolveSlaveRoutes(ParsedShardingConfig config, List<TableRoute> baseRoutes) {
        List<TableRoute> results = new ArrayList<>();
        for (TableRoute baseRoute : baseRoutes) {
            ReadwriteGroup group = config.getReadwriteGroups().get(baseRoute.getRouteAlias());
            if (group == null || group.getReadDataSourceNames().isEmpty()) {
                continue;
            }
            for (String slaveName : group.getReadDataSourceNames()) {
                PhysicalDataSourceConfig dataSourceConfig = config.getDataSources().get(slaveName);
                if (dataSourceConfig == null) {
                    throw new IllegalStateException("未找到从库数据源配置: " + slaveName);
                }
                TableRoute route = baseRoute.copy();
                route.setPhysicalDataSourceName(slaveName);
                route.setDataSourceConfig(dataSourceConfig);
                results.add(route);
            }
        }
        return deduplicateRoutes(results);
    }

    private List<TableRoute> deduplicateRoutes(List<TableRoute> routes) {
        return new ArrayList<>(routes.stream()
                .collect(Collectors.toMap(TableRoute::fullKey, item -> item, (left, right) -> left, LinkedHashMap::new))
                .values());
    }

    private List<String> splitActualNodes(String actualNodes) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        for (int i = 0; i < actualNodes.length(); i++) {
            char currentChar = actualNodes.charAt(i);
            if (currentChar == '{') {
                braceDepth++;
            } else if (currentChar == '}') {
                braceDepth--;
            } else if (currentChar == ',' && braceDepth == 0) {
                segments.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }

    private List<String> expandTableExpression(String tableExpression) {
        Matcher padMatcher = PAD_RANGE_PATTERN.matcher(tableExpression);
        if (padMatcher.matches()) {
            String prefix = padMatcher.group(1);
            int start = Integer.parseInt(padMatcher.group(2));
            int end = Integer.parseInt(padMatcher.group(3));
            int width = Integer.parseInt(padMatcher.group(4));
            return buildRangeTables(prefix, start, end, width);
        }
        Matcher rangeMatcher = SIMPLE_RANGE_PATTERN.matcher(tableExpression);
        if (rangeMatcher.matches()) {
            String prefix = rangeMatcher.group(1);
            int start = Integer.parseInt(rangeMatcher.group(2));
            int end = Integer.parseInt(rangeMatcher.group(3));
            return buildRangeTables(prefix, start, end, 0);
        }
        return Collections.singletonList(tableExpression);
    }

    private List<String> buildRangeTables(String prefix, int start, int end, int width) {
        List<String> tables = new ArrayList<>();
        for (int index = start; index <= end; index++) {
            String suffix = width > 0 ? String.format("%0" + width + "d", index) : String.valueOf(index);
            tables.add(prefix + suffix);
        }
        return tables;
    }

    private SchemaAddColumnBO normalizeRequest(SchemaAddColumnBO request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        request.setLogicTable(trimToNull(request.getLogicTable()));
        request.setColumnName(trimToNull(request.getColumnName()));
        request.setColumnType(trimToNull(request.getColumnType()));
        request.setDefaultValue(trimToNull(request.getDefaultValue()));
        request.setComment(trimToNull(request.getComment()));
        request.setAfterColumn(trimToNull(request.getAfterColumn()));
        request.setNullable(request.getNullable() == null ? Boolean.TRUE : request.getNullable());
        request.setDryRun(request.getDryRun() == null ? Boolean.FALSE : request.getDryRun());
        request.setDefaultValueAsExpression(request.getDefaultValueAsExpression() == null ? Boolean.FALSE : request.getDefaultValueAsExpression());
        request.setSyncTimeoutSeconds(request.getSyncTimeoutSeconds() == null ? 60 : request.getSyncTimeoutSeconds());
        request.setSyncCheckIntervalMillis(request.getSyncCheckIntervalMillis() == null ? 2000 : request.getSyncCheckIntervalMillis());
        validateRequest(request);
        return request;
    }

    private void validateRequest(SchemaAddColumnBO request) {
        if (!isValidIdentifier(request.getLogicTable())) {
            throw new IllegalArgumentException("逻辑表名不合法");
        }
        if (!isValidIdentifier(request.getColumnName())) {
            throw new IllegalArgumentException("字段名不合法");
        }
        if (!COLUMN_TYPE_PATTERN.matcher(request.getColumnType()).matches()) {
            throw new IllegalArgumentException("字段类型定义不合法");
        }
        if (StringUtils.isNotBlank(request.getAfterColumn()) && !isValidIdentifier(request.getAfterColumn())) {
            throw new IllegalArgumentException("afterColumn 不合法");
        }
        if (Boolean.TRUE.equals(request.getDefaultValueAsExpression())
                && StringUtils.isNotBlank(request.getDefaultValue())
                && !SAFE_SQL_EXPRESSION_PATTERN.matcher(request.getDefaultValue()).matches()) {
            throw new IllegalArgumentException("默认值表达式不合法");
        }
        if (request.getSyncTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("syncTimeoutSeconds 必须大于 0");
        }
        if (request.getSyncCheckIntervalMillis() <= 0) {
            throw new IllegalArgumentException("syncCheckIntervalMillis 必须大于 0");
        }
    }

    private String buildDdlTemplate(SchemaAddColumnBO request) {
        return buildAlterSql("<physical_table>", request);
    }

    private String buildRollbackSqlTemplate(SchemaAddColumnBO request) {
        return buildRollbackSql("<physical_table>", request.getColumnName());
    }

    private String buildAlterSql(String tableName, SchemaAddColumnBO request) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("ALTER TABLE ")
                .append(quoteIdentifier(tableName))
                .append(" ADD COLUMN ")
                .append(quoteIdentifier(request.getColumnName()))
                .append(" ")
                .append(request.getColumnType());
        sqlBuilder.append(Boolean.TRUE.equals(request.getNullable()) ? " NULL" : " NOT NULL");
        if (StringUtils.isNotBlank(request.getDefaultValue())) {
            sqlBuilder.append(" DEFAULT ");
            if (Boolean.TRUE.equals(request.getDefaultValueAsExpression())) {
                sqlBuilder.append(request.getDefaultValue());
            } else {
                sqlBuilder.append("'").append(escapeSql(request.getDefaultValue())).append("'");
            }
        }
        if (StringUtils.isNotBlank(request.getComment())) {
            sqlBuilder.append(" COMMENT '").append(escapeSql(request.getComment())).append("'");
        }
        if (StringUtils.isNotBlank(request.getAfterColumn())) {
            sqlBuilder.append(" AFTER ").append(quoteIdentifier(request.getAfterColumn()));
        }
        return sqlBuilder.toString();
    }

    private String buildRollbackSql(String tableName, String columnName) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " DROP COLUMN " + quoteIdentifier(columnName);
    }

    private List<String> buildPlannedRollbackSqls(List<TableRoute> masterRoutes, SchemaAddColumnBO request) {
        return masterRoutes.stream()
                .map(route -> buildRollbackSql(route.getTableName(), request.getColumnName()))
                .collect(Collectors.toList());
    }

    private List<String> buildExecutedRollbackSqls(List<SchemaChangeReportVO.MasterExecutionResult> masterResults, SchemaAddColumnBO request) {
        return masterResults.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDdlExecuted()))
                .map(item -> buildRollbackSql(item.getTableName(), request.getColumnName()))
                .collect(Collectors.toList());
    }

    private Connection createConnection(PhysicalDataSourceConfig config) throws SQLException {
        if (StringUtils.isNotBlank(config.getDriverClassName())) {
            try {
                Class.forName(config.getDriverClassName());
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("驱动类不存在: " + config.getDriverClassName(), ex);
            }
        }
        return DriverManager.getConnection(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }

    private void ensureTableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TABLE_EXISTS_SQL)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("物理表不存在: " + tableName);
                }
            }
        }
    }

    private SchemaChangeReportVO.ColumnSnapshot loadColumnSnapshot(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COLUMN_SNAPSHOT_SQL)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                SchemaChangeReportVO.ColumnSnapshot snapshot = new SchemaChangeReportVO.ColumnSnapshot();
                snapshot.setColumnType(resultSet.getString("COLUMN_TYPE"));
                snapshot.setNullable("YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")));
                snapshot.setDefaultValue(resultSet.getString("COLUMN_DEFAULT"));
                snapshot.setComment(resultSet.getString("COLUMN_COMMENT"));
                snapshot.setOrdinalPosition(resultSet.getInt("ORDINAL_POSITION"));
                return snapshot;
            }
        }
    }

    private SchemaChangeReportVO.ReplicaStatus loadReplicaStatus(Connection connection) {
        SchemaChangeReportVO.ReplicaStatus status = tryLoadReplicaStatus(connection, "SHOW REPLICA STATUS", "REPLICA");
        if (status != null) {
            return status;
        }
        status = tryLoadReplicaStatus(connection, "SHOW SLAVE STATUS", "SLAVE");
        if (status != null) {
            return status;
        }
        SchemaChangeReportVO.ReplicaStatus fallback = new SchemaChangeReportVO.ReplicaStatus();
        fallback.setMode("UNKNOWN");
        fallback.setMessage("未读取到 SHOW REPLICA STATUS / SHOW SLAVE STATUS，按结构比对结果判断");
        return fallback;
    }

    private SchemaChangeReportVO.ReplicaStatus tryLoadReplicaStatus(Connection connection, String sql, String mode) {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            SchemaChangeReportVO.ReplicaStatus status = new SchemaChangeReportVO.ReplicaStatus();
            status.setMode(mode);
            String ioRunning = getString(resultSet, "Replica_IO_Running", "Slave_IO_Running");
            String sqlRunning = getString(resultSet, "Replica_SQL_Running", "Slave_SQL_Running");
            Long secondsBehindMaster = getLong(resultSet, "Seconds_Behind_Source", "Seconds_Behind_Master");
            status.setIoRunning(yesOrUnknown(ioRunning));
            status.setSqlRunning(yesOrUnknown(sqlRunning));
            status.setSecondsBehindMaster(secondsBehindMaster);
            status.setMessage("已读取复制状态");
            return status;
        } catch (SQLException ex) {
            return null;
        }
    }

    private Boolean isReplicaHealthy(SchemaChangeReportVO.ReplicaStatus status) {
        if (status == null) {
            return null;
        }
        if ("UNKNOWN".equals(status.getMode())) {
            return null;
        }
        if (Boolean.FALSE.equals(status.getIoRunning()) || Boolean.FALSE.equals(status.getSqlRunning())) {
            return false;
        }
        return true;
    }

    private String getString(ResultSet resultSet, String preferred, String fallback) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String label = metaData.getColumnLabel(index);
            if (preferred.equalsIgnoreCase(label) || fallback.equalsIgnoreCase(label)) {
                return resultSet.getString(label);
            }
        }
        return null;
    }

    private Long getLong(ResultSet resultSet, String preferred, String fallback) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String label = metaData.getColumnLabel(index);
            if (preferred.equalsIgnoreCase(label) || fallback.equalsIgnoreCase(label)) {
                Object value = resultSet.getObject(label);
                if (value == null) {
                    return null;
                }
                if (value instanceof Number number) {
                    return number.longValue();
                }
                return Long.parseLong(String.valueOf(value));
            }
        }
        return null;
    }

    private boolean columnEquals(SchemaChangeReportVO.ColumnSnapshot master, SchemaChangeReportVO.ColumnSnapshot slave) {
        if (master == null || slave == null) {
            return false;
        }
        return Objects.equals(normalizeColumnType(master.getColumnType()), normalizeColumnType(slave.getColumnType()))
                && Objects.equals(master.getNullable(), slave.getNullable())
                && Objects.equals(StringUtils.defaultString(master.getDefaultValue()), StringUtils.defaultString(slave.getDefaultValue()))
                && Objects.equals(StringUtils.defaultString(master.getComment()), StringUtils.defaultString(slave.getComment()));
    }

    private String normalizeColumnType(String columnType) {
        return StringUtils.deleteWhitespace(StringUtils.defaultString(columnType)).toLowerCase(Locale.ROOT);
    }

    private boolean isValidIdentifier(String value) {
        return StringUtils.isNotBlank(value) && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    private String quoteIdentifier(String identifier) {
        if (!isValidIdentifier(identifier) && !"<physical_table>".equals(identifier)) {
            throw new IllegalArgumentException("标识符不合法: " + identifier);
        }
        return "<physical_table>".equals(identifier) ? identifier : "`" + identifier + "`";
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private String trimToNull(String value) {
        String trimmed = StringUtils.trimToEmpty(value);
        return StringUtils.isBlank(trimmed) ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private SchemaChangeReportVO finalizeAndAudit(Object requestPayload,
                                                  SchemaChangeReportVO report,
                                                  LocalDateTime startTime,
                                                  String status,
                                                  String statusMessage) {
        try {
            SchemaChangeAudit audit = buildAuditEntity(requestPayload, report, startTime, status, statusMessage);
            if (schemaChangeAuditService.save(audit)) {
                report.setAuditId(audit.getId());
            } else {
                report.getWarnings().add("执行结果已返回，但审计表保存失败");
            }
        } catch (Exception ex) {
            log.error("保存结构变更审计失败", ex);
            report.getWarnings().add("执行结果已返回，但审计表保存失败: " + ex.getMessage());
        }
        return report;
    }

    private SchemaChangeAudit buildAuditEntity(Object requestPayload,
                                               SchemaChangeReportVO report,
                                               LocalDateTime startTime,
                                               String status,
                                               String statusMessage) {
        LocalDateTime finishTime = LocalDateTime.now();
        SchemaChangeAudit audit = new SchemaChangeAudit();
        audit.setLogicTable(report.getLogicTable());
        audit.setColumnName(report.getColumnName());
        audit.setDdlTemplate(report.getDdlTemplate());
        audit.setRollbackSqls(toJson(report.getRollbackSqls()));
        audit.setRequestPayload(toJson(requestPayload));
        audit.setReportPayload(toJson(report));
        audit.setStatus(status);
        audit.setStatusMessage(statusMessage);
        audit.setDryRun(booleanToInt(report.getDryRun()));
        audit.setExecuted(booleanToInt(report.getExecuted()));
        audit.setFullSyncConfirmed(booleanToInt(report.getFullSyncConfirmed()));
        audit.setPhysicalTableCount(report.getPhysicalTableCount());
        audit.setShardingConfigSource(report.getShardingConfigSource());
        audit.setOperatorId(resolveOperatorId());
        audit.setStartTime(startTime);
        audit.setFinishTime(finishTime);
        audit.setCreateTime(finishTime);
        audit.setUpdateTime(finishTime);
        return audit;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化审计数据失败", ex);
        }
    }

    private Integer booleanToInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Long resolveOperatorId() {
        try {
            Class<?> contextInfoClass = Class.forName("com.aiolos.common.model.ContextInfo");
            Object value = contextInfoClass.getMethod("getUserId").invoke(null);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null) {
                return Long.parseLong(String.valueOf(value));
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        throw new IllegalStateException("YAML 节点不是 Map: " + value);
    }

    private List<?> asList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        return Collections.singletonList(value);
    }

    private Boolean yesOrUnknown(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return "yes".equalsIgnoreCase(value);
    }

    @Data
    private static class ParsedShardingConfig {

        private String configDataId;
        private String defaultDataSource;
        private Map<String, PhysicalDataSourceConfig> dataSources = new LinkedHashMap<>();
        private Map<String, ReadwriteGroup> readwriteGroups = new LinkedHashMap<>();
        private Map<String, String> tableActualNodes = new LinkedHashMap<>();
    }

    @Data
    private static class ReadwriteGroup {

        private String name;
        private String writeDataSourceName;
        private List<String> readDataSourceNames = new ArrayList<>();
    }

    @Data
    private static class PhysicalDataSourceConfig {

        private String name;
        private String driverClassName;
        private String jdbcUrl;
        private String username;
        private String password;
    }

    @Data
    private static class TableRoute {

        private String routeAlias;
        private String physicalDataSourceName;
        private String tableName;
        private PhysicalDataSourceConfig dataSourceConfig;

        private TableRoute copy() {
            TableRoute route = new TableRoute();
            route.setRouteAlias(routeAlias);
            route.setPhysicalDataSourceName(physicalDataSourceName);
            route.setTableName(tableName);
            route.setDataSourceConfig(dataSourceConfig);
            return route;
        }

        private String tableKey() {
            return routeAlias + "|" + tableName;
        }

        private String fullKey() {
            return StringUtils.defaultString(physicalDataSourceName, routeAlias) + "|" + tableName;
        }
    }

    @Data
    private static class NacosTarget {

        private String serverAddr;
        private String dataId;
        private String username;
        private String password;
        private String namespace;
        private String group;
    }
}
