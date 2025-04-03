package se.alipsa.gade.environment.connections;

import com.google.cloud.resourcemanager.v3.Project;
import groovy.lang.GroovyClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.Constants;
import se.alipsa.gade.Gade;
import se.alipsa.groovy.resolver.Dependency;
import se.alipsa.gade.utils.Alerts;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.JdbcUrlParser;
import se.alipsa.gade.utils.gradle.GradleUtils;
import se.alipsa.groovy.datautil.ConnectionInfo;
import se.alipsa.matrix.bigquery.Bq;
import se.alipsa.matrix.bigquery.BqException;
import se.alipsa.matrix.core.Matrix;

public class ConnectionHandler {

  private static final Logger log = LogManager.getLogger(ConnectionHandler.class);

  ConnectionInfo connectionInfo;
  ConnectionType connectionType;

  public boolean verifyConnection() {
    return switch (connectionType) {
      case JDBC -> verifyJdbcConnection();
      case BIGQUERY -> verifyBigQueryConnection();
    };
  }

  private boolean verifyBigQueryConnection() {
    try {
      Bq bq = new Bq(connectionInfo.getUrl());
      bq.getDatasets();
      return true;
    } catch (BqException e) {
      log.info("Exception during connection verification", e);
      return false;
    }
  }

  private boolean verifyJdbcConnection() {
    try (Connection con = connect()) {
      return con != null;
    } catch (SQLException ex) {
      Exception exceptionToShow = ex;
      try {
        JdbcUrlParser.validate(connectionInfo.getDriver(), connectionInfo.getUrl());
      } catch (MalformedURLException exc) {
        exceptionToShow = exc;
      }
      ExceptionAlert.showAlert("Failed to connect to database: " + exceptionToShow, ex);
      if (exceptionToShow.getMessage().contains("authentication")) {
        log.warn("Connection attempted to '{}' using userName '{}', and password '{}'",
            connectionInfo.getName(), connectionInfo.getUser(), connectionInfo.getPassword());
      }
      return false;
    }
  }

  public Matrix getConnectionMetadata() throws ConnectionException {
    if (connectionType == ConnectionType.JDBC) {
      return getJdbcConnectionMetadata();
    } else {
      return getBigQueryMetaData();
    }
  }

  private String getBigQueryDatasetMetaData(String datasetName) {
    return """
    select col.TABLE_NAME
     , TABLE_TYPE
     , COLUMN_NAME
     , ORDINAL_POSITION
     , IS_NULLABLE
     , DATA_TYPE
     , 0 as CHARACTER_MAXIMUM_LENGTH
     , 0 as NUMERIC_PRECISION
     , 0 as NUMERIC_SCALE
     , COLLATION_NAME
     , tab.TABLE_SCHEMA
     from `${datasetName}`.INFORMATION_SCHEMA.COLUMNS col
     inner join `${datasetName}`.INFORMATION_SCHEMA.TABLES tab
           on col.TABLE_NAME = tab.TABLE_NAME and col.TABLE_SCHEMA = tab.TABLE_SCHEMA
     where TABLE_TYPE <> 'SYSTEM TABLE'
    """.replace("`${datasetName}`", datasetName);
  }
  private Matrix getBigQueryMetaData() throws ConnectionException {
    try {
      Bq bq = new Bq(connectionInfo.getUrl());
      List<String> sqlList = new ArrayList<>();
      for (String datasetName : bq.getDatasets()) {
        sqlList.add(getBigQueryDatasetMetaData(datasetName));
      }
      String sql = String.join(" UNION ALL ", sqlList);
      return query(sql);
    } catch (BqException e) {
      throw new ConnectionException("Failed to get big query metadata", e);
    }
  }

  private Matrix getJdbcConnectionMetadata() throws ConnectionException {
    String sql;
    if (connectionInfo.getDriver().equals(Constants.Driver.SQLLITE.getDriverClass())) {
      boolean hasTables = false;
      try {
        Connection jdbcCon = connect();
        if (jdbcCon == null) {
          throw new ConnectionFailedException("Failed to establish a connection to SQLite");
        }
        ResultSet rs = jdbcCon.createStatement().executeQuery("select * from sqlite_master");
        if (rs.next()) hasTables = true;
        jdbcCon.close();
      } catch (SQLException e) {
        ExceptionAlert.showAlert("Failed to query sqlite_master", e);
      }
      if (hasTables) {
        sql = """
           SELECT
           m.name as TABLE_NAME
           , m.type as TABLE_TYPE
           , p.name as COLUMN_NAME
           , p.cid as ORDINAL_POSITION
           , case when p.[notnull] = 0 then 1 else 0 end as IS_NULLABLE
           , p.type as DATA_TYPE
           , 0 as CHARACTER_MAXIMUM_LENGTH
           , 0 as NUMERIC_PRECISION
           , 0 as NUMERIC_SCALE
           , '' as COLLATION_NAME
           , TABLE_SCHEMA
           FROM
             sqlite_master AS m
           JOIN
             pragma_table_info(m.name) AS p
           """;
      } else {
        throw new ConnectionException("This sqlite database has no tables yet");
      }
    } else {
      sql = """
         select col.TABLE_NAME
         , TABLE_TYPE
         , COLUMN_NAME
         , ORDINAL_POSITION
         , IS_NULLABLE
         , DATA_TYPE
         , CHARACTER_MAXIMUM_LENGTH
         , NUMERIC_PRECISION
         , NUMERIC_SCALE
         , COLLATION_NAME
         , tab.TABLE_SCHEMA
         from INFORMATION_SCHEMA.COLUMNS col
         inner join INFORMATION_SCHEMA.TABLES tab
               on col.TABLE_NAME = tab.TABLE_NAME and col.TABLE_SCHEMA = tab.TABLE_SCHEMA
         where TABLE_TYPE <> 'SYSTEM TABLE'
         and tab.TABLE_SCHEMA not in ('SYSTEM TABLE', 'PG_CATALOG', 'INFORMATION_SCHEMA', 'pg_catalog', 'information_schema')
         """;
    }
    return query(sql);
  }

  public List<String> getDatabases() throws ConnectionException {
    try {
      if (connectionType == ConnectionType.JDBC) {
        return listDatabaseJdbc();
      } else {
        Bq bq = new Bq(connectionInfo.getUrl());
        return bq.getProjects().stream().map(Project::getName).toList();
      }
    } catch (BqException e) {
      throw new ConnectionException("Failed to get list of databases (projects)", e);
    }
  }

  public Matrix query(String sql, int... limit) throws ConnectionException {
    if (connectionType == ConnectionType.JDBC) {
      return queryJdbc(sql, limit);
    } else if (connectionType == ConnectionType.BIGQUERY) {
      return queryBq(sql, limit);
    }
    log.error("Connection type not matching anything known");
    return null;
  }

  private Matrix queryBq(String sql, int... limit) throws ConnectionException {
    try {
      Bq bq = new Bq(connectionInfo.getUrl());
      if (limit.length > 0) {
        if (sql.trim().endsWith(";")) {
          sql = sql.substring(0, sql.length() - 1);
        }
        return bq.query(sql + " limit " + limit[0] + ";");
      } else {
        return bq.query(sql);
      }
    } catch (BqException e) {
      throw new ConnectionException("Failed to query", e);
    }
  }

  public ConnectionHandler(ConnectionInfo ci) {
    connectionInfo = ci;
    if (ci.getUrl().startsWith("jdbc:")) {
      connectionType = ConnectionType.JDBC;
    } else {
      connectionType = ConnectionType.BIGQUERY;
    }
  }

  Matrix queryJdbc(String sql, int... limitRows) throws ConnectionException {
    try (Connection connection = connect()){
      if (connection == null) {
        throw new ConnectionFailedException( "Failed to establish a connection to the database");
      }
      try (Statement stm=connection.createStatement()) {
        if (limitRows.length > 0) {
          stm.setMaxRows(limitRows[0]);
        }
        try (ResultSet rs = stm.executeQuery(sql)) {
          rs.setFetchSize(200);
          return Matrix.builder().data(rs).build();
        }
      }
    } catch (SQLException e) {
      throw new ConnectionException(e.getMessage(), e);
    }
  }

  @SuppressWarnings("unchecked")
  public Connection connect() throws SQLException {
    var ci = connectionInfo;
    log.info("Connecting to {} using {}", ci.getUrl(), ci.getDependency());
    //if (!ci.getUrl().contains("password")) {
    //  ci.setPassword(passwordField.getText());
    //}
    var gui = Gade.instance();
    Driver driver;

    try {
      Dependency dep = new Dependency(ci.getDependency());
      log.info("Resolving dependency {}", ci.getDependency());
      File jar = GradleUtils.downloadArtifact(dep);
      URL url = jar.toURI().toURL();
      URL[] urls = new URL[]{url};
      log.info("Dependency url is {}", urls[0]);
      if (gui.dynamicClassLoader == null) {
        ClassLoader cl;
        cl = gui.getConsoleComponent().getClassLoader();
        gui.dynamicClassLoader = new GroovyClassLoader(cl);
      }

      if (Arrays.stream(gui.dynamicClassLoader.getURLs()).noneMatch(p -> p.equals(url))) {
        gui.dynamicClassLoader.addURL(url);
      }

    } catch (IOException | URISyntaxException e) {
      Platform.runLater(() ->
          ExceptionAlert.showAlert(ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(), e)
      );
      return null;
    }


    try {
      log.info("Attempting to load the class {}", ci.getDriver());
      Class<Driver> clazz = (Class<Driver>) gui.dynamicClassLoader.loadClass(ci.getDriver());
      log.info("Loaded driver from session classloader, instating the driver {}", ci.getDriver());
      try {
        driver = clazz.getDeclaredConstructor().newInstance();
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
        log.error("Failed to instantiate the driver: {}, clazz is {}", ci.getDriver(), clazz, e);
        Platform.runLater(() ->
            Alerts.showAlert("Failed to instantiate the driver",
                ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(),
                Alert.AlertType.ERROR)
        );
        return null;
      }
    } catch (ClassCastException | ClassNotFoundException e) {
      Platform.runLater(() ->
          Alerts.showAlert("Failed to load driver",
              ci.getDriver() + " could not be loaded from dependency " + ci.getDependency(),
              Alert.AlertType.ERROR)
      );
      return null;
    }
    Properties props = new Properties();
    if ( urlContainsLogin(ci.getUrlSafe()) ) {
      log.info("Skipping specified user/password since it is part of the url");
    } else {
      if (ci.getUser() != null) {
        props.put("user", ci.getUser());
        if (ci.getPassword() != null) {
          props.put("password", ci.getPassword());
        }
      }
    }
    gui.setNormalCursor();
    return driver.connect(ci.getUrl(), props);
  }

  public boolean urlContainsLogin(String url) {
    String safeLcUrl = url.toLowerCase();
    return ( safeLcUrl.contains("user") && safeLcUrl.contains("pass") ) || safeLcUrl.contains("@");
  }

  private List<String> listDatabaseJdbc() throws ConnectionException {
    try(Connection con = connect()) {
      if (con == null) {
        throw new ConnectionFailedException("Failed to establish a connection to the database");
      }
      DatabaseMetaData meta = con.getMetaData();
      ResultSet res = meta.getCatalogs();
      List<String> dbList = new ArrayList<>();
      while (res.next()) {
        dbList.add(res.getString("TABLE_CAT"));
      }
      res.close();
      return dbList;
    } catch (SQLException e) {
      throw new ConnectionException("Failed to list databases", e);
    }
  }

  public boolean isJdbc() {
    return connectionType == ConnectionType.JDBC;
  }

  public boolean isBigQuery() {
    return connectionType == ConnectionType.BIGQUERY;
  }

  public ConnectionInfo getConnectionInfo() {
    return connectionInfo;
  }

  public ConnectionType getConnectionType() {
    return connectionType;
  }
}
