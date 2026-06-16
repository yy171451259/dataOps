package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.*;
import com.dataops.dms.mapper.MetadataColumnMapper;
import com.dataops.dms.mapper.MetadataTableMapper;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class MetadataServiceImpl implements MetadataService {

    @Resource
    private MetadataTableMapper tableMapper;

    @Resource
    private MetadataColumnMapper columnMapper;

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Override
    public Result<Integer> collectMetadata(String databaseId) {
        return collectMetadata(databaseId, null);
    }

    @Override
    public Result<Integer> collectMetadata(String databaseId, String databaseName) {
        DatabaseInstance db = databaseInstanceService.getById(databaseId);
        if (db == null) {
            return Result.error("数据库实例不存在");
        }

        String targetDb = databaseName != null ? databaseName : db.getDefaultSchemaName();
        if (targetDb == null || targetDb.isEmpty()) {
            return Result.error("未指定数据库名，请在实例配置中填写默认数据库或选择数据库");
        }

        // 物理清除旧数据（绕过逻辑删除，避免 UNIQUE KEY 冲突）
        tableMapper.physicalDeleteBySchema(databaseId, targetDb);
        columnMapper.physicalDeleteByInstanceId(databaseId);

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        int tableCount = 0;

        try {
            // 连接到指定数据库
            String url = String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
                db.getHost(), db.getPort() != null ? db.getPort() : 3306, targetDb
            );
            Properties props = new Properties();
            props.put("user", db.getUsername());
            props.put("password", db.getPassword());
            props.put("connectTimeout", "5000");
            conn = DriverManager.getConnection(url, props);
            stmt = conn.createStatement();

            // 采集表信息
            rs = stmt.executeQuery(
                "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_TYPE, TABLE_ROWS, DATA_LENGTH " +
                "FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = '" + targetDb + "' " +
                "ORDER BY TABLE_NAME"
            );

            List<MetadataTable> tableList = new ArrayList<>();
            Map<String, MetadataTable> tableMap = new LinkedHashMap<>();
            while (rs.next()) {
                MetadataTable metaTable = new MetadataTable();
                metaTable.setInstanceId(databaseId);
                metaTable.setSchemaName(targetDb);
                metaTable.setTableName(rs.getString("TABLE_NAME"));
                metaTable.setTableComment(rs.getString("TABLE_COMMENT"));
                metaTable.setTableType(rs.getString("TABLE_TYPE"));
                metaTable.setRowCount(rs.getLong("TABLE_ROWS"));
                metaTable.setDataSize(rs.getObject("DATA_LENGTH") != null ? rs.getLong("DATA_LENGTH") : 0L);
                metaTable.setLastCollected(LocalDateTime.now());
                metaTable.setDeleted(0); // 逻辑删除标记：未删除
                tableMapper.insert(metaTable);
                tableMap.put(metaTable.getTableName(), metaTable);
                tableCount++;
            }
            rs.close();

            if (tableCount == 0) {
                return Result.success("数据库 '" + targetDb + "' 中未找到表", 0);
            }

            // 采集字段信息
            rs = stmt.executeQuery(
                "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH AS COL_LEN, " +
                "IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT, ORDINAL_POSITION, COLUMN_DEFAULT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = '" + targetDb + "' " +
                "ORDER BY TABLE_NAME, ORDINAL_POSITION"
            );

            List<MetadataColumn> columnList = new ArrayList<>();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                MetadataTable metaTable = tableMap.get(tableName);
                if (metaTable == null) continue;

                MetadataColumn col = new MetadataColumn();
                col.setTableId(metaTable.getId());
                col.setInstanceId(databaseId);
                col.setColumnName(rs.getString("COLUMN_NAME"));
                col.setDataType(rs.getString("DATA_TYPE"));
                col.setColumnLength(rs.getObject("COL_LEN") != null ? rs.getLong("COL_LEN") : null);
                col.setIsNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                col.setIsPrimaryKey("PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY")));
                col.setColumnComment(rs.getString("COLUMN_COMMENT"));
                col.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                col.setDeleted(0); // 逻辑删除标记：未删除
                columnMapper.insert(col);
                columnList.add(col);
            }

            String msg = "元数据采集完成，共 " + tableCount + " 张表， " + columnList.size() + " 个字段";
            if (databaseName != null) msg += "（数据库:" + databaseName + "）";
            return Result.success(msg, tableCount);

        } catch (Exception e) {
            log.error("元数据采集失败: {}", e.getMessage());
            return Result.error("元数据采集失败: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception e) {}
            try { if (stmt != null) stmt.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
        }
    }

    @Override
    public Result<Page<MetadataTable>> listTables(Integer page, Integer size, String databaseId, String keyword) {
        return listTables(page, size, databaseId, keyword, null);
    }

    public Result<Page<MetadataTable>> listTables(Integer page, Integer size, String databaseId, String keyword, String schemaName) {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        if (databaseId != null && !databaseId.isEmpty()) {
            wrapper.eq(MetadataTable::getInstanceId, databaseId);
        }
        if (schemaName != null && !schemaName.isEmpty()) {
            wrapper.eq(MetadataTable::getSchemaName, schemaName);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(MetadataTable::getTableName, keyword)
                    .or()
                    .like(MetadataTable::getTableComment, keyword));
        }
        wrapper.orderByAsc(MetadataTable::getTableName);
        return Result.success(tableMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<List<MetadataColumn>> listColumns(String tableId) {
        LambdaQueryWrapper<MetadataColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataColumn::getTableId, tableId);
        wrapper.orderByAsc(MetadataColumn::getOrdinalPosition);
        return Result.success(columnMapper.selectList(wrapper));
    }

    @Override
    public Result<MetadataTable> getTableDetail(String tableId) {
        return Result.success(tableMapper.selectById(tableId));
    }

    @Override
    public Result<Page<MetadataTable>> search(String keyword, String databaseId, Integer page, Integer size) {
        return listTables(page, size, databaseId, keyword);
    }

    @Override
    public Result<MetadataTable> updateTableMeta(String tableId, String tableComment, String businessTags, String owner) {
        MetadataTable table = tableMapper.selectById(tableId);
        if (table == null) {
            return Result.error("表不存在");
        }
        if (tableComment != null) table.setTableComment(tableComment);
        if (businessTags != null) table.setBusinessTags(businessTags);
        if (owner != null) table.setOwner(owner);
        tableMapper.updateById(table);
        return Result.success(table);
    }

    @Override
    public Result<MetadataColumn> updateColumnMeta(String columnId, String columnComment, String businessName, Boolean isSensitive) {
        MetadataColumn col = columnMapper.selectById(columnId);
        if (col == null) {
            return Result.error("字段不存在");
        }
        if (columnComment != null) col.setColumnComment(columnComment);
        if (businessName != null) col.setBusinessName(businessName);
        if (isSensitive != null) col.setIsSensitive(isSensitive);
        columnMapper.updateById(col);
        return Result.success(col);
    }

    @Override
    public Result<Object> getDatabaseStats(String databaseId) {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataTable::getInstanceId, databaseId);
        long tableCount = tableMapper.selectCount(wrapper);

        Map<String, Object> stats = new HashMap<>();
        stats.put("databaseId", databaseId);
        stats.put("tableCount", tableCount);
        stats.put("lastCollected", LocalDateTime.now());
        return Result.success(stats);
    }
}

