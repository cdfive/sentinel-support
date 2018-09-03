# sentinel-support
sentinel学习以及方便接入

## 业务工程接入步骤
1. 添加sentinel-support依赖
```xml
<dependency>
    <groupId>com.winxuan</groupId>
    <artifactId>sentinel-support</artifactId>
    <version>0.1-SNAPSHOT</version>
</dependency>
```

2. resource目录下增加sentinel.properties配置文件

jdbc作为数据源的配置(推荐) 
<code>
\# 是否启用,默认false
sentinel.enable=true

\# dataSource类型,zookeeper或jdbc
sentinel.dataSource.type=jdbc

\# sentinel_db数据库连接
sentinel.dataSource.jdbc.driverClassName=com.mysql.jdbc.Driver
sentinel.dataSource.jdbc.url=jdbc:mysql://localhost:3306/sentinel_db
sentinel.dataSource.jdbc.username=root
sentinel.dataSource.jdbc.password=root
\# 应用名称,对应sentinel_app表中的列app_name
sentinel.dataSource.jdbc.appName=sentinel-support-demo
\# 定时刷新规则的时间间隔(秒),默认30秒
sentinel.dataSource.jdbc.ruleRefreshSec=30
</code>

zookeeper作为数据源的配置
<code>
\# 是否启用,默认false
sentinel.enable=true

\# dataSource类型,zookeeper或jdbc
sentinel.dataSource.type=jdbc

\# dataSource类型是zookeeper的配置
\# zookeeper地址
sentinel.dataSource.zookeeper.url=zk.test.winxuan.io:8900
\# sentinel规则路径,不同规则后面路径规定,流控规则:/flow,熔断降级规则:/degrade,系统负载保护规则:/system
sentinel.dubbo.path=/winxuan.config/toolkit/dev/1.0.1/xiejihan.test.dubbo.sentinel.rule
</code>

3. 根据jdbc或zookeeper类型，在数据库或zookpeer的配置中心界面上添加、修改规则