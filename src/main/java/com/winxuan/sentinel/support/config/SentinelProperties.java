package com.winxuan.sentinel.support.config;

import com.alibaba.csp.sentinel.datasource.DataSource;
import com.alibaba.csp.sentinel.datasource.zookeeper.ZookeeperDataSource;
import com.alibaba.csp.sentinel.init.InitExecutor;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.winxuan.sentinel.support.SentinelSupportConstant;
import com.winxuan.sentinel.support.activemq.aspect.MessageListenerAspect;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

import javax.annotation.PostConstruct;
import java.util.List;

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

    @Value("${sentinel.enable:false}")
    private boolean enable;

    @Value("${sentinel.zkServer:localhost:2181}")
    private String zkServer;

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

        if (zkServer == null) {
            logWarn("zkServer is null");
            return;
        }

        initZookeeperDataSource("dubbo", "dubboPath", dubboPath);
        initZookeeperDataSource("activemq", "activemqPath", activemqPath);
    }

    @Bean
    @Conditional(SentinelCondition.class)
    public MessageListenerAspect messageListenerAspect() {
        return new MessageListenerAspect();
    }

    private static class SentinelCondition implements Condition {
        @Override
        public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
            String enable = conditionContext.getEnvironment().getProperty("sentinel.enable");
            return enable != null && "true".equals(enable);
        }
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

            DataSource<String, List<FlowRule>> flowRuleDataSource = new ZookeeperDataSource<>(zkServer, flowPath
                    , source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
            FlowRuleManager.register2Property(flowRuleDataSource.getProperty());

            DataSource<String, List<DegradeRule>> degradeRuleDataSource = new ZookeeperDataSource<>(zkServer, degradePath
                    , source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {}));
            DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());

            DataSource<String, List<SystemRule>> systemRuleDataSource = new ZookeeperDataSource<>(zkServer, systemPath
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
