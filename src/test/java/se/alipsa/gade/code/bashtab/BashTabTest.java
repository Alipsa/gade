package se.alipsa.gade.code.bashtab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BashTabTest {

  @Test
  void parseArgsSplitsOnWhitespace() {
    assertEquals(List.of("one", "two", "three"), BashTask.parseArgs("one two three"));
  }

  @Test
  void parseArgsRespectsSingleAndDoubleQuotes() {
    assertEquals(List.of("a", "b c", "d"), BashTask.parseArgs("a 'b c' d"));
    assertEquals(List.of("a", "b c", "d"), BashTask.parseArgs("a \"b c\" d"));
  }

  @Test
  void parseArgsHandlesEmptyAndBlankInput() {
    assertTrue(BashTask.parseArgs("").isEmpty());
    assertTrue(BashTask.parseArgs("   ").isEmpty());
    assertTrue(BashTask.parseArgs(null).isEmpty());
  }

  @Test
  void parseArgsPreservesEmptyQuotedArguments() {
    assertEquals(List.of("", "a", "", "b c"),
        BashTask.parseArgs("\"\" a '' \"b c\""));
  }

  @Test
  void buildCommandRunsCurrentContentEvenForSavedFiles() {
    File file = new File("example.sh");

    assertEquals(List.of("bash", "-c", "echo current", file.getAbsolutePath(), "one two"),
        BashTask.buildCommand("echo current", file, List.of("one two")));
  }

  @Test
  void workingDirFollowsTheFileTreeWorkingDirectory(@TempDir Path treeDir) {
    String original = System.getProperty("user.dir");
    try {
      System.setProperty("user.dir", treeDir.toString());
      assertEquals(treeDir.toFile(), BashTask.workingDir());
    } finally {
      System.setProperty("user.dir", original);
    }
  }

  @Test
  void workingDirFallsBackToTheProcessBuilderDefaultWhenUnusable(@TempDir Path treeDir) {
    String original = System.getProperty("user.dir");
    try {
      System.setProperty("user.dir", treeDir.resolve("gone").toString());
      assertNull(BashTask.workingDir(), "a directory that does not exist should not be used");

      System.setProperty("user.dir", "  ");
      assertNull(BashTask.workingDir(), "a blank user.dir should not be used");
    } finally {
      System.setProperty("user.dir", original);
    }
  }
}
