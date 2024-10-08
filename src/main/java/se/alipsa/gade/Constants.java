package se.alipsa.gade;

import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import se.alipsa.gade.utils.SystemUtils;

public class Constants {
  public static final String SESSION_MUNIN_CONNECTION = "MuninConnection";
  public static final String DEFAULT_GMD_REPORT_NAME = "Gmd Report";
  public static final String DEFAULT_GROOVY_REPORT_NAME = "Groovy Report";
  public static final String PREF_MUNIN_SERVER = "MuninConnection.serverName";
  public static final String PREF_MUNIN_PORT = "MuninConnection.serverPort";
  public static final String PREF_MUNIN_USERNAME = "MuninConnection.userName";
  public static final int ICON_HEIGHT = 20;
  public static final int ICON_WIDTH = 20;

  public static final int HGAP = 5;
  public static final int VGAP = 5;
  public static final Insets FLOWPANE_INSETS = new Insets(5, 10, 5, 5);

  public static final KeyCodeCombination KEY_CODE_COPY =
          SystemUtils.getPlatform() == SystemUtils.OS.MAC ?
                  new KeyCodeCombination(KeyCode.C, KeyCombination.META_ANY)
          : new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_ANY);

  public static final String INDENT = "  ";

  public static final String THEME = "theme";
  public static final String DARK_THEME = "darkTheme.css";
  public static final String BRIGHT_THEME = "brightTheme.css";
  public static final String BLUE_THEME = "blueTheme.css";

  public enum Driver {
    POSTGRES("org.postgresql.Driver", "org.postgresql:postgresql:42.7.4"),
    MYSQL("com.mysql.jdbc.Driver", "com.mysql:mysql-connector-j:9.0.9"),
    MARIADB("org.mariadb.jdbc.Driver", "org.mariadb.jdbc:mariadb-java-client:3.4.1"),
    H2("org.h2.Driver", "com.h2database:h2:2.3.232"),
    SQLSERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", "com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11"),
    SQLLITE("org.sqlite.JDBC", "org.xerial:sqlite-jdbc:3.46.1.0"),
    FIREBIRD("org.firebirdsql.jdbc.FBDriver", "org.firebirdsql.jdbc:jaybird:5.0.5.java11"),
    DERBY("org.apache.derby.jdbc.ClientDriver", "org.apache.derby:derby:10.17.1.0"),
    ORACLE("oracle.jdbc.OracleDriver", "com.oracle.database.jdbc:ojdbc11:23.5.0.24.07"),
    NONE("", "");

    final String driverClass;
    final String dependency;

    Driver(String driverClass, String dependency) {
      this.driverClass = driverClass;
      this.dependency = dependency;
    }

    public static Driver fromClass(String driverName) {
      for (Driver driver : values()) {
        if (driver.getDriverClass().equals(driverName)) {
          return driver;
        }
      }
      return NONE;
    }

    public String getDriverClass() {
      return driverClass;
    }

    public String getDependency() {
      return dependency;
    }

    @Override
    public String toString() {
      return driverClass;
    }
  }

  public static final String PREF_LAST_EXPORT_DIR = "last.export.dir";
  public static final String AUTORUN_FILENAME = "autorun.groovy";

  public enum GitStatus {
    GIT_ADDED("-fx-text-fill: #629755;"),
    GIT_UNTRACKED("-fx-text-fill: sienna"),
    GIT_CHANGED("-fx-text-fill: #6897BB"),
    GIT_CONFLICT("-fx-text-fill: red"),
    GIT_IGNORED("-fx-text-fill: grey"),
    GIT_MODIFIED("-fx-text-fill: blue"),
    GIT_UNCOMITTED_CHANGE("-fx-text-fill:  #8AA4C8"),
    GIT_NONE("");

    private final String style;
    GitStatus(String style) {
      this.style = style;
    }
    public String getStyle() {
      return style;
    }
  }

  public static final String REPORT_BUG = "Please report this bug to https://github.com/perNyfelt/gade/issues!";

  public enum MavenRepositoryUrl {
     MAVEN_CENTRAL("Maven Central", "https://repo1.maven.org/maven2/");

     public final String name;
     public final String baseUrl;

     MavenRepositoryUrl(String name, String baseUrl) {
        this.name = name;
        this.baseUrl = baseUrl;
     }
  }

}
