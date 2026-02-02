package se.alipsa.gade.code.gmdtab;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se.alipsa.gade.Gade;
import se.alipsa.gade.console.ConsoleComponent;
import se.alipsa.gmd.core.GmdException;

class GmdUtilPdfTest {

  private static Field instanceField;
  private Gade previousInstance;

  @BeforeAll
  static void initFx() throws Exception {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // already started
    }
    instanceField = Gade.class.getDeclaredField("instance");
    instanceField.setAccessible(true);
  }

  @BeforeEach
  void setUp() throws Exception {
    previousInstance = (Gade) instanceField.get(null);
    Gade mockGui = Mockito.mock(Gade.class);
    ConsoleComponent console = Mockito.mock(ConsoleComponent.class);
    Mockito.when(mockGui.getConsoleComponent()).thenReturn(console);
    instanceField.set(null, mockGui);
  }

  @AfterEach
  void tearDown() throws Exception {
    instanceField.set(null, previousInstance);
  }

  @Test
  void saveGmdAsPdfCreatesNonEmptyFile(@TempDir File tempDir) throws Exception {
    assumeTrue(canCreateWebView(), "JavaFX WebView not available in this environment");

    File pdf = new File(tempDir, "sample.pdf");
    assertFalse(pdf.exists(), "PDF should not exist before export");

    CompletableFuture<Void> future = new CompletableFuture<>();
    Platform.runLater(() -> {
      try {
        GmdUtil.saveGmdAsPdf("# Title\n\nHello world", pdf);
        future.complete(null);
      } catch (GmdException e) {
        future.completeExceptionally(e);
      }
    });

    assertDoesNotThrow(() -> future.get(40, TimeUnit.SECONDS), "PDF export should complete");
    assertTrue(pdf.exists(), "PDF should exist after export");
    assertTrue(pdf.length() > 0, "PDF should be non-empty");
  }

  private static boolean canCreateWebView() {
    try {
      CompletableFuture<Boolean> ready = new CompletableFuture<>();
      Platform.runLater(() -> {
        try {
          WebView view = new WebView();
          assertNotNull(view);
          ready.complete(true);
        } catch (Throwable t) {
          ready.complete(false);
        }
      });
      return ready.get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      return false;
    }
  }
}
