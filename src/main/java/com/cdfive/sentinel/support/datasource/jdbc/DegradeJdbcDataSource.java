package com.cdfive.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * degrade rule JdbcDataSource
 * @author cdfive
 */
public class DegradeJdbcDataSource extends AbstractJdbcDataSource<List<DegradeRule>> {

    private static final String DEGRADE_RULE_TABLE = "sentinel_degrade_rule";

    private static final String INSERT_DEGRADE_RULE_SQL = "INSERT INTO sentinel_degrade_rule"
            + " (app_id,resource,limit_app,grade,_count,time_window,create_time,update_time,enabled,deleted)"
            + " VALUES(?,?,?,?,?,?,NOW(),NOW(),1,0)";

    public DegradeJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port) {
        super(dbDataSource, appName, ip, port);
    }

    public DegradeJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appName, ip, port, refreshSec);
    }

    public DegradeJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port) {
        super(dbDataSource, appId, appName, ip, port);
    }

    public DegradeJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appId, appName, ip, port, refreshSec);
    }

    @Override
    protected String initRuleTableName() {
        return DEGRADE_RULE_TABLE;
    }

    @Override
    protected List<DegradeRule> convert(List<Map<String, Object>> list) {
        if (list == null || list.size() == 0) {
            return null;
        }

        List<DegradeRule> degradeRules = new ArrayList<DegradeRule>();
        for (Map<String, Object> map : list) {
            DegradeRule degradeRule = new DegradeRule();
            degradeRules.add(degradeRule);

            degradeRule.setResource(getMapStringVal(map, "resource"));
            degradeRule.setLimitApp(getMapStringVal(map, "limit_app"));
            degradeRule.setGrade(getMapIntVal(map, "grade"));
            degradeRule.setCount(getMapDoubleVal(map, "_count"));
            degradeRule.setTimeWindow(getMapIntVal(map, "time_window"));
        }

        return degradeRules;
    }

    @Override
    protected String initInsertSql() {
        return INSERT_DEGRADE_RULE_SQL;
    }

    @Override
    protected List<Object[]> initInsertSqlParametersList(List<DegradeRule> value) {
        if (value == null || value.size() == 0) {
            return null;
        }

        List<Object[]> sqlParametersList = new ArrayList<Object[]>();
        for (DegradeRule degradeRule : value) {
            Object[] sqlParameters = new Object[6];
            sqlParametersList.add(sqlParameters);
            int i = 0;
            sqlParameters[i++] = getAppId();
            sqlParameters[i++] = degradeRule.getResource();
            sqlParameters[i++] = degradeRule.getLimitApp();
            sqlParameters[i++] = degradeRule.getGrade();
            sqlParameters[i++] = degradeRule.getCount();
            sqlParameters[i++] = degradeRule.getTimeWindow();
        }

        return sqlParametersList;
    }
}
