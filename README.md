# sentinel-support

sentinel学习以及方便接入

## sentinel-support目标

1. 统一管理sentinel的依赖版本，方便接入和扩展

2. 提供sentinel.properties配置文件支持：包括是否启用sentinel、指定数据源

3. 提供一个定制的JdbcDataSource实现

4. ActiveMQ支持：通过BrokerFilter(send)、MessageListener中onMessage的aspect(receive)，加入sentinel的埋点


## 业务工程接入步骤

### 1.添加sentinel-support依赖

```xml
<dependency>
    <groupId>com.winxuan</groupId>
    <artifactId>sentinel-support</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2.resource目录下增加sentinel.properties配置文件

* jdbc作为数据源(**推荐**)：

```
# 是否启用,默认false
sentinel.enable=true

# dataSource类型,zookeeper或jdbc,默认jdbc
sentinel.dataSource.type=jdbc

# sentinel_db数据库连接
sentinel.dataSource.jdbc.driverClassName=com.mysql.jdbc.Driver
sentinel.dataSource.jdbc.url=jdbc:mysql://localhost:3306/sentinel_db
sentinel.dataSource.jdbc.username=root
sentinel.dataSource.jdbc.password=root
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

### 3.根据业务场景，在数据库或zookpeer的配置中心界面上添加、修改规则

### 4.修改启动脚本，增加JVM参数

-Dcsp.sentinel.dashboard.server=consoleIp:port // 控制台ip和端口</br>
-Dcsp.sentinel.api.port=8719 // 客户端api监控端口,默认8719，单机多个应用需要配置不同的端口</br> 
-Dproject.name=xxx // 应用名称
-Djava.net.preferIPv4Stack=true // 解决加载ipv6模块的问题

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


sentinel_db database ddl:
```sql
-- drop table
DROP TABLE IF EXISTS sentinel_app;
DROP TABLE IF EXISTS sentinel_flow_rule;
DROP TABLE IF EXISTS sentinel_degrade_rule;
DROP TABLE IF EXISTS sentinel_system_rule;

-- create table
-- 应用表
CREATE TABLE `sentinel_app` (
  `id` INT NOT NULL COMMENT 'id，主键',
  `_name` VARCHAR(100) NOT NULL COMMENT '应用名称',
  `chn_name` VARCHAR(100) COMMENT '应用中文名称',
  `description` VARCHAR(500) COMMENT '应用描述',
  `host_name` VARCHAR(100) COMMENT '应用所在机器名称',
  `ip` VARCHAR(50) NOT NULL COMMENT '应用ip地址',
  `_port` INT NOT NULL COMMENT '端口号,指sentinel的端口号,csp.sentinel.api.port,默认8719', 
  `create_user_id` INT COMMENT '创建人id',
  `update_user_id` INT COMMENT '修改人id',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '修改时间',
  `enabled` TINYINT NOT NULL COMMENT '是否启用 0-禁用 1-启用',
  `deleted` TINYINT COMMENT '是否删除 0-正常 1-删除',
  INDEX name_idx(`_name`) USING BTREE,
  INDEX ip_idx(`ip`) USING BTREE,
  INDEX port_idx(`_port`) USING BTREE,
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8;

-- 流控规则表
CREATE TABLE `sentinel_flow_rule` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT 'id，主键',
  `app_id` INT NOT NULL COMMENT '应用id',
  `resource` VARCHAR(200) NOT NULL COMMENT '规则的资源描述',
  `resource_type` VARCHAR(20) COMMENT '资源类型 activemq,dubbo,rest,...',
  `resource_description` VARCHAR(500) COMMENT '资源描述',
  `limit_app` VARCHAR(100) NOT NULL COMMENT '被限制的应用,授权时候为逗号分隔的应用集合，限流时为单个应用',
  `grade` TINYINT NOT NULL COMMENT '0-THREAD 1-QPS',
  `_count` DOUBLE NOT NULL COMMENT '数量',
  `strategy` INT NOT NULL COMMENT '0-直接 1-关联 2-链路',
  `ref_resource` VARCHAR(200) COMMENT '关联的资源',
  `control_behavior` TINYINT NOT NULL COMMENT '0-直接拒绝 1-冷启动 2-匀速器',
  `warm_up_period_sec` INT COMMENT '冷启动时间(秒)',
  `max_queueing_time_ms` INT COMMENT '匀速器最大排队时间(毫秒)',
  `create_user_id` INT COMMENT '创建人id',
  `update_user_id` INT COMMENT '修改人id',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '修改时间',
  `change_status` TINYINT COMMENT '保留字段,未来做增量更新使用 0-未改变 1-新增 2-修改 3-删除',
  `enabled` TINYINT NOT NULL COMMENT '是否启用 0-禁用 1-启用',
  `deleted` TINYINT NOT NULL COMMENT '是否删除 0-正常 1-删除',
  INDEX app_id_idx(`app_id`) USING BTREE,
  INDEX resource_idx(`resource`) USING BTREE,
  INDEX enabled_idx(`enabled`) USING BTREE,
  INDEX deleted_idx(`deleted`) USING BTREE,
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8;


-- 熔断降级规则表
CREATE TABLE `sentinel_degrade_rule` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT 'id，主键',
  `app_id` INT NOT NULL COMMENT '应用id',
  `resource` VARCHAR(200) NOT NULL COMMENT '规则的资源描述',
  `resource_type` VARCHAR(20) COMMENT '资源类型 activemq,dubbo,rest,...',
  `resource_description` VARCHAR(500) COMMENT '资源描述',
  `limit_app` VARCHAR(100) NOT NULL COMMENT '被限制的应用,授权时候为逗号分隔的应用集合，限流时为单个应用',
  `grade` TINYINT NOT NULL COMMENT '0-根据响应时间 1-根据异常比例',		
  `_count` DOUBLE NOT NULL COMMENT '数量',
  `time_window` INT COMMENT '降级后恢复时间',
  `create_user_id` INT COMMENT '创建人id',
  `update_user_id` INT COMMENT '修改人id',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '修改时间',
  `change_status` TINYINT COMMENT '保留字段,未来做增量更新使用 0-未改变 1-新增 2-修改 3-删除',
  `enabled` TINYINT NOT NULL COMMENT '是否启用 0-禁用 1-启用',
  `deleted` TINYINT NOT NULL COMMENT '是否删除 0-正常 1-删除',
  INDEX app_id_idx(`app_id`) USING BTREE,
  INDEX resource_idx(`resource`) USING BTREE,
  INDEX enabled_idx(`enabled`) USING BTREE,
  INDEX deleted_idx(`deleted`) USING BTREE,
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8;

-- 系统负载保护规则表
CREATE TABLE `sentinel_system_rule` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT 'id，主键',
  `app_id` INT NOT NULL COMMENT '应用id',
  `highest_system_load` DOUBLE COMMENT '最大系统负载',
  `qps` DOUBLE COMMENT 'QPS',
  `avg_rt` LONG COMMENT '平均响应时间',
  `max_thread` LONG COMMENT '最大线程数',
  `create_user_id` INT COMMENT '创建人id',
  `update_user_id` INT COMMENT '修改人id',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '修改时间',
  `change_status` TINYINT COMMENT '保留字段,未来做增量更新使用 0-未改变 1-新增 2-修改 3-删除',
  `enabled` TINYINT NOT NULL COMMENT '是否启用 0-禁用 1-启用',
  `deleted` TINYINT NOT NULL COMMENT '是否删除 0-正常 1-删除',
  INDEX app_id_idx(`app_id`) USING BTREE,
  INDEX enabled_idx(`enabled`) USING BTREE,
  INDEX deleted_idx(`deleted`) USING BTREE,
  PRIMARY KEY (`id`)
) ENGINE=INNODB DEFAULT CHARSET=utf8;
```

sentinel_db database init data:
```sql
-- init data start
INSERT INTO sentinel_app(id,_name,ip,_port,create_time,update_time,enabled,deleted) VALUES(1,'sentinel-support-dubbo-provider-demo','192.168.1.101',8719,NOW(),NOW(),1,0);
INSERT INTO sentinel_app(id,_name,ip,_port,create_time,update_time,enabled,deleted) VALUES(2,'sentinel-support-dubbo-provider-demo','192.168.1.101',8720,NOW(),NOW(),1,0);

INSERT INTO sentinel_flow_rule(app_id,resource,limit_app,grade,_count,strategy,ref_resource,control_behavior,warm_up_period_sec,max_queueing_time_ms,create_time,update_time,enabled,deleted) 
VALUES(1,'com.demo.FooService:hello(java.lang.String)','default',1,5,0,NULL,0,NULL,NULL,NOW(),NOW(),1,0);
INSERT INTO sentinel_flow_rule(app_id,resource,limit_app,grade,_count,strategy,ref_resource,control_behavior,warm_up_period_sec,max_queueing_time_ms,create_time,update_time,enabled,deleted) 
VALUES(2,'com.demo.FooService:hello(java.lang.String)','default',1,10,0,NULL,0,NULL,NULL,NOW(),NOW(),1,0);

INSERT INTO sentinel_degrade_rule(app_id,resource,limit_app,grade,_count,time_window,create_time,update_time,enabled,deleted) 
VALUES(1,'com.demo.FooService:hello(java.lang.String)','default',0,5,10,NOW(),NOW(),1,0);
INSERT INTO sentinel_degrade_rule(app_id,resource,limit_app,grade,_count,time_window,create_time,update_time,enabled,deleted) 
VALUES(2,'com.demo.FooService:hello(java.lang.String)','default',1,10,5,NOW(),NOW(),1,0);

INSERT INTO sentinel_system_rule(app_id,highest_system_load,qps,avg_rt,max_thread,create_time,update_time,enabled,deleted) 
VALUES(1,3,10,20,10,NOW(),NOW(),1,0);
INSERT INTO sentinel_system_rule(app_id,highest_system_load,qps,avg_rt,max_thread,create_time,update_time,enabled,deleted) 
VALUES(2,2,20,40,20,NOW(),NOW(),1,0);
-- init data end
```