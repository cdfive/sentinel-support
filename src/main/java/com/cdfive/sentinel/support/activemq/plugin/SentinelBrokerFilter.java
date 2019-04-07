package com.cdfive.sentinel.support.activemq.plugin;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerFilter;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.command.Message;

/**
 * Sentinel的activemq broker过滤器
 * @author cdfive
 */
@Slf4j
public class SentinelBrokerFilter extends BrokerFilter {

    /**日志前缀*/
    private static final String LOG_PRIFEX = "[SentinelBrokerFilter]";

    /**资源名称后缀，资源名称=队列名称_send*/
    private static final String SEND = "_send";

    public SentinelBrokerFilter(Broker next) {
        super(next);
    }

    @Override
    public void send(ProducerBrokerExchange producerExchange, Message messageSend) throws Exception {
        String name = messageSend.getDestination().getPhysicalName() + SEND;
        Entry entry = null;
        try {
            ContextUtil.enter(name);
            entry = SphU.entry(name, EntryType.OUT);
            log("send message=>" + messageSend.getMessage().getMessageId().toString());
            super.send(producerExchange, messageSend);
        } catch (BlockException ex) {
            logWarn("blocked=>" + messageSend.getMessage().getMessageId().toString());
        } finally {
            if (entry != null) {
                entry.exit();
            }
            ContextUtil.exit();
        }
    }

    private void log(String info) {
        log.info(LOG_PRIFEX + info);
    }

    private void logWarn(String info) {
        log.warn(LOG_PRIFEX + info);
    }
}
