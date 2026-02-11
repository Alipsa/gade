package se.alipsa.gade.code.completion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportCompletionTest {

  private static final Logger log = LogManager.getLogger(ImportCompletionTest.class);

  @Test
  void testClasspathScannerFindsGroovyTransform() {
    Map<String, List<String>> index = ClasspathScanner.getInstance().scan(
        getClass().getClassLoader());

    //System.out.println("Total classes indexed: " + index.size());

    // Look for groovy.transform classes
    boolean foundTransform = false;
    for (List<String> fqcns : index.values()) {
      for (String fqcn : fqcns) {
        if (fqcn.startsWith("groovy.transform.")) {
          //System.out.println("Found: " + fqcn);
          foundTransform = true;
        }
      }
    }

    // Also check what packages we have
    //System.out.println("\nPackages starting with 'groovy':");
    java.util.Set<String> packages = new java.util.TreeSet<>();
    for (List<String> fqcns : index.values()) {
      for (String fqcn : fqcns) {
        if (fqcn.startsWith("groovy.")) {
          int lastDot = fqcn.lastIndexOf('.');
          if (lastDot > 0) {
            packages.add(fqcn.substring(0, lastDot));
          }
        }
      }
    }
    //packages.forEach(p -> System.out.println("  " + p));

    assertTrue(foundTransform, "Should find groovy.transform classes");
  }

  @Test
  void testImportCompletionForGroovyTransform() {
    CompletionContext ctx = CompletionContext.builder()
        .fullText("import groovy.tr")
        .caretPosition(16)
        .lineText("import groovy.tr")
        .lineOffset(16)
        .tokenPrefix("tr")
        .classLoader(getClass().getClassLoader())
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);

    log.info("Completions for 'import groovy.tr':");
    for (CompletionItem item : items) {
      log.info("  completion='{}' insertText='{}' ({})", item.completion(), item.insertText(), item.kind());
    }

    assertFalse(items.isEmpty(), "Should have completion suggestions");

    // completion() should be "transform" (for filtering against "tr")
    // insertText() should be "transform" (replaces "tr" to get "groovy.transform")
    CompletionItem transformItem = items.stream()
        .filter(item -> item.completion().equals("transform"))
        .findFirst()
        .orElse(null);

    assertNotNull(transformItem, "Should have 'transform' completion item");
    assertEquals("transform", transformItem.insertText(), "insertText should be segment to replace partial");
    assertEquals("groovy.transform", transformItem.display(), "display should show full path");

    // Verify filtering would work: completion starts with "tr"
    assertTrue(transformItem.completion().startsWith("tr"), "completion should start with 'tr' for filtering");
  }

  @Test
  void testImportCompletionForClasses() {
    // Test completing a class: import groovy.transform.Fi -> Field
    CompletionContext ctx = CompletionContext.builder()
        .fullText("import groovy.transform.Fi")
        .caretPosition(26)
        .lineText("import groovy.transform.Fi")
        .lineOffset(26)
        .tokenPrefix("Fi")
        .classLoader(getClass().getClassLoader())
        .build();

    List<CompletionItem> items = GroovyCompletionEngine.getInstance().complete(ctx);

    log.info("Completions for 'import groovy.transform.Fi':");
    for (CompletionItem item : items) {
      log.info("  completion='{}' insertText='{}'", item.completion(), item.insertText());
    }

    // Should suggest Field class
    CompletionItem fieldItem = items.stream()
        .filter(item -> item.completion().equals("Field"))
        .findFirst()
        .orElse(null);

    assertNotNull(fieldItem, "Should suggest Field class");
    assertEquals("Field", fieldItem.insertText(), "insertText should be class name to replace partial");
    assertEquals("groovy.transform.Field", fieldItem.display(), "display should show full FQCN");
  }
}
