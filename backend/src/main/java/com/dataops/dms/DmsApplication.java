package com.dataops.dms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * DataOps DMS 主应用类
 * 
 * 注意：如果 Flowable 启动有问题，可临时排除：
 * exclude = { 
 *     LiquibaseAutoConfiguration.class,
 *     org.flowable.spring.boot.FlowableSecurityAutoConfiguration.class,
 *     org.flowable.spring.boot.RestApiAutoConfiguration.class
 * }
 */
@SpringBootApplication(exclude = {
    LiquibaseAutoConfiguration.class,
    // 排除 App Engine 及其关联引擎自动配置，避免 Event Registry Liquibase 建表失败
    // 仅保留 Process Engine（BPM 流程引擎）
    org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration.class,
    org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration.class,
    org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration.class,
    org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration.class,
    org.flowable.spring.boot.form.FormEngineServicesAutoConfiguration.class,
    org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration.class,
    org.flowable.spring.boot.content.ContentEngineServicesAutoConfiguration.class
})
@EnableAsync
@MapperScan("com.dataops.dms.mapper")
public class DmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmsApplication.class, args);
        System.out.println("===================================");
        System.out.println("  DataOps DMS 启动成功！");
        System.out.println("  API: http://localhost:8080");
        System.out.println("===================================");
    }
}
