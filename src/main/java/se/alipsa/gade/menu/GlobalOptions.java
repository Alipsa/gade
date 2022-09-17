package se.alipsa.gade.menu;


import java.util.HashMap;

public class GlobalOptions extends HashMap<String, Object> {

  public static final String CONSOLE_MAX_LENGTH_PREF = "ConsoleTextArea.MaxLength";
  public static final String GRADLE_HOME = "GlobalOptions.GradleHome";
  public static final String USE_GRADLE_CLASSLOADER = "GlobalOptions.UseGradleClassloader";
  public static final String ADD_BUILDDIR_TO_CLASSPATH = "GlobalOptions.AddBuildDirToClasspath";

  public static final String RESTART_SESSION_AFTER_GRADLE_RUN = "GlobalOptions.restartSessionAfterGradleBuild";
  public static final String ENABLE_GIT = "GlobalOptions.EnableGit";
  public static final String AUTORUN_GLOBAL = "GlobalOptions.AutoRunGlobal";
  public static final String AUTORUN_PROJECT = "GlobalOptions.AutoRunProject";
  public static final String DEFAULT_LOCALE = "GlobalOptions.defaultLocale";

  public static final String TIMEZONE = "GlobalOptions.timezone";
  public static final String ADD_IMPORTS = "GlobalOptions.addImports";

  private static final long serialVersionUID = -4781261903018339389L;


  public String getString(String key) {
    return String.valueOf(get(key));
  }
  public int getInt(String key) {
    Object val = get(key);
    if (val instanceof Integer) {
      return (Integer) val;
    } else {
      return Integer.parseInt(val.toString());
    }
  }

  public boolean getBoolean(String key) {
    Object val = get(key);
    if (val instanceof Boolean) {
      return (Boolean) val;
    } else {
      return Boolean.parseBoolean(val.toString());
    }
  }
}
