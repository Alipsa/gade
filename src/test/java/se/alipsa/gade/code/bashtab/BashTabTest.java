package se.alipsa.gade.code.bashtab;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
