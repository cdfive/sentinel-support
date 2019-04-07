package com.cdfive.sentinel.support.datasource.jdbc;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.WritableDataSource;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.cdfive.sentinel.support.SentinelSupportConstant;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * abstract JdbcDataSource
 * @author cdfive
 */
@Slf4j
public abstract class AbstractJdbcDataSource<T> implements ReadableDataSource<List<Map<String, Object>>, T>, WritableDataSource<T> {

    /**
     * sql: find app_id by appName,ip,port, only enabled and not deleted
     */
    private static final String FIND_APP_ID_SQL = "SELECT id FROM sentinel_app WHERE _name=? AND ip=? AND _port=? AND enabled=1 AND deleted=0";
    /**
     * sql: find rule list by app_id, only enabled and not deleted
     */
    private static final String READ_RULE_SQL = "SELECT * FROM %s WHERE app_id=? AND enabled=1 AND deleted=0";
    /**
     * sql: delete rule list by app_id
     */
    private static final String DELETE_RULE_SQL = "DELETE FROM %s WHERE app_id=?";


    /**for getProperty() and update value, use DynamicSentinelProperty as implement*/
    @Getter
    @Setter
    private SentinelProperty<T> property;

    /**a standard javax.sql.DataSource*/
    @Getter
    @Setter
    private DataSource dbDataSource;

    /**app id, a application has a unique app id*/
    @Getter
    @Setter
    private Integer appId;

    /**app name, the application name*/
    @Getter
    @Setter
    private String appName;

    /**the ip of server which the app deployed*/
    @Getter
    @Setter
    private String ip;

    /**the port the app used*/
    @Getter
    @Setter
    private Integer port;

    /**the rule table which store the rule's value, init value in subclass @see,initRuleTableName*/
    @Getter
    @Setter
    private String ruleTableName;

    /**the interval in second which is used for refresh the rules, if null needn't refresh*/
    @Getter
    @Setter
    private Long refreshSec;

    /**the refresh backgound thread service*/
    @Getter
    @Setter
    private ScheduledExecutorService refreshService;


    /**
     * constructor
     * @param dbDataSource a standard javax.sql.DataSource
     * @param appName application name
     * @param ip the ip of server which the app deployed
     * @param port the port the app used
     */
    public AbstractJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port)  {
        this(dbDataSource, null, appName, ip, port);
    }

    /**
     * constructor
     * @param dbDataSource a standard javax.sql.DataSource
     * @param appName application name
     * @param ip the ip of server which the app deployed
     * @param port the port the app used
     * @param refreshSec the interval in second which is used for refresh the rules, if null needn't refresh
     */
    public AbstractJdbcDataSource(DataSource dbDataSource, String appName, String ip, Integer port, Long refreshSec)  {
        this(dbDataSource, null, appName, ip, port, refreshSec);
    }

    /**
     * constructor
     * @param dbDataSource a standard javax.sql.DataSource
     * @param appId application id
     * @param appName application name
     * @param ip the ip of server which the app deployed
     * @param port the port the app used
     *
     * if appId is null, query from database by appName,ip and prot
     */
    public AbstractJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port)  {
        this(dbDataSource, appId, appName, ip, port, null);
    }

    /**
     * constructor
     * @param dbDataSource a standard javax.sql.DataSource
     * @param appId application id
     * @param appName application name
     * @param ip the ip of server which the app deployed
     * @param port the port the app used
     * @param refreshSec the interval in second which is used for refresh the rules, if null needn't refresh
     *
     * if appId is null, query from database by appName,ip and prot
     */
    public AbstractJdbcDataSource(DataSource dbDataSource, Integer appId, String appName, String ip, Integer port, Long refreshSec) {
        checkNotNull(dbDataSource, "javax.sql.DataSource dbDataSource can't be null");
        checkNotEmpty(appName, "appName can't be null or empty");

        this.dbDataSource = dbDataSource;
        this.appName = appName;
        this.ip = ip;
        this.port = port;
        this.refreshSec = refreshSec;

        if (refreshSec != null) {
            check(refreshSec > 0, "refreshSec must > 0");
        }

        this.property = new DynamicSentinelProperty<T>();

        this.ruleTableName = initRuleTableName();
        checkNotEmpty(ruleTableName, "ruleTableName can't be null or empty");

        if (appId == null) {
            if (!initAppId()) {
                return;
            }
        } else {
            this.appId = appId;
        }

        loadData();

        if (refreshSec != null) {
            startRefreshService();
        }
    }

    /**
     * query app id from db by appName,ip,port
     * @return true-init success false-init failed
     */
    private boolean initAppId() {
        Object object = findObjectBySql(FIND_APP_ID_SQL, new Object[]{appName, ip, port});

        if (object == null) {
            log.error("can't find appId,appName=" + appName + ",ip=" + ip + ",port=" + port);
            return false;
        }

        this.appId = Integer.parseInt(object.toString());

        log.info(SentinelSupportConstant.LOG_PRIFEX + this.getClass().getSimpleName() + " initAppId, appId=" + appId);
        return true;
    }

    /**
     * load data and update property value
     */
    private void loadData() {
        try {
            T t = loadConfig();
            getProperty().updateValue(t);
        } catch (Exception e) {
            log.error(SentinelSupportConstant.LOG_PRIFEX + "loadData exception", e);
            RecordLog.warn("loadData exception", e);
        }
    }

    /**
     * start background thread to refresh
     */
    private void startRefreshService() {
        refreshService = Executors.newScheduledThreadPool(1,
                new NamedThreadFactory("sentinel-datasource-auto-refresh-task", true));
        refreshService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                loadData();
            }
        }, refreshSec, refreshSec, TimeUnit.SECONDS);
    }

    @Override
    public T loadConfig() throws Exception {
        List<Map<String, Object>> list = readSource();
        return convert(list);
    }

    @Override
    public List<Map<String, Object>> readSource() throws Exception {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> list = findListMapBySql(String.format(READ_RULE_SQL, ruleTableName), new Object[]{appId});
        int count = list != null ? 0 : list.size();
        log.info(SentinelSupportConstant.LOG_PRIFEX + "app(" + appId + ") " + count + " rules readSource cost "+ (System.currentTimeMillis() - start) / 1000.0 + "s");
        return list;
    }

    @Override
    public SentinelProperty<T> getProperty() {
        return property;
    }

    @Override
    public void write(T value) throws Exception {
        long start = System.currentTimeMillis();
        String deleteSql = String.format(DELETE_RULE_SQL, ruleTableName);
        Object[] deleteSqlParameters = new Object[]{appId};
        String insertSql = initInsertSql();
        List<Object[]> insertSqlParametersList = initInsertSqlParametersList(value);

        deleteAndInsert(deleteSql, deleteSqlParameters, insertSql, insertSqlParametersList);

        int insertCount = insertSqlParametersList == null ? 0 : insertSqlParametersList.size();
        log.info(SentinelSupportConstant.LOG_PRIFEX + "app(" + appId + ") " + insertCount
                + " rules write cost " + (System.currentTimeMillis() - start) / 1000.0 + "s");
    }

    @Override
    public void close() throws Exception {
        if (refreshService != null) {
            refreshService.shutdownNow();
        }
    }


    /**============for subClass implement start============*/
    /**rule table name*/
    abstract protected String initRuleTableName();

    /**convert List<Map<String, Object>> to List<XxxRule>*/
    abstract protected T convert(List<Map<String, Object>> list);

    /**XxxRule insert sql template*/
    abstract protected String initInsertSql();

    /**XxxRule insert sql parameters*/
    abstract protected List<Object[]> initInsertSqlParametersList(T value);
    /**============for subClass implement end============*/


    /**
     * query object with sql and parameters from database
     * @return Object
     */
    private Object findObjectBySql(String sql, Object[] sqlParameters) {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        Object object = null;
        try {
            connection = dbDataSource.getConnection();
            preparedStatement = connection.prepareStatement(sql);
            if (sqlParameters != null) {
                for (int i = 0; i < sqlParameters.length; i++) {
                    preparedStatement.setObject(i + 1, sqlParameters[i]);
                }
            }

            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                object = resultSet.getObject(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLException", e);
        } finally {
            closeJdbcObjects(resultSet, preparedStatement, connection);
        }

        return object;
    }

    /**
     * query list map with sql and parameters from database
     * <p>
     * Note:
     * Map's key is the column name of select sql, if has alias name grammer, alias name prefered
     * </P>
     * @return List<Map<String, Object>>
     */
    private List<Map<String, Object>> findListMapBySql(String sql, Object[] sqlParameters) {
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
            closeJdbcObjects(resultSet, preparedStatement, connection);
        }

        return list;
    }

    /**
     * refresh data, delete first and then insert
     */
    private void deleteAndInsert(String deleteSql, Object[] deleteSqlParameters, String insertSql, List<Object[]> insertSqlParametersList) {
        Connection connection = null;
        PreparedStatement deletePstmt = null;
        PreparedStatement insertPstmt = null;

        try {
            connection = dbDataSource.getConnection();

            deletePstmt = connection.prepareStatement(deleteSql);
            if (deleteSqlParameters != null) {
                for (int i = 0; i < deleteSqlParameters.length; i++) {
                    deletePstmt.setObject(i + 1, deleteSqlParameters[i]);
                }
            }
            deletePstmt.executeUpdate();

            if (insertSqlParametersList == null || insertSqlParametersList.size() == 0) {
                return;
            }

            insertPstmt = connection.prepareStatement(insertSql);
            if (insertSqlParametersList != null) {
                for (int i = 0; i < insertSqlParametersList.size(); i++) {
                    Object[] insertSqlParameters = insertSqlParametersList.get(i);
                    for (int j = 0; j < insertSqlParameters.length; j++) {
                        insertPstmt.setObject(j + 1, insertSqlParameters[j]);
                    }
                    insertPstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLException", e);
        } finally {
            closeJdbcObjects(insertPstmt, deletePstmt, connection);
        }
    }

    /**
     * close JDBC Objects: ResultSet,PreparedStatement,Connection...
     * @param jdbcObjects JDBC Objects: ResultSet,PreparedStatement,Connection...
     */
    private void closeJdbcObjects(Object ... jdbcObjects) {
        if (jdbcObjects == null) {
            return;
        }

        for (Object jdbcObject : jdbcObjects) {
            if (jdbcObject != null) {
                if (jdbcObject instanceof ResultSet) {
                    try {
                        ((ResultSet) jdbcObject).close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (jdbcObject instanceof Statement) {
                    try {
                        ((Statement) jdbcObject).close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (jdbcObject instanceof Connection) {
                    try {
                        ((Connection) jdbcObject).close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * check condition
     */
    private void check(boolean condition, String msg) {
        if (!condition) {
            throw new IllegalArgumentException(msg);
        }
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

    /**
     * get string value of key from map, default value is null
     */
    protected String getMapStringVal(Map<String, Object> map, String key) {
        return getMapVal(map, key, null, new ParseMapValCallback<String>() {
            @Override
            public String parseVal(String strVal) {
                return strVal;
            }
        });
    }

    /**
     * get int value of key from map, default value is 0
     */
    protected int getMapIntVal(Map<String, Object> map, String key) {
        return getMapVal(map, key, 0, new ParseMapValCallback<Integer>() {
            @Override
            public Integer parseVal(String strVal) {
                return Integer.parseInt(strVal);
            }
        });
    }

    /**
     * get long value of key from map, default value is 0L
     */
    protected long getMapLongVal(Map<String, Object> map, String key) {
        return getMapVal(map, key, 0L, new ParseMapValCallback<Long>() {
            @Override
            public Long parseVal(String strVal) {
                return Long.parseLong(strVal);
            }
        });
    }

    /**
     * get double value of key from map, default value is 0D
     */
    protected double getMapDoubleVal(Map<String, Object> map, String key) {
        return getMapVal(map, key, 0D, new ParseMapValCallback<Double>() {
            @Override
            public Double parseVal(String strVal) {
                return Double.parseDouble(strVal);
            }
        });
    }

    /**
     * get value of key from the map
     * <p>
     * if map doesn't contains key or the value of key from the map is null, then return default value
     * else use ParseMapValCallback to parse the string value to the return type T
     *
     * @param map                 map
     * @param key                 key
     * @param defVal              default value
     * @param parseMapValCallback
     * @param <T>                 the type
     * @return result of type T
     */
    protected static <T> T getMapVal(Map<String, Object> map, String key, T defVal, ParseMapValCallback<T> parseMapValCallback) {
        if (!map.containsKey(key)) {
            return defVal;
        }

        Object obj = map.get(key);
        if (obj == null) {
            return defVal;
        }

        return parseMapValCallback.parseVal(obj.toString());
    }

    /**
     * map value parse callback function
     *
     * @param <T> type
     */
    protected interface ParseMapValCallback<T> {
        /**
         * ParseMapValCallback
         *
         * @param strVal the string value
         * @return the parsed result of type T
         */
        T parseVal(String strVal);
    }
}
