package se.alipsa.gade.environment.connections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.Constants;
import se.alipsa.groovy.datautil.ConnectionInfo;

/**
 * Test suite for ConnectionHandler covering business logic that can be tested
 * without requiring the full Gade infrastructure (Gradle dependency resolution,
 * dynamic classloaders, JavaFX, etc.).
 *
 * Tested functionality:
 * - Connection type detection (JDBC vs BigQuery)
 * - URL parsing and login detection
 * - ConnectionInfo accessor methods
 * - Driver type validation
 *
 * NOT tested (requires full Gade infrastructure):
 * - Actual database connections (connect() method)
 * - Query execution (requires live database connection)
 * - Metadata fetching (requires live database connection)
 * - Connection pooling (requires connection manager setup)
 *
 * These untested areas are covered by integration tests and manual testing,
 * as they require a fully initialized Gade environment with dependency resolution.
 */
class ConnectionHandlerTest {

  // ========== Connection Type Detection Tests ==========

  @Test
  void testJdbcConnectionTypeDetection() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    assertTrue(handler.isJdbc(), "Should detect JDBC connection type");
    assertFalse(handler.isBigQuery(), "Should not be BigQuery type");
    assertEquals(ConnectionType.JDBC, handler.getConnectionType());
  }

  @Test
  void testBigQueryConnectionTypeDetection() {
    ConnectionInfo ci = new ConnectionInfo();
    ci.setName("test-bq");
    ci.setDriver("com.google.cloud.bigquery.BigQuery");
    ci.setUrl("bq://myproject");
    ci.setDependency("com.google.cloud:google-cloud-bigquery:2.10.0");

    ConnectionHandler handler = new ConnectionHandler(ci);

    assertTrue(handler.isBigQuery(), "Should detect BigQuery connection type");
    assertFalse(handler.isJdbc(), "Should not be JDBC type");
    assertEquals(ConnectionType.BIGQUERY, handler.getConnectionType());
  }

  @Test
  void testConnectionTypeBasedOnUrl() {
    // JDBC URL patterns
    String[] jdbcUrls = {
        "jdbc:h2:mem:testdb",
        "jdbc:postgresql://localhost/db",
        "jdbc:mysql://localhost:3306/db",
        "jdbc:oracle:thin:@localhost:1521:xe",
        "jdbc:sqlserver://localhost;database=testdb",
        "jdbc:sqlite:test.db"
    };

    for (String url : jdbcUrls) {
      ConnectionInfo ci = new ConnectionInfo();
      ci.setUrl(url);
      ci.setDriver("some.jdbc.Driver");
      ConnectionHandler handler = new ConnectionHandler(ci);
      assertTrue(handler.isJdbc(),
          "URL '" + url + "' should be detected as JDBC");
    }

    // BigQuery URL patterns - need to set driver before URL
    String[] bqUrls = {
        "bq://myproject",
        "bq://my-gcp-project",
        "bigquery://project123"
    };

    for (String url : bqUrls) {
      ConnectionInfo ci = new ConnectionInfo();
      ci.setDriver("com.google.cloud.bigquery.BigQuery");  // Set driver first
      ci.setUrl(url);
      ConnectionHandler handler = new ConnectionHandler(ci);
      assertTrue(handler.isBigQuery(),
          "URL '" + url + "' should be detected as BigQuery");
    }
  }

  // ========== URL Parsing Tests ==========

  @Test
  void testUrlContainsLoginDetection() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    // URLs without embedded credentials
    assertFalse(handler.urlContainsLogin("jdbc:h2:mem:testdb"),
        "Simple H2 URL should not contain login");
    assertFalse(handler.urlContainsLogin("jdbc:postgresql://localhost/db"),
        "PostgreSQL URL without credentials should not contain login");

    // URLs with embedded credentials
    assertTrue(handler.urlContainsLogin("jdbc:postgresql://host/db?user=john&password=secret"),
        "URL with user and password params should be detected");
    assertTrue(handler.urlContainsLogin("jdbc:mysql://user:pass@localhost/db"),
        "URL with user:pass@ format should be detected");
    assertTrue(handler.urlContainsLogin("jdbc:sqlserver://localhost;integratedSecurity=true"),
        "URL with integrated security should be detected");

    // Oracle special case - @ is used for TNS, not credentials
    assertFalse(handler.urlContainsLogin("jdbc:oracle:thin:@localhost:1521:xe"),
        "Oracle TNS format should not be detected as containing login");
  }

  @Test
  void testUrlContainsLoginCaseInsensitive() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    // Test case insensitivity
    assertTrue(handler.urlContainsLogin("jdbc:postgresql://host/db?USER=john&PASSWORD=secret"),
        "Should detect uppercase USER and PASSWORD params");
    assertTrue(handler.urlContainsLogin("jdbc:postgresql://host/db?User=john&Pass=secret"),
        "Should detect mixed case user and pass params");
    assertTrue(handler.urlContainsLogin("jdbc:sqlserver://localhost;INTEGRATEDSECURITY=TRUE"),
        "Should detect uppercase integrated security");
    assertTrue(handler.urlContainsLogin("jdbc:mysql://User:Pass@localhost/db"),
        "Should detect credentials regardless of case in URL");
  }

  @Test
  void testUrlContainsLoginVariousFormats() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    // Test various parameter formats
    assertTrue(handler.urlContainsLogin("jdbc:postgresql://host/db?user=john&password=secret"),
        "Should detect user and password parameters");
    assertTrue(handler.urlContainsLogin("jdbc:mysql://john:secret@localhost:3306/db"),
        "Should detect user:password before host");

    // Edge cases
    assertFalse(handler.urlContainsLogin("jdbc:h2:file:./data/userdb"),
        "Should not detect 'user' in file path as credentials");
  }

  // ========== ConnectionInfo Accessor Tests ==========

  @Test
  void testConnectionInfoAccessors() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    assertNotNull(handler.getConnectionInfo(), "getConnectionInfo should not return null");
    assertEquals(ci, handler.getConnectionInfo(), "Should return same ConnectionInfo instance");
    assertEquals("test-h2", handler.getConnectionInfo().getName());
    assertEquals(Constants.Driver.H2.getDriverClass(), handler.getConnectionInfo().getDriver());
    assertEquals("jdbc:h2:mem:testdb", handler.getConnectionInfo().getUrl());
  }

  @Test
  void testConnectionInfoModification() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    // Modify the original ConnectionInfo
    ci.setName("modified-name");
    ci.setUser("modified-user");

    // Handler should reflect the changes (same instance)
    assertEquals("modified-name", handler.getConnectionInfo().getName());
    assertEquals("modified-user", handler.getConnectionInfo().getUser());
  }

  // ========== Driver Type Tests ==========

  @Test
  void testSupportedDriverTypes() {
    // Test all supported JDBC driver types
    ConnectionInfo[] drivers = {
        createDriverInfo("PostgreSQL", Constants.Driver.POSTGRES),
        createDriverInfo("MySQL", Constants.Driver.MYSQL),
        createDriverInfo("MariaDB", Constants.Driver.MARIADB),
        createDriverInfo("H2", Constants.Driver.H2),
        createDriverInfo("SQLServer", Constants.Driver.SQLSERVER),
        createDriverInfo("SQLite", Constants.Driver.SQLLITE),
        createDriverInfo("Firebird", Constants.Driver.FIREBIRD),
        createDriverInfo("Derby", Constants.Driver.DERBY),
        createDriverInfo("Oracle", Constants.Driver.ORACLE),
        createDriverInfo("JParq", Constants.Driver.JPARQ)
    };

    for (ConnectionInfo ci : drivers) {
      ConnectionHandler handler = new ConnectionHandler(ci);
      assertTrue(handler.isJdbc(),
          ci.getName() + " should be detected as JDBC type");
      assertFalse(handler.isBigQuery(),
          ci.getName() + " should not be BigQuery type");
    }
  }

  @Test
  void testGetConnectionType() {
    // Test JDBC
    ConnectionInfo jdbcCi = createH2ConnectionInfo();
    ConnectionHandler jdbcHandler = new ConnectionHandler(jdbcCi);
    assertEquals(ConnectionType.JDBC, jdbcHandler.getConnectionType());

    // Test BigQuery - set driver before URL
    ConnectionInfo bqCi = new ConnectionInfo();
    bqCi.setDriver("com.google.cloud.bigquery.BigQuery");
    bqCi.setUrl("bq://project");
    ConnectionHandler bqHandler = new ConnectionHandler(bqCi);
    assertEquals(ConnectionType.BIGQUERY, bqHandler.getConnectionType());
  }

  // ========== Edge Cases and Validation ==========

  @Test
  void testConnectionWithMinimalInfo() {
    // Create ConnectionInfo with just a driver set
    ConnectionInfo ci = new ConnectionInfo();
    ci.setDriver(Constants.Driver.H2.getDriverClass());
    ci.setUrl("jdbc:h2:mem:test");

    ConnectionHandler handler = new ConnectionHandler(ci);
    assertNotNull(handler.getConnectionType());
    assertTrue(handler.isJdbc());
  }

  @Test
  void testUrlWithSpecialCharacters() {
    ConnectionInfo ci = createH2ConnectionInfo();
    ConnectionHandler handler = new ConnectionHandler(ci);

    // Test URLs with special characters
    assertTrue(handler.urlContainsLogin("jdbc:mysql://user:p@ss!@localhost/db"),
        "Should handle special characters in password");
    assertTrue(handler.urlContainsLogin("jdbc:postgresql://host/db?user=john&password=p@ss%20word"),
        "Should handle URL-encoded passwords");
  }

  // ========== Helper Methods ==========

  private ConnectionInfo createH2ConnectionInfo() {
    ConnectionInfo ci = new ConnectionInfo();
    ci.setName("test-h2");
    ci.setDriver(Constants.Driver.H2.getDriverClass());
    ci.setUrl("jdbc:h2:mem:testdb");
    ci.setUser("sa");
    ci.setPassword("");
    ci.setDependency(Constants.Driver.H2.getDependency());
    return ci;
  }

  private ConnectionInfo createDriverInfo(String name, Constants.Driver driver) {
    ConnectionInfo ci = new ConnectionInfo();
    ci.setName(name);
    ci.setDriver(driver.getDriverClass());
    ci.setUrl("jdbc:" + name.toLowerCase() + "://localhost/test");
    ci.setDependency(driver.getDependency());
    return ci;
  }
}
