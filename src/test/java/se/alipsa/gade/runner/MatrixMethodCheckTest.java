package se.alipsa.gade.runner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.matrix.core.MatrixBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;

public class MatrixMethodCheckTest {

  private static final Logger log = LogManager.getLogger(MatrixMethodCheckTest.class);

  @Test
  void checkMatrixMethods() {
    log.info("Matrix class location: {}", Matrix.class.getProtectionDomain().getCodeSource().getLocation());
    log.info("Matrix methods containing 'Csv':");
    for (Method m : Matrix.class.getMethods()) {
      if (m.getName().toLowerCase().contains("csv")) {
        log.info("  {}: {}", m.getName(), Arrays.toString(m.getParameterTypes()));
      }
    }

    log.info("MatrixBuilder methods containing 'csv':");
    for (Method m : MatrixBuilder.class.getMethods()) {
      if (m.getName().toLowerCase().contains("csv")) {
        log.info("  {}: {}", m.getName(), Arrays.toString(m.getParameterTypes()));
      }
    }
  }
}
