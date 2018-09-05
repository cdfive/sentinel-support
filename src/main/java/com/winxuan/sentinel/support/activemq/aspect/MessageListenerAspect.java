package com.winxuan.sentinel.support.activemq.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.winxuan.sentinel.support.SentinelSupportConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.command.ActiveMQMessage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import javax.jms.Message;

/**
 * JMS的MessageListener.onMessage，通过aspectj的aop方式加入sentinel埋点
 * @author cdfive
 * @date 2018-08-29
 */
@Slf4j
@Aspect
public class MessageListenerAspect {

    /**资源名称后缀，资源名称=队列名称_receive*/
    private static final String RECEIVE = "_receive";

    public MessageListenerAspect() {
        log.info(SentinelSupportConstant.LOG_PRIFEX + "MessageListenerAspect init");
    }

    @Pointcut("execution(* javax.jms.MessageListener.onMessage(..))&&args(message)")
    public void pointCutOnMessage(Message message) {
    }

    @Around("pointCutOnMessage(message)")
    public void aroundOnMessage(ProceedingJoinPoint pjp, Message message) throws Throwable {
        String name = ((ActiveMQMessage) message).getDestination().getPhysicalName() + RECEIVE;

        Entry entry = null;
        try {
            ContextUtil.enter(name);
            entry = SphU.entry(name, EntryType.OUT);
            pjp.proceed();
        } catch (BlockException ex) {
            System.out.println("Blocked: ");
        } finally {
            if (entry != null) {
                entry.exit();
            }
            ContextUtil.exit();
        }
    }
}
