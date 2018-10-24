package com.winxuan.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * flow rule JdbcDataSource
 * @author cdfive
 * @date 2018-09-08
 */
public class WxFlowJdbcDataSource extends WxAbstractJdbcDataSource<List<FlowRule>> {

    private static final String FLOW_RULE_TABLE = "sentinel_flow_rule";

    private static final String INSERT_FLOW_RULE_SQL = "INSERT INTO sentinel_flow_rule"
            + " (app_id,resource,limit_app,grade,_count,strategy,ref_resource,control_behavior,warm_up_period_sec,max_queueing_time_ms,create_time,update_time,enabled,deleted)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),1,0)";

    public WxFlowJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port) {
        super(dbDataSource, appName, ip, port);
    }

    public WxFlowJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appName, ip, port, refreshSec);
    }

    public WxFlowJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port) {
        super(dbDataSource, appId, appName, ip, port);
    }

    public WxFlowJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appId, appName, ip, port, refreshSec);
    }

    @Override
    protected String initRuleTableName() {
        return FLOW_RULE_TABLE;
    }

    @Override
    protected List<FlowRule> convert(List<Map<String, Object>> list) {
        if (list == null || list.size() == 0) {
            return null;
        }

        List<FlowRule> flowRules = new ArrayList<FlowRule>();
        for (Map<String, Object> map : list) {
            FlowRule flowRule = new FlowRule();
            flowRules.add(flowRule);

            flowRule.setResource(getMapStringVal(map, "resource"));
            flowRule.setLimitApp(getMapStringVal(map, "limit_app"));
            flowRule.setGrade(getMapIntVal(map, "grade"));
            flowRule.setCount(getMapDoubleVal(map, "_count"));
            flowRule.setStrategy(getMapIntVal(map, "strategy"));
            flowRule.setRefResource(getMapStringVal(map, "ref_resource"));
            flowRule.setControlBehavior(getMapIntVal(map, "control_behavior"));
            flowRule.setWarmUpPeriodSec(getMapIntVal(map, "warm_up_period_sec"));
            flowRule.setMaxQueueingTimeMs(getMapIntVal(map, "max_queueing_time_ms"));
        }

        return flowRules;
    }

    @Override
    protected String initInsertSql() {
        return INSERT_FLOW_RULE_SQL;
    }

    @Override
    protected List<Object[]> initInsertSqlParametersList(List<FlowRule> value) {
        if (value == null || value.size() == 0) {
            return null;
        }

        List<Object[]> sqlParametersList = new ArrayList<Object[]>();
        for (FlowRule flowRule : value) {
            Object[] sqlParameters = new Object[10];
            sqlParametersList.add(sqlParameters);
            int i = 0;
            sqlParameters[i++] = getAppId();
            sqlParameters[i++] = flowRule.getResource();
            sqlParameters[i++] = flowRule.getLimitApp();
            sqlParameters[i++] = flowRule.getGrade();
            sqlParameters[i++] = flowRule.getCount();
            sqlParameters[i++] = flowRule.getStrategy();
            sqlParameters[i++] = flowRule.getRefResource();
            sqlParameters[i++] = flowRule.getControlBehavior();
            sqlParameters[i++] = flowRule.getWarmUpPeriodSec();
            sqlParameters[i++] = flowRule.getMaxQueueingTimeMs();
        }

        return sqlParametersList;
    }
}
