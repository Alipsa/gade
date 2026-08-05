package se.alipsa.gade.code.jstab;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.UniqueTag;

/**
 * Converts Rhino JavaScript values used by the JavaScript tab into Java values.
 */
public final class RhinoValueConverter {

  private RhinoValueConverter() {
  }

  public static Object[][] toObjectMatrix(Object value) {
    if (!(value instanceof NativeArray array)) {
      throw new IllegalArgumentException("Expected a JavaScript array");
    }
    int rowCount = checkedLength(array);
    Object[][] matrix = new Object[rowCount][];
    for (int row = 0; row < rowCount; row++) {
      Object rowValue = array.get(row, array);
      if (!(rowValue instanceof NativeArray rowArray)) {
        throw new IllegalArgumentException("Expected a two-dimensional JavaScript array");
      }
      matrix[row] = toObjectArray(rowArray);
    }
    return matrix;
  }

  private static Object[] toObjectArray(NativeArray array) {
    int length = checkedLength(array);
    Object[] result = new Object[length];
    for (int index = 0; index < length; index++) {
      Object value = array.get(index, array);
      result[index] = toJavaValue(value);
    }
    return result;
  }

  private static Object toJavaValue(Object value) {
    if (value == Undefined.instance || value == UniqueTag.NOT_FOUND) {
      return null;
    }
    if (value instanceof NativeArray array) {
      return toObjectArray(array);
    }
    Object javaValue = Context.jsToJava(value, Object.class);
    if (javaValue instanceof Double number) {
      return narrowIntegral(number);
    }
    return javaValue;
  }

  private static Object narrowIntegral(Double number) {
    double value = number;
    if (!Double.isFinite(value) || value != Math.rint(value)) {
      return number;
    }
    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
      return number.intValue();
    }
    if (value >= Long.MIN_VALUE && value < 0x1.0p63) {
      return number.longValue();
    }
    return number;
  }

  private static int checkedLength(NativeArray array) {
    long length = array.getLength();
    if (length > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("JavaScript array is too large");
    }
    return (int) length;
  }
}
