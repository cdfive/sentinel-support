package com.winxuan.sentinel.support.config;

import com.alibaba.csp.sentinel.datasource.DataSource;
import com.alibaba.csp.sentinel.datasource.jdbc.JdbcDataSource;
import com.alibaba.csp.sentinel.datasource.zookeeper.ZookeeperDataSource;
import com.alibaba.csp.sentinel.init.InitExecutor;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.winxuan.sentinel.support.SentinelSupportConstant;
import com.winxuan.sentinel.support.activemq.aspect.MessageListenerAspect;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * @author cdfive
 * @date 2018-08-29
 */
@Slf4j
@Data
@Configuration
@PropertySource(value = "classpath:sentinel.properties", ignoreResourceNotFound = true)
public class SentinelProperties {

    private static final String FLOW_PATH = "flow";

    private static final String DEGRADE_PATH = "degrade";

    private static final String SYSTEM_PATH = "system";

    private static final String DATA_SOURCE_TYPE_JDBC = "jdbc";

    private static final String DATA_SOURCE_TYPE_ZOOKEEPER = "zookeeper";

    @Value("${sentinel.enable:false}")
    private boolean enable;

    @Value("${sentinel.dataSource.type:zookeeper}")
    private String dataSourceType;

    /******************jdbc datasource配置******************/
    @Value("${sentinel.dataSource.jdbc.driverClassName:#{null}}")
    private String driverClassName;

    @Value("${sentinel.dataSource.jdbc.url:#{null}}")
    private String url;

    @Value("${sentinel.dataSource.jdbc.username:#{null}}")
    private String username;

    @Value("${sentinel.dataSource.jdbc.password:#{null}}")
    private String password;

    @Value("${sentinel.dataSource.jdbc.appName:#{null}}")
    private String appName;

    @Value("${sentinel.dataSource.jdbc.ruleRefreshSec:#{null}}")
    private Long ruleRefreshSec;

    /******************zookeeper datasource配置******************/
    @Value("${sentinel.dataSource.zookeeper.url:localhost:2181}")
    private String zookeeperUrl;

    @Value("${sentinel.dubbo.path:#{null}}")
    private String dubboPath;

    @Value("${sentinel.activemq.path:#{null}}")
    private String activemqPath;

    @PostConstruct
    public void init() {
        log(this.toString());

        log("InitExecutor.doInit() start");
        InitExecutor.doInit();
        log("InitExecutor.doInit() end");

        if (!enable) {
            return;
        }

        log("sentinel is enable");

        if (dataSourceType == null) {
            logWarn("dataSourceType is null");
            return;
        }

        if (!DATA_SOURCE_TYPE_JDBC.equals(dataSourceType) && !DATA_SOURCE_TYPE_ZOOKEEPER.equals(dataSourceType)) {
            logWarn("dataSourceType=" + dataSourceType + " invalid. now supported: jdbc or zookeeper");
            return;
        }

        if (DATA_SOURCE_TYPE_ZOOKEEPER.equals(dataSourceType)) {
            log("initZookeeperDataSource");
            initZookeeperDataSource();
            return;
        }

        if (DATA_SOURCE_TYPE_JDBC.equals(dataSourceType)) {
            log("initJdbcDataSource");
            initJdbcDataSource();
            return;
        }
    }

    @Bean
    @Conditional(SentinelJdbcDataSourceCondition.class)
    public DruidDataSource sentinelDataSource() {
        DruidDataSource datasource = new DruidDataSource();
        datasource.setUrl(url);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setDriverClassName(driverClassName);
        return datasource;
    }

    @Bean
    @Conditional(SentinelJdbcDataSourceCondition.class)
    public JdbcTemplate sentinelJdbcTemplate() {
        return new JdbcTemplate(sentinelDataSource());
    }

    @Bean
    @Conditional(SentinelEnableCondition.class)
    public MessageListenerAspect messageListenerAspect() {
        return new MessageListenerAspect();
    }

    private static class SentinelEnableCondition implements Condition {
        @Override
        public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
            String enable = conditionContext.getEnvironment().getProperty("sentinel.enable");
            return enable != null && "true".equals(enable);
        }
    }

    private static class SentinelJdbcDataSourceCondition implements Condition {
        @Override
        public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
            String enable = conditionContext.getEnvironment().getProperty("sentinel.enable");
            String dataSourceType = conditionContext.getEnvironment().getProperty("sentinel.dataSource.type");
            return enable != null && "true".equals(enable) && dataSourceType != null && DATA_SOURCE_TYPE_JDBC.equals(dataSourceType);
        }
    }

    private void initJdbcDataSource() {
        Assert.notNull(driverClassName, "sentinel.dataSource.jdbc.driverClassName is null, please check sentinel.properties");
        Assert.notNull(url, "sentinel.dataSource.jdbc.url is null, please check sentinel.properties");
        Assert.notNull(username, "sentinel.dataSource.jdbc.username is null, please check sentinel.properties");
        Assert.notNull(password, "sentinel.dataSource.jdbc.password is null, please check sentinel.properties");
        Assert.notNull(appName, "sentinel.dataSource.jdbc.appName is null, please check sentinel.properties");

        DataSource<List<Map<String, Object>>, List<FlowRule>> flowRuleDataSource = new JdbcDataSource(sentinelJdbcTemplate(), appName, new JdbcDataSource.JdbcFlowRuleParser(), ruleRefreshSec);
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());

        DataSource<List<Map<String, Object>>, List<DegradeRule>> degradeRuleDataSource = new JdbcDataSource(sentinelJdbcTemplate(), appName, new JdbcDataSource.JdbcDegradeRuleParser(), ruleRefreshSec);
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());

        DataSource<List<Map<String, Object>>, List<SystemRule>> dataSource = new JdbcDataSource(sentinelJdbcTemplate(), appName, new JdbcDataSource.JdbcSystemRuleParser(), ruleRefreshSec);
        SystemRuleManager.register2Property(dataSource.getProperty());
    }

    private void initZookeeperDataSource() {
        if (zookeeperUrl == null) {
            logWarn("zookeeperUrl is null");
            return;
        }

        initZookeeperDataSource("dubbo", "dubboPath", dubboPath);
        initZookeeperDataSource("activemq", "activemqPath", activemqPath);
    }

    private void initZookeeperDataSource(String name, String zkPathName, String zkPath) {
        if (zkPath == null) {
            log(zkPathName + " is null");
        } else {
            String flowPath = getPath(zkPath, FLOW_PATH);
            log(name + " flowPath=" + flowPath);
            String degradePath = getPath(zkPath, DEGRADE_PATH);
            log(name + " degradePath=" + degradePath);
            String systemPath = getPath(zkPath, SYSTEM_PATH);
            log(name +" systemPath=" + systemPath);

            DataSource<String, List<FlowRule>> flowRuleDataSource = new ZookeeperDataSource<>(zookeeperUrl, flowPath
                    , source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            FlowRuleManager.register2Property(flowRuleDataSource.getProperty());

            DataSource<String, List<DegradeRule>> degradeRuleDataSource = new ZookeeperDataSource<>(zookeeperUrl, degradePath
                    , source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {}));
            DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());

            DataSource<String, List<SystemRule>> systemRuleDataSource = new ZookeeperDataSource<>(zookeeperUrl, systemPath
                    , source -> JSON.parseObject(source, new TypeReference<List<SystemRule>>() {}));
            SystemRuleManager.register2Property(systemRuleDataSource.getProperty());
        }
    }

    private static String getPath(String... paths) {
        if (paths == null) {
            return null;
        }

        String result = "";

        for (String path : paths) {
            if (path == null) {
                continue;
            }

            if (!path.startsWith("/")) {
                result += "/";
            }

            result += path;
        }

        return result;
    }

    private static void log(String info) {
        log.info(SentinelSupportConstant.LOG_PRIFEX + info);
    }

    private static void logWarn(String info) {
        log.warn(SentinelSupportConstant.LOG_PRIFEX + info);
    }
}
