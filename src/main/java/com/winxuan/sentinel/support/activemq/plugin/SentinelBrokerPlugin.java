package com.winxuan.sentinel.support.activemq.plugin;

import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerPlugin;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Sentinel的activemq broker插件
 * @author cdfive
 * @date 2018-08-27
 */
@Slf4j
@Setter
@Getter
public class SentinelBrokerPlugin implements BrokerPlugin {

    /**日志前缀*/
    private static final String LOG_PRIFEX = "[SentinelBrokerPlugin]";

    /**zk地址*/
    private String zkServer;

    /**MQ流量控制规则的zk节点*/
    private String mqFlowRulePath;

    /**MQ熔断降级规则的zk节点*/
    private String mqDegradeRulePath;

    /**MQ系统保护规则的zk节点*/
    private String mqSystemRulePath;

    @Override
    public Broker installPlugin(Broker broker) throws Exception {
        log("installPlugin");

        checkProperty("zkServer", zkServer);
        checkProperty("mqFlowRulePath", mqFlowRulePath);
        checkProperty("mqDegradeRulePath", mqDegradeRulePath);
        checkProperty("mqSystemRulePath", mqSystemRulePath);

        log("InitExecutor.doInit start");
        InitExecutor.doInit();// Env中static块调了该方法，但在初始化才执行，这里为了启动时就能访问http规则提前调用
        log("InitExecutor.doInit end");

        log("FlowRule zookeeper register start");
        ReadableDataSource<String, List<FlowRule>> flowRuleDataSource = new ZookeeperDataSource<>(zkServer, mqFlowRulePath,
                source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {}));
        FlowRuleManager.register2Property(flowRuleDataSource.getProperty());
        log("FlowRule zookeeper register end");

        log("DegradeRule zookeeper register start");
        ReadableDataSource<String, List<DegradeRule>> degradeRuleDataSource = new ZookeeperDataSource<>(zkServer, mqDegradeRulePath,
                source -> JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {}));
        DegradeRuleManager.register2Property(degradeRuleDataSource.getProperty());
        log("DegradeRule zookeeper register end");

        log("SystemRule zookeeper register start");
        ReadableDataSource<String, List<SystemRule>> systemRuleDataSource = new ZookeeperDataSource<>(zkServer, mqSystemRulePath,
                source -> JSON.parseObject(source, new TypeReference<List<SystemRule>>() {}));
        SystemRuleManager.register2Property(systemRuleDataSource.getProperty());
        log("SystemRule zookeeper register end");

        return new SentinelBrokerFilter(broker);
    }

    private void checkProperty(String name, String value) {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException(name + "属性不能为空，请检查activemq.xml中SentinelBrokerPlugin的Bean配置");
        }
    }

    private void log(String info) {
        log.info(LOG_PRIFEX + info);
    }
}
