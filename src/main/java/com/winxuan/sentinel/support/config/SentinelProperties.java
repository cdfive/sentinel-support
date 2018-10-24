package com.winxuan.sentinel.support.config;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.zookeeper.ZookeeperDataSource;
import com.alibaba.csp.sentinel.init.InitExecutor;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.transport.config.TransportConfig;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.csp.sentinel.util.AppNameUtil;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.winxuan.sentinel.support.SentinelSupportConstant;
import com.winxuan.sentinel.support.activemq.aspect.MessageListenerAspect;
import com.winxuan.sentinel.support.datasource.jdbc.WxDegradeJdbcDataSource;
import com.winxuan.sentinel.support.datasource.jdbc.WxFlowJdbcDataSource;
import com.winxuan.sentinel.support.datasource.jdbc.WxSystemJdbcDataSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 提供sentinel.properties配置文件支持：包括启用、指定数据源、ActiveMQ的MessageListenerAspect
 * 暂时仅支持jdbc、zookeeper数据源
 *
 * @author cdfive
 * @date 2018-08-29
 */
@Slf4j
@Data
@Configuration
@PropertySource(value = "classpath:sentinel.properties", ignoreResourceNotFound = true)
public class SentinelProperties {

    /**数据源类型常量*/
    private static final String DATA_SOURCE_TYPE_JDBC = "jdbc";
    private static final String DATA_SOURCE_TYPE_ZOOKEEPER = "zookeeper";

    /**3种类型规则在zookeeper节点路径*/
    private static final String FLOW_PATH = "flow";
    private static final String DEGRADE_PATH = "degrade";
    private static final String SYSTEM_PATH = "system";

    /**sentinel db DataSource*/
    private DruidDataSource sentinelDbDataSource;

    /**是否启用sentinel支持，默认false*/
    @Value("${sentinel.enable:false}")
    private boolean enable;

    /**数据源类型，jdbc或zookeeper，默认jdbc*/
    @Value("${sentinel.dataSource.type:jdbc}")
    private String dataSourceType;


    /**============jdbc datasource配置============*/
    @Value("${sentinel.dataSource.jdbc.driverClassName:#{null}}")
    private String driverClassName;

    @Value("${sentinel.dataSource.jdbc.url:#{null}}")
    private String url;

    @Value("${sentinel.dataSource.jdbc.username:#{null}}")
    private String username;

    @Value("${sentinel.dataSource.jdbc.password:#{null}}")
    private String password;

    /**DruidDataSource config*/
    @Value("${sentinel.dataSource.jdbc.initial-size:#{null}}")
    private Integer initialSize;
    @Value("${sentinel.dataSource.jdbc.maxActive:#{null}}")
    private Integer maxActive;
    @Value("${sentinel.dataSource.jdbc.maxIdle:#{null}}")
    private Integer maxIdle;
    @Value("${sentinel.dataSource.jdbc.minIdle:#{null}}")
    private Integer minIdle;
    @Value("${sentinel.dataSource.jdbc.log-abandoned:#{null}}")
    private Boolean logAbandoned;
    @Value("${sentinel.dataSource.jdbc.remove-abandoned:#{null}}")
    private Boolean removeAbandoned;
    @Value("${sentinel.dataSource.jdbc.removeAbandonedTimeout:#{null}}")
    private Integer removeAbandonedTimeout;
    @Value("${sentinel.dataSource.jdbc.max-wait:#{null}}")
    private Integer maxWait;
    @Value("${sentinel.dataSource.jdbc.min-evictable-idle-time-millis:#{null}}")
    private Long minEvictableIdleTimeMillis;
    @Value("${sentinel.dataSource.jdbc.time-between-eviction-runs-millis:#{null}}")
    private Long timeBetweenEvictionRunsMillis;
    @Value("${sentinel.dataSource.jdbc.test-on-borrow:#{null}}")
    private Boolean testOnBorrow;
    @Value("${sentinel.dataSource.jdbc.test-while-idle:#{null}}")
    private Boolean testWhileIdle;
    @Value("${sentinel.dataSource.jdbc.test-on-return:#{null}}")
    private Boolean testOnReturn;

    /**============zookeeper datasource配置============*/
    @Value("${sentinel.dataSource.zookeeper.url:localhost:2181}")
    private String zookeeperUrl;// zookeeper地址

    @Value("${sentinel.dubbo.path:#{null}}")
    private String dubboPath;// dubbo规则路径,不同规则后面路径规定,流控规则/flow,熔断降级规则/degrade,系统负载保护规则/system

    @Value("${sentinel.activemq.path:#{null}}")
    private String activemqPath;// activemq规则路径,不同规则后面路径固定,流控规则/flow,熔断降级规则/degrade,系统负载保护规则/system

    @PostConstruct
    public void init() {
        log(this.toString());

        if (!enable) {
            logDebug("sentinel is not enable");
            return;
        }

        log("sentinel is enable");

        log("InitExecutor.doInit() start");
        InitExecutor.doInit();
        log("InitExecutor.doInit() end");

        String appName = AppNameUtil.getAppName();
        log("appName=" + appName);

        String hostName = HostNameUtil.getHostName();
        log("hostName=" + hostName);

        String ip = HostNameUtil.getIp();
        log("ip=" + ip);

        String transportPort = TransportConfig.getPort();
        if (StringUtils.isEmpty(transportPort)) {
            logError("can't get transport port, check start JVM argment -Dcsp.sentinel.api.port=xxx");
            return;
        }
        Integer port = Integer.parseInt(transportPort);
        log("port=" + port);

        if (dataSourceType == null) {
            logWarn("dataSourceType is null");
            return;
        }

        if (!DATA_SOURCE_TYPE_JDBC.equals(dataSourceType) && !DATA_SOURCE_TYPE_ZOOKEEPER.equals(dataSourceType)) {
            logWarn("dataSourceType=" + dataSourceType + " invalid. now supported: jdbc or zookeeper");
            return;
        }

        if (DATA_SOURCE_TYPE_JDBC.equals(dataSourceType)) {
            log("initJdbcDataSource");
            initJdbcDataSource(appName, ip, port);
            return;
        }

        if (DATA_SOURCE_TYPE_ZOOKEEPER.equals(dataSourceType)) {
            log("initZookeeperDataSource");
            initZookeeperDataSource();
            return;
        }
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

    private void initJdbcDataSource(String appName, String ip, Integer port) {
        Assert.notNull(driverClassName, "sentinel.dataSource.jdbc.driverClassName is null, please check sentinel.properties");
        Assert.notNull(url, "sentinel.dataSource.jdbc.url is null, please check sentinel.properties");
        Assert.notNull(username, "sentinel.dataSource.jdbc.username is null, please check sentinel.properties");
        Assert.notNull(password, "sentinel.dataSource.jdbc.password is null, please check sentinel.properties");

        sentinelDbDataSource = new DruidDataSource();
        sentinelDbDataSource.setUrl(url);
        sentinelDbDataSource.setUsername(username);
        sentinelDbDataSource.setPassword(password);
        sentinelDbDataSource.setDriverClassName(driverClassName);

        /**DruidDataSource config*/
        if (initialSize != null) { sentinelDbDataSource.setInitialSize(initialSize); }
        if (maxActive != null) { sentinelDbDataSource.setMaxActive(maxActive); }
        if (minIdle != null) { sentinelDbDataSource.setMinIdle(minIdle); }
        if (logAbandoned != null) { sentinelDbDataSource.setLogAbandoned(logAbandoned); }
        if (removeAbandoned != null) { sentinelDbDataSource.setRemoveAbandoned(removeAbandoned); }
        if (removeAbandonedTimeout != null) { sentinelDbDataSource.setRemoveAbandonedTimeout(removeAbandonedTimeout); }
        if (maxWait != null) { sentinelDbDataSource.setMaxWait(maxWait); }
        if (minEvictableIdleTimeMillis != null) { sentinelDbDataSource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis); }
        if (timeBetweenEvictionRunsMillis != null) { sentinelDbDataSource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis); }
        if (testOnBorrow != null) { sentinelDbDataSource.setTestOnBorrow(testOnBorrow); }
        if (testWhileIdle != null) { sentinelDbDataSource.setTestWhileIdle(testWhileIdle); }
        if (testOnReturn != null) { sentinelDbDataSource.setTestOnReturn(testOnReturn); }

        WxFlowJdbcDataSource flowRuleDataSource = new WxFlowJdbcDataSource(sentinelDbDataSource, appName, ip, port);
        Integer appId = flowRuleDataSource.getAppId();
        if (appId != null) {
            FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
            WritableDataSourceRegistry.registerFlowDataSource(flowRuleDataSource);

            WxDegradeJdbcDataSource degradeJdbcDataSource = new WxDegradeJdbcDataSource(sentinelDbDataSource, appId, appName, ip, port);
            DegradeRuleManager.register2Property(degradeJdbcDataSource.getProperty());
            WritableDataSourceRegistry.registerDegradeDataSource(degradeJdbcDataSource);

            WxSystemJdbcDataSource systemJdbcDataSource = new WxSystemJdbcDataSource(sentinelDbDataSource, appId, appName, ip, port);
            SystemRuleManager.register2Property(systemJdbcDataSource.getProperty());
            WritableDataSourceRegistry.registerSystemDataSource(systemJdbcDataSource);
        }
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

            ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new ZookeeperDataSource<List<FlowRule>>(zookeeperUrl, flowPath,
                new Converter<String, List<FlowRule>>() {
                @Override
                public List<FlowRule> convert(String source) {
                    return JSON.parseObject(source, new TypeReference<List<FlowRule>>(){});
                }
            });
            FlowRuleManager.register2Property(flowRuleDataSource.getProperty());

            ReadableDataSource<String, List<DegradeRule>> degradeRuleDataSource = new ZookeeperDataSource<List<DegradeRule>>(zookeeperUrl, degradePath,
                new Converter<String, List<DegradeRule>>() {
                    @Override
                    public List<DegradeRule> convert(String source) {
                        return JSON.parseObject(source, new TypeReference<List<DegradeRule>>(){});
                    }
                }
            );
            DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());

            ReadableDataSource<String, List<SystemRule>> systemRuleDataSource = new ZookeeperDataSource<List<SystemRule>>(zookeeperUrl, systemPath,
                new Converter<String, List<SystemRule>>() {
                    @Override
                    public List<SystemRule> convert(String source) {
                        return JSON.parseObject(source, new TypeReference<List<SystemRule>>(){});
                    }
                }
            );
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

    private static void logDebug(String info) {
        log.debug(SentinelSupportConstant.LOG_PRIFEX + info);
    }

    private static void log(String info) {
        log.info(SentinelSupportConstant.LOG_PRIFEX + info);
    }

    private static void logWarn(String info) {
        log.warn(SentinelSupportConstant.LOG_PRIFEX + info);
    }

    private static void logError(String info) {
        log.error(SentinelSupportConstant.LOG_PRIFEX + info);
    }
}
