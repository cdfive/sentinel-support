package com.winxuan.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.jdbc.JdbcDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JdbcDataSource for winxuan project
 *
 * <p>
 *  init by JdbcTemplate and appName
 * </p>
 *
 * <p>
 * custorm desgin tables<br/>
 *
 * one table for app: sentinel_app<br/>
 * three tables for rules: sentinel_flow_rule, sentinel_degrade_rule, sentinel_system_rule<br/>
 * </p>
 *
 * @author cdfive
 * @date 2018-09-04
 */
public class WinxuanJdbcDataSource<T> extends JdbcDataSource<T> {

    /**sql: find app_id by appName, only enabled and not deleted*/
    private static final String FIND_APP_ID_SQL = "SELECT id FROM sentinel_app WHERE name=? AND enabled=1 AND deleted=0";
    /**sql: find rule list by app_id, only enabled and not deleted*/
    private static final String READ_SOURCE_SQL = "SELECT * FROM %s WHERE app_id=? AND enabled=1 AND deleted=0";

    /**rule type constant*/
    public static final String RULE_TYPE_FLOW = "flow";
    public static final String RULE_TYPE_DEGRADE = "degrade";
    public static final String RULE_TYPE_SYSTEM = "system";

    /**
     * key:rule type
     * value:rule table name
     */
    private static final Map<String, String> RULE_TYPE_TABLE_NAME_MAP = new HashMap<String, String>() {{
        put(RULE_TYPE_FLOW, "sentinel_flow_rule");
        put(RULE_TYPE_DEGRADE, "sentinel_degrade_rule");
        put(RULE_TYPE_SYSTEM, "sentinel_system_rule");
    }};

    /**app name*/
    private String appName;

    /**app id*/
    private Integer appId;

    /**rule type*/
    private String ruleType;

    /**Spring JdbcTemplate for execute sql query from db*/
    private JdbcTemplate jdbcTemplate;

    public WinxuanJdbcDataSource(JdbcTemplate jdbcTemplate, String appName, Converter<List<Map<String, Object>>, T> converter) {
        this(jdbcTemplate, appName, converter, DEFAULT_RULE_REFRESH_SEC);
    }

    public WinxuanJdbcDataSource(JdbcTemplate jdbcTemplate, String appName, Converter<List<Map<String, Object>>, T> converter, Long ruleRefreshSec) {
        super(jdbcTemplate.getDataSource(), converter, ruleRefreshSec);

        this.jdbcTemplate = jdbcTemplate;
        this.appName = appName;

        initAppId();

        this.ruleType = getRuleTypeByConverter(converter);

        Assert.isTrue(RULE_TYPE_TABLE_NAME_MAP.containsKey(ruleType), "ruleType invalid, must be flow|degrade|system");
        String ruleTableName = RULE_TYPE_TABLE_NAME_MAP.get(ruleType);

        setSql(String.format(READ_SOURCE_SQL, ruleTableName));

        setSqlParameters(new Object[]{ this.appId });

        firstLoad();
    }

    /**
     * query app_id from db by appName
     */
    private void initAppId() {
        Integer appId = jdbcTemplate.query(FIND_APP_ID_SQL, new ResultSetExtractor<Integer>() {
            @Override
            public Integer extractData(ResultSet resultSet) throws SQLException, DataAccessException {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
                return null;
            }
        }, appName);

        Assert.notNull(appId, "can't find appId, appName=" + appName);

        this.appId = appId;
    }

    /**
     * get ruleType by configParse's class
     * @param converter
     * @return
     */
    private String getRuleTypeByConverter(Converter<List<Map<String, Object>>, T> converter) {
        if (converter instanceof JdbcDataSource.JdbcFlowRuleConverter) {
            return RULE_TYPE_FLOW;
        }

        if (converter instanceof JdbcDataSource.JdbcDegradeRuleConverter) {
            return RULE_TYPE_DEGRADE;
        }

        if (converter instanceof JdbcDataSource.JdbcSystemRuleConverter) {
            return RULE_TYPE_SYSTEM;
        }

        throw new IllegalArgumentException("converter invalid");
    }
}
