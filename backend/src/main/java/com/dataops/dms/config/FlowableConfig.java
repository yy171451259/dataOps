package com.dataops.dms.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;

/**
 * Flowable工作流配置
 * 确保数据库表自动创建/更新，解决首次启动时 ACT_GE_PROPERTY 表不存在的问题
 * 清空 Process Engine 的 configurator，阻止 Event Registry 引擎初始化
 * 数据库使用 utf8 字符集，避免索引超长问题
 */
@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    @Override
    public void configure(SpringProcessEngineConfiguration configuration) {
        // 数据库表更新策略：true = 自动创建/更新表
        configuration.setDatabaseSchemaUpdate("true");
        // 显式指定数据库类型，避免自动检测失败
        configuration.setDatabaseType("mysql");
        // 关闭异步执行器
        configuration.setAsyncExecutorActivate(false);
        // 配置字体，解决流程图中文乱码问题
        configuration.setActivityFontName("宋体");
        configuration.setLabelFontName("宋体");
        configuration.setAnnotationFontName("宋体");
        // 禁用部署时自动生成流程图
        configuration.setCreateDiagramOnDeploy(false);
        // 清空 Process Engine configurator，阻止 Event Registry 初始化
        configuration.setConfigurators(new ArrayList<>());
    }
}
