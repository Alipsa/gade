package utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.model.Library;
import se.alipsa.gade.utils.LibraryUtils;

public class LibraryUtilsTest {

  @Test
  public void testGetGroup() {
    assertEquals("se.alipsa", LibraryUtils.getGroup("se.alipsa:rideutils"));
    assertEquals("", LibraryUtils.getGroup("magrittr"));
  }

  @Test
  public void testGetPackage() {
    assertEquals("rideutils", LibraryUtils.getPackage("se.alipsa:rideutils"));
    assertEquals("magrittr", LibraryUtils.getPackage("magrittr"));
  }

  @Test
  public void libraryFromCoordinateParses() {
    Library lib = LibraryUtils.libraryFromCoordinate("org.example:demo:1.2.3");
    assertEquals("org.example", lib.getGroup());
    assertEquals("demo", lib.getPackageName());
    assertEquals("1.2.3", lib.getVersion());
  }

  @Test
  public void testParseGradleCachePath() {
    String path = "/home/user/.gradle/caches/modules-2/files-2.1/org.apache.commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar";
    Library lib = LibraryUtils.parseLibraryFromPath(path);
    assertEquals("org.apache.commons", lib.getGroup());
    assertEquals("commons-lang3", lib.getPackageName());
    assertEquals("3.12.0", lib.getVersion());
  }

  @Test
  public void parseMavenRepoPath() {
    String path = "/home/user/.m2/repository/org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar";
    Library lib = LibraryUtils.parseLibraryFromPath(path);
    assertEquals("org.apache.commons", lib.getGroup());
    assertEquals("commons-lang3", lib.getPackageName());
    assertEquals("3.12.0", lib.getVersion());
  }

  @Test
  public void parseGrabPath() {
    String path = "/home/user/.groovy/grapes/org.apache.commons/commons-lang3/jars/commons-lang3-3.12.0.jar";
    Library lib = LibraryUtils.parseLibraryFromPath(path);
    assertEquals("org.apache.commons", lib.getGroup());
    assertEquals("commons-lang3", lib.getPackageName());
    assertEquals("3.12.0", lib.getVersion());
  }
}
