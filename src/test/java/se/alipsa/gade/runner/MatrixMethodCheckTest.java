package se.alipsa.gade.runner;

import org.junit.jupiter.api.Test;
import se.alipsa.matrix.core.Matrix;
import se.alipsa.matrix.core.MatrixBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;

public class MatrixMethodCheckTest {

  @Test
  void checkMatrixMethods() {
    System.out.println("Matrix class location: " + Matrix.class.getProtectionDomain().getCodeSource().getLocation());
    System.out.println("\nMatrix methods containing 'Csv':");
    for (Method m : Matrix.class.getMethods()) {
      if (m.getName().toLowerCase().contains("csv")) {
        System.out.println("  " + m.getName() + ": " + Arrays.toString(m.getParameterTypes()));
      }
    }

    System.out.println("\nMatrixBuilder methods containing 'csv':");
    for (Method m : MatrixBuilder.class.getMethods()) {
      if (m.getName().toLowerCase().contains("csv")) {
        System.out.println("  " + m.getName() + ": " + Arrays.toString(m.getParameterTypes()));
      }
    }
  }
}
