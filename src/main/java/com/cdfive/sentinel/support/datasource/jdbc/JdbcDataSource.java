package com.cdfive.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.datasource.AutoRefreshDataSource;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * jdbc implement of DataSource
 *
 * @see com.alibaba.csp.sentinel.datasource.ReadableDataSource
 * @see com.alibaba.csp.sentinel.datasource.AutoRefreshDataSource
 *
 * <p>
 *  This class using javax.sql.DataSource dbDatasource, String sql, Object[] sqlParameters
 *  to query <b>effective</b> sentinel rules from databse, and convert the List<Map<String, Object>>
 *  to sentinel rule objects.
 *
 *  Extends on AutoRefreshDataSource<S, T> only dependency on sentinel-datasource-extension.
 *  use Class and JDBC API in java.sql and javax.sql package, so has no other dependencies.
 *
 *  Users are free to choose their own jdbc databse, desgin tables for storage, choose different ORM framework like Spring JDBC, MyBatis and so on.
 *  Only provide a standard javaxDataSource, and the query rule sql and sql parameters.
 * </p>
 *
 * <p>
 *  Usage:
 *
 *    1.Using JdbcDataSource
 *    javax.sql.DataSource dbDataSource = new DruidDataSource() // or get @Bean from Spring container
 *    Long ruleRefreshSec = 5L;// default 30 seconds
 *    String sql = "select * from my_flow_rule_table where enable=?";
 *    Object[] sqlPara
 *    DataSource<List<Map<String, Object>>, List<FlowRule>> flowRuleDataSource = new JdbcDataSource(sentinelJdbcTemplate(), new JdbcDataSource.JdbcFlowRuleConverter(), ruleRefreshSec);
 *
 *    2.Extends from JdbcDataSource
 *    design own construct logic, eg: init by appName
 *    design own tables for store the sentinel rule datas
 *
 * </p>
 * @author cdfive
 * @date 2018-09-01
 */
@Deprecated // @see AbstractJdbcDataSource,FlowJdbcDataSource,DegradeJdbcDataSource,SystemJdbcDataSource
public class JdbcDataSource<T> extends AutoRefreshDataSource<List<Map<String, Object>>, T> {

    /**
     * the default time interval to refresh sentinel rules, 30 seconds
     *
     * <p>
     * pull mode, pull data by sql query from database
     * </p>
     *
     * <p>
     * for not query database frequently, the default is 30s<br />
     * for short numbers, use second instead of millisecond
     * </p>
     */
    protected static final Long DEFAULT_RULE_REFRESH_SEC = 30L;

    /**
     * javax.sql.DataSource Object, which related to user's database
     */
    private DataSource dbDataSource;

    /**
     * sql which query <b>effective</b> sentinel rules from databse
     */
    private String sql;

    /**
     * sql paramters
     */
    private Object[] sqlParameters;


    /**
     * constructor
     * for extends use
     * @param dbDataSource javax.sql.DataSource Object, which related to user's database
     * @param converter rule converter
     */
    public JdbcDataSource(DataSource dbDataSource, Converter<List<Map<String, Object>>, T> converter) {
        this(dbDataSource, converter, DEFAULT_RULE_REFRESH_SEC);
    }

    /**
     * constructor
     * for extends use
     * @param dbDataSource javax.sql.DataSource Object, which related to user's database
     * @param converter rule converter
     * @param ruleRefreshSec the time interval to refresh sentinel rules, in second
     */
    public JdbcDataSource(DataSource dbDataSource, Converter<List<Map<String, Object>>, T> converter, Long ruleRefreshSec) {
        super(converter, ruleRefreshSec * 1000);

        this.dbDataSource = dbDataSource;
    }

    /**
     * constructor
     * @param dbDataSource javax.sql.DataSource Object, which related to user's database
     * @param sql sql which query <b>effective</b> sentinel rules from databse
     * @param converter rule converter
     */
    public JdbcDataSource(DataSource dbDataSource, String sql, Converter<List<Map<String, Object>>, T> converter) {
        this(dbDataSource, sql, null, converter, DEFAULT_RULE_REFRESH_SEC);
    }

    /**
     * constructor
     * @param dbDataSource javax.sql.DataSource Object, which related to user's database
     * @param sql sql which query <b>effective</b> sentinel rules from databse
     * @param sqlParameters sql parameters
     * @param converter rule converter
     */
    public JdbcDataSource(DataSource dbDataSource, String sql, Object[] sqlParameters, Converter<List<Map<String, Object>>, T> converter) {
        this(dbDataSource, sql, sqlParameters, converter, DEFAULT_RULE_REFRESH_SEC);
    }

    /**
     * constructor
     * @param dbDataSource javax.sql.DataSource Object, which related to user's database
     * @param sql sql which query <b>effective</b> sentinel rules from databse
     * @param sqlParameters sql parameters
     * @param converter rule converter
     * @param ruleRefreshSec the time interval to refresh sentinel rules, in second
     */
    public JdbcDataSource(DataSource dbDataSource, String sql, Object[] sqlParameters, Converter<List<Map<String, Object>>, T> converter, Long ruleRefreshSec) {
        super(converter, ruleRefreshSec * 1000);

        checkNotNull(dbDataSource, "javax.sql.DataSource dbDataSource can't be null");
        checkNotEmpty(sql, "sql can't be null or empty");

        this.dbDataSource = dbDataSource;
        this.sql = sql;
        this.sqlParameters = sqlParameters;

        firstLoad();
    }

    /**
     * load the data from source firstly
     */
    protected void firstLoad() {
        try {
            T newValue = loadConfig();
            getProperty().updateValue(newValue);
        } catch (Exception e) {
            RecordLog.warn("loadConfig exception", e);// need to write to app log?
        }
    }

    /**
     * query sentinel rules from db by app_id
     */
    @Override
    public List<Map<String, Object>> readSource() throws Exception {
        List<Map<String, Object>> list = findListMapBySql();
        return list;
    }

    /**
     * query sql from databse, a standard javaxDataSource, and the query rule sql and sql parameters
     *
     * <P>
     *  Note:
     *  Map's key is the column name of select sql, if has alias name grammer, alias name prefered
     *
     *  eg: select grade,limit_app as limitApp,... from flow_rule_table
     *
     *  the keys are grade,limitApp
     * </P>
     * @return List<Map<String, Object>>
     */
    private List<Map<String, Object>> findListMapBySql() {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        List<Map<String, Object>> list;
        try {
            connection = dbDataSource.getConnection();
            preparedStatement = connection.prepareStatement(sql);
            if (sqlParameters != null) {
                for (int i = 0; i < sqlParameters.length; i++) {
                    preparedStatement.setObject(i + 1, sqlParameters[i]);
                }
            }

            list = new ArrayList<Map<String, Object>>();
            resultSet = preparedStatement.executeQuery();
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> map = new HashMap<String, Object>();
                list.add(map);
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = resultSetMetaData.getColumnLabel(i);// get column alias name as key
                    if (columnName == null || columnName.isEmpty()) {
                        columnName = resultSetMetaData.getColumnName(i);// get column name as key
                    }
                    map.put(columnName, resultSet.getObject(i));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLException", e);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    throw new RuntimeException("SQLException=>resultSet.close()", e);
                } finally {
                    if (preparedStatement != null) {
                        try {
                            preparedStatement.close();
                        } catch (SQLException e) {
                            throw new RuntimeException("SQLException=>preparedStatement.close()", e);
                        } finally {
                            if (connection != null) {
                                try {
                                    connection.close();
                                } catch (SQLException e) {
                                    throw new RuntimeException("SQLException=>connection.close()", e);
                                }
                            }
                        }
                    }
                }
            }
        }

        return list;
    }

    /**
     * check null object
     */
    private void checkNotNull(Object obj, String msg) {
        if (obj == null) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * check empty string
     */
    private void checkNotEmpty(String str, String msg) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**getters and setters*/
    public DataSource getDbDataSource() {
        return dbDataSource;
    }

    public void setDbDataSource(DataSource dbDataSource) {
        this.dbDataSource = dbDataSource;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Object[] getSqlParameters() {
        return sqlParameters;
    }

    public void setSqlParameters(Object[] sqlParameters) {
        this.sqlParameters = sqlParameters;
    }
}
