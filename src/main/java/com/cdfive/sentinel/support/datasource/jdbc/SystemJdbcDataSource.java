package com.cdfive.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.slots.system.SystemRule;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * system rule JdbcDataSource
 * @author cdfive
 * @date 2018-09-08
 */
public class SystemJdbcDataSource extends AbstractJdbcDataSource<List<SystemRule>> {

    private static final String SYSTEM_RULE_TABLE = "sentinel_system_rule";

    private static final String INSERT_SYSTEM_RULE_SQL = "INSERT INTO sentinel_system_rule"
            + " (app_id,highest_system_load,qps,avg_rt,max_thread,create_time,update_time,enabled,deleted)"
            + " VALUES(?,?,?,?,?,NOW(),NOW(),1,0)";

    public SystemJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port) {
        super(dbDataSource, appName, ip, port);
    }

    public SystemJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appName, ip, port, refreshSec);
    }

    public SystemJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port) {
        super(dbDataSource, appId, appName, ip, port);
    }

    public SystemJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port, Long refreshSec) {
        super(dbDataSource, appId, appName, ip, port, refreshSec);
    }

    @Override
    protected String initRuleTableName() {
        return SYSTEM_RULE_TABLE;
    }

    @Override
    protected List<SystemRule> convert(List<Map<String, Object>> list) {
        if (list == null || list.size() == 0) {
            return null;
        }

        List<SystemRule> systemRules = new ArrayList<SystemRule>();
        for (Map<String, Object> map : list) {
            SystemRule systemRule = new SystemRule();
            systemRules.add(systemRule);

            systemRule.setResource(getMapStringVal(map, "resource"));
            systemRule.setLimitApp(getMapStringVal(map, "limit_app"));
            systemRule.setHighestSystemLoad(getMapDoubleVal(map, "highest_system_load"));
            systemRule.setQps(getMapDoubleVal(map, "qps"));
            systemRule.setAvgRt(getMapLongVal(map, "avg_rt"));
            systemRule.setMaxThread(getMapLongVal(map, "max_thread"));
        }

        return systemRules;
    }

    @Override
    protected String initInsertSql() {
        return INSERT_SYSTEM_RULE_SQL;
    }

    @Override
    protected List<Object[]> initInsertSqlParametersList(List<SystemRule> value) {
        if (value == null || value.size() == 0) {
            return null;
        }

        List<Object[]> sqlParametersList = new ArrayList<Object[]>();
        Object[] sqlParameters = new Object[5];
        sqlParametersList.add(sqlParameters);

        sqlParameters[0] = getAppId();

        for (SystemRule systemRule : value) {
            if (systemRule.getHighestSystemLoad() != -1.0D) {
                sqlParameters[1] = systemRule.getHighestSystemLoad();
                continue;
            }

            if (systemRule.getQps() != -1.0D) {
                sqlParameters[2] = systemRule.getQps();
                continue;
            }

            if (systemRule.getAvgRt() != -1L) {
                sqlParameters[3] = systemRule.getAvgRt();
                continue;
            }

            if (systemRule.getMaxThread() != -1L) {
                sqlParameters[4] = systemRule.getMaxThread();
                continue;
            }
        }

        return sqlParametersList;
    }

}
