package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.DatabaseInstance;

import java.util.List;
import java.util.Map;

/**
 * 数据库实例服务接口
 */
public interface DatabaseInstanceService extends IService<DatabaseInstance> {

    /**
     * 测试数据库连接
     */
    boolean testConnection(DatabaseInstance db);

    /**
     * 测试已保存的数据库连接
     */
    boolean testConnectionById(String id);

    /**
     * 获取所有可用的数据库实例
     */
    List<DatabaseInstance> getActiveInstances();

    /**
     * 保存并测试连接
     */
    boolean saveAndTest(DatabaseInstance db);

    /**
     * 获取数据库实例下的所有表名
     */
    List<String> getTableNames(String id) throws Exception;
    List<String> getTableNames(String id, String databaseName) throws Exception;

    /**
     * 获取指定表的建表语句
     */
    String getCreateTableSql(String id, String tableName) throws Exception;
    String getCreateTableSql(String id, String databaseName, String tableName) throws Exception;

    /**
     * 批量获取多张表的建表语句
     */
    Map<String, String> batchGetCreateTableSql(String id, List<String> tableNames) throws Exception;
    Map<String, String> batchGetCreateTableSql(String id, String databaseName, List<String> tableNames) throws Exception;

    /**
     * 获取数据库连接
     */
    java.sql.Connection getConnection(DatabaseInstance db) throws Exception;

    /**
     * 获取数据库实例的完整Schema信息（表名+列名+列类型），用于SQL智能补全
     */
    List<Map<String, Object>> getSchemaForCompletion(String id) throws Exception;

    /**
     * 获取指定数据库的Schema信息（用于补全）
     */
    List<Map<String, Object>> getSchemaForCompletion(String id, String databaseName) throws Exception;

    /**
     * 获取数据库实例下的所有Schema名（SHOW DATABASES）
     */
    List<String> getSchemaNames(String id) throws Exception;

    /**
     * 获取数据库浏览器综合Schema（表、视图、存储过程、函数、触发器、事件）
     */
    Map<String, Object> getBrowserSchema(String id, String databaseName) throws Exception;

    /**
     * 获取单张表的详细信息（索引、外键、约束、触发器、DDL）
     */
    Map<String, Object> getTableDetail(String id, String databaseName, String tableName) throws Exception;
}