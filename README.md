# sentinel-support

sentinel学习以及方便接入

## sentinel-support目标

1. 统一管理sentinel的依赖版本，方便接入和扩展

2. 提供sentinel.properties配置文件支持：包括启用、指定数据源

3. activemq支持：通过BrokerFilter(send)、MessageListener中onMessage的aspect(receive)，加入sentinel的埋点


## 业务工程接入步骤

### 1.添加sentinel-support依赖

```xml
<dependency>
    <groupId>com.winxuan</groupId>
    <artifactId>sentinel-support</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>
```

### 2.resource目录下增加sentinel.properties配置文件

* jdbc作为数据源(**推荐**)：

```
# 是否启用,默认true
sentinel.enable=true

# dataSource类型,zookeeper或jdbc,默认jdbc
sentinel.dataSource.type=jdbc

# sentinel_db数据库连接
sentinel.dataSource.jdbc.driverClassName=com.mysql.jdbc.Driver
sentinel.dataSource.jdbc.url=jdbc:mysql://localhost:3306/sentinel_db
sentinel.dataSource.jdbc.username=root
sentinel.dataSource.jdbc.password=root
# 应用名称,对应sentinel_app表中的列app_name
sentinel.dataSource.jdbc.appName=sentinel-support-demo
# 定时刷新规则的时间间隔(秒),默认30秒
sentinel.dataSource.jdbc.ruleRefreshSec=30
```

* zookeeper作为数据源:

```
# 是否启用,默认true
sentinel.enable=true

# dataSource类型,zookeeper或jdbc,默认jdbc
sentinel.dataSource.type=jdbc

# dataSource类型是zookeeper的配置
# zookeeper地址
sentinel.dataSource.zookeeper.url=zk.test.winxuan.io:8900
# dubbo规则根path,3种类型规则path：流控规则/flow,熔断降级规则/degrade,系统负载保护规则/system
sentinel.dubbo.path=/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.dubbo.sentinel.rule
# activemq规则根path,3种类型规则path：流控规则/flow,熔断降级规则/degrade,系统负载保护规则/system
sentinel.activemq.path=/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.activemq.sentinel.rule
```

> 目前zookeeper上配置规则，不同类型规则path固定：流控规则/flow,熔断降级规则/degrade,系统负载保护规则/system</br>
一个应用的某类型规则列表，是一个大的json array // 考虑如何改进？

### 3.根据业务场景，在数据库或zookpeer的配置中心界面上添加、修改规则即可

## ActiveMQ的SentinelBrokerFilter插件

说明：插件通过扩展BrokerFilter重写send方法，加入sentinel埋点；其中，资源名称 = 队列名称+"_send"

> MessageListenerAspect中，接收消息埋点的资源名称=队列名称+"_receive

* 相关类：
    com.winxuan.sentinel.support.activemq.plugin.SentinelBrokerPlugin
    com.winxuan.sentinel.support.activemq.plugin.SentinelBrokerFilter

* 打包：执行assembly插件，assembly:assembly，target目录下会生成sentinelBrokerPlugin.jar，该jar仅包含了上面两个类

> 使用package打包不会生成sentinelBrokerPlugin.jar，因为pom.xml的assembly插件配置，<phase>package</phase>注释掉了，
  打开注释会影响到install，暂时没找到解决方法T_T!
    
* 运行：修改ActiveMQ/conf/activemq.xml文件，在broker标签下增加如下配置：
```xml
<plugins>
    <bean xmlns="http://www.springframework.org/schema/beans" id="sentinelBrokerPlugin" class="com.winxuan.sentinel.support.activemq.plugin.SentinelBrokerPlugin">
        <property name="zkServer" value="zk.test.winxuan.io:8900" />
        <property name="mqFlowRulePath" value="/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.mq.sentinel.rule/flow" />
        <property name="mqDegradeRulePath" value="/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.mq.sentinel.rule/degrade" />
        <property name="mqSystemRulePath" value="/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.mq.sentinel.rule/system" />
    </bean>
</plugins>
```

> 插件使用zookeeper数据源；其中zookeeper地址和规则path请根据实际情况修改；/flow、/degrade、/system为固定后缀

* ActiveMQ接入sentinel控制台：修改ActiveMQ/bin/activemq.bat脚本，%ACTIVEMQ_OPTS%后面增加参数：</br>
-Dcsp.sentinel.dashboard.server=localhost:8080 -Dproject.name=ActiveMQ

> 测试使用ActiveMQ的版本：5.15.5


## 参考示例
sentinel-support-demo的[README.md](https://github.com/cdfive/sentinel-support-demo)
