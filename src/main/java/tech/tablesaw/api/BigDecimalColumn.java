package tech.tablesaw.api;


import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;
import it.unimi.dsi.fastutil.objects.*;
import tech.tablesaw.columns.AbstractColumnParser;
import tech.tablesaw.columns.Column;
import tech.tablesaw.columns.numbers.NumberColumnFormatter;
import tech.tablesaw.columns.numbers.NumberFillers;
import tech.tablesaw.columns.numbers.fillers.DoubleRangeIterable;
import tech.tablesaw.selection.BitmapBackedSelection;
import tech.tablesaw.selection.Selection;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class BigDecimalColumn extends NumberColumn<BigDecimalColumn, BigDecimal>
    implements NumberFillers<BigDecimalColumn> {

  protected final ObjectArrayList<BigDecimal> data;


  protected BigDecimalColumn(String name, ObjectArrayList<BigDecimal> data) {
    super(BigDecimalColumnType.instance(), name, BigDecimalColumnType.DEFAULT_PARSER);
    setPrintFormatter(BigDecimalColumnFormatter.floatingPointDefault());
    this.data = data;
  }

  /** {@inheritDoc} */
  @Override
  public String getString(int row) {
    final BigDecimal value = getBigDecimal(row);
    return String.valueOf(getPrintFormatter().format(value));
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return data.size();
  }

  /** {@inheritDoc} */
  @Override
  public void clear() {
    data.clear();
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn setMissing(int index) {
    set(index, BigDecimalColumnType.missingValueIndicator());
    return this;
  }

  protected BigDecimalColumn(String name) {
    super(BigDecimalColumnType.instance(), name, BigDecimalColumnType.DEFAULT_PARSER);
    setPrintFormatter(BigDecimalColumnFormatter.floatingPointDefault());
    this.data = new ObjectArrayList<>(DEFAULT_ARRAY_SIZE);
  }

  public static BigDecimalColumn create(String name, BigDecimal... arr) {
    return new BigDecimalColumn(name, new ObjectArrayList<>(arr));
  }

  public static BigDecimalColumn create(String name) {
    return new BigDecimalColumn(name);
  }

  public static BigDecimalColumn create(String name, double... arr) {
    final BigDecimal[] values = new BigDecimal[arr.length];
    for (int i = 0; i < arr.length; i++) {
      values[i] = BigDecimal.valueOf(arr[i]);
    }
    return new BigDecimalColumn(name, new ObjectArrayList<>(values));
  }

  public static BigDecimalColumn create(String name, float... arr) {
    final BigDecimal[] values = new BigDecimal[arr.length];
    for (int i = 0; i < arr.length; i++) {
      values[i] = BigDecimal.valueOf(arr[i]);
    }
    return new BigDecimalColumn(name, new ObjectArrayList<>(values));
  }

  public static BigDecimalColumn create(String name, int... arr) {
    final BigDecimal[] values = new BigDecimal[arr.length];
    for (int i = 0; i < arr.length; i++) {
      values[i] = BigDecimal.valueOf(arr[i]);
    }
    return new BigDecimalColumn(name, new ObjectArrayList<>(values));
  }

  public static BigDecimalColumn create(String name, long... arr) {
    final BigDecimal[] values = new BigDecimal[arr.length];
    for (int i = 0; i < arr.length; i++) {
      values[i] = BigDecimal.valueOf(arr[i]);
    }
    return new BigDecimalColumn(name, new ObjectArrayList<>(values));
  }

  public static BigDecimalColumn create(String name, Collection<? extends Number> numberList) {
    BigDecimalColumn newColumn = new BigDecimalColumn(name, new ObjectArrayList<>(0));
    for (Number number : numberList) {
      newColumn.append(toBigDecimal(number));
    }
    return newColumn;
  }

  public static BigDecimalColumn create(String name, Number[] numbers) {
    BigDecimalColumn newColumn = new BigDecimalColumn(name, new ObjectArrayList<>(0));
    for (Number number : numbers) {
      newColumn.append(toBigDecimal(number));
    }
    return newColumn;
  }

  public static BigDecimalColumn create(String name, int initialSize) {
    BigDecimalColumn column = new BigDecimalColumn(name);
    for (int i = 0; i < initialSize; i++) {
      column.appendMissing();
    }
    return column;
  }

  public static BigDecimalColumn create(String name, Stream<BigDecimal>stream) {
    ObjectArrayList<BigDecimal> list = new ObjectArrayList<>();
    stream.forEach(list::add);
    return new BigDecimalColumn(name, list);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn createCol(String name, int initialSize) {
    return create(name, initialSize);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn createCol(String name) {
    return create(name);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal get(int index) {
    BigDecimal result = getBigDecimal(index);
    return isMissingValue(result) ? null : result;
  }

  protected BigDecimal getBigDecimal(int index) {
    return data.get(index);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn where(Selection selection) {
    return (BigDecimalColumn) super.where(selection);
  }

  public Selection isNotIn(final BigDecimal... values) {
    final Selection results = new BitmapBackedSelection();
    results.addRange(0, size());
    results.andNot(isIn(values));
    return results;
  }

  public Selection isIn(final BigDecimal... values) {
    final Selection results = new BitmapBackedSelection();
    final ObjectRBTreeSet<BigDecimal> doubleSet = new ObjectRBTreeSet<>(values);
    for (int i = 0; i < size(); i++) {
      if (doubleSet.contains(getBigDecimal(i))) {
        results.add(i);
      }
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn subset(int[] rows) {
    final BigDecimalColumn c = this.emptyCopy();
    for (final int row : rows) {
      c.append(getBigDecimal(row));
    }
    return c;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn unique() {
    final ObjectSet<BigDecimal> values = new ObjectOpenHashSet<>();
    for (int i = 0; i < size(); i++) {
      values.add(getBigDecimal(i));
    }
    final BigDecimalColumn column = BigDecimalColumn.create(name() + " Unique values");
    values.forEach(column::append);
    return column;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn top(int n) {
    ObjectArrayList<BigDecimal> top = new ObjectArrayList<>();
    BigDecimal[] values = data.toArray(new BigDecimal[]{});
    ObjectArrays.parallelQuickSort(values, ObjectComparators.OPPOSITE_COMPARATOR);
    for (int i = 0; i < n && i < values.length; i++) {
      top.add(values[i]);
    }
    return new BigDecimalColumn(name() + "[Top " + n + "]", top);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn bottom(final int n) {
    ObjectArrayList<BigDecimal> bottom = new ObjectArrayList<>();
    BigDecimal[] values = data.toArray(new BigDecimal[]{});
    ObjectArrays.parallelQuickSort(values);
    for (int i = 0; i < n && i < values.length; i++) {
      bottom.add(values[i]);
    }
    return new BigDecimalColumn(name() + "[Bottoms " + n + "]", bottom);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn lag(int n) {
    final int srcPos = n >= 0 ? 0 : -n;
    final BigDecimal[] dest = new BigDecimal[size()];
    final int destPos = Math.max(n, 0);
    final int length = n >= 0 ? size() - n : size() + n;

    for (int i = 0; i < size(); i++) {
      dest[i] = BigDecimalColumnType.missingValueIndicator();
    }

    BigDecimal[] array = data.toArray(new BigDecimal[]{});

    System.arraycopy(array, srcPos, dest, destPos, length);
    return new BigDecimalColumn(name() + " lag(" + n + ")", new ObjectArrayList<>(dest));
  }

  @Override
  public double getDouble(int index) {
    BigDecimal bd = get(index);
    return bd == null ? Double.NaN : bd.doubleValue();
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn removeMissing() {
    BigDecimalColumn result = copy();
    result.clear();
    ObjectListIterator<BigDecimal> iterator = data.iterator();
    while (iterator.hasNext()) {
      BigDecimal v = iterator.next();
      if (!isMissingValue(v)) {
        result.append(v);
      }
    }
    return result;
  }

  /** Adds the given float to this column */
  public BigDecimalColumn append(final BigDecimal f) {
    data.add(f);
    return this;
  }

  /** Adds the given float to this column */
  public BigDecimalColumn append(final float f) {
    data.add(BigDecimal.valueOf(f));
    return this;
  }

  /** Adds the given double to this column */
  public BigDecimalColumn append(double d) {
    data.add(BigDecimal.valueOf(d));
    return this;
  }

  public BigDecimalColumn append(int i) {
    data.add(BigDecimal.valueOf(i));
    return this;
  }

  public BigDecimalColumn append(long l) {
    data.add(BigDecimal.valueOf(l));
    return this;
  }

  public BigDecimalColumn append(Number val) {
    if (val == null) {
      appendMissing();
    } else {
      append(toBigDecimal(val));
    }
    return this;
  }

  public BigDecimalColumn append(String val) {
    if (val == null) {
      appendMissing();
    } else {
      append(parse(val));
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn append(final Column<BigDecimal> column) {
    Preconditions.checkArgument(
        column.type() == this.type(),
        "Column '%s' has type %s, but column '%s' has type %s.",
        name(),
        type(),
        column.name(),
        column.type());
    final BigDecimalColumn numberColumn = (BigDecimalColumn) column;
    final int size = numberColumn.size();
    for (int i = 0; i < size; i++) {
      append(numberColumn.getBigDecimal(i));
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn append(Column<BigDecimal> column, int row) {
    checkArgument(
        column.type() == this.type(),
        "Column '%s' has type %s, but column '%s' has type %s.",
        name(),
        type(),
        column.name(),
        column.type());
    BigDecimalColumn bdColumn = (BigDecimalColumn) column;
    return append(bdColumn.getBigDecimal(row));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn appendMissing() {
    return append(BigDecimalColumnType.missingValueIndicator());
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn appendObj(Object obj) {
    if (obj == null) {
      return appendMissing();
    }
    if (obj instanceof BigDecimal) {
      return append(((BigDecimal) obj));
    }

    if (obj instanceof Number) {
      return append((Number) obj);
    }

    if (obj instanceof String) {
      return append((String) obj);
    }

    throw new IllegalArgumentException("Could not append " + obj.getClass());
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn appendCell(final String value) {
    try {
      return append(parse(value));
    } catch (final NumberFormatException e) {
      throw new NumberFormatException(
          "Error adding value to column " + name() + ": " + e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn appendCell(final String value, AbstractColumnParser<?> parser) {
    try {
      return append(parse(value));
    } catch (final NumberFormatException e) {
      throw new NumberFormatException(
          "Error adding value to column " + name() + ": " + e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnformattedString(final int row) {
    final BigDecimal value = getBigDecimal(row);
    if (BigDecimalColumnType.valueIsMissing(value)) {
      return "";
    }
    return String.valueOf(value);
  }

  /** {@inheritDoc} */
  @Override
  public int valueHash(int rowNumber) {
    BigDecimal value = getBigDecimal(rowNumber);
    return value == null ? Integer.MIN_VALUE : value.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(int rowNumber1, int rowNumber2) {
    BigDecimal val1 = getBigDecimal(rowNumber1);
    BigDecimal val2 = getBigDecimal(rowNumber2);
    if (val1 == null && val2 == null) return true;
    if (val1 != null) {
      return val1.equals(val2);
    }
    return false ;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn copy() {
    BigDecimalColumn copy = new BigDecimalColumn(name(), data.clone());
    copy.setPrintFormatter(getPrintFormatter());
    copy.locale = locale;
    return copy;
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<BigDecimal> iterator() {
    return data.iterator();
  }

  public BigDecimal[] asBigDecimalArray() {
    return data.toArray(new BigDecimal[]{});
  }

  public BigDecimal[] asArray() {
    return asBigDecimalArray();
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimal[] asObjectArray() {
    final BigDecimal[] output = new BigDecimal[size()];
    for (int i = 0; i < size(); i++) {
      if (!isMissing(i)) {
        output[i] = getBigDecimal(i);
      } else {
        output[i] = null;
      }
    }
    return output;
  }

  /** {@inheritDoc} */
  @Override
  public int compare(BigDecimal o1, BigDecimal o2) {
    return compareBigDecimals(o1, o2);
  }

  public static int compareBigDecimals(BigDecimal o1, BigDecimal o2) {
    if ((o1 == null) && (o2 == null)) return 0;
    if ((o1 != null) && (o2 == null)) return -1;
    if (o1 == null) return 1;
    return o1.compareTo(o2);
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn set(int i, BigDecimal val) {
    return val == null ? setMissing(i) : set(i, val);
  }

  /**
   * Updates this column where values matching the selection are replaced with the corresponding
   * value from the given column
   */
  public BigDecimalColumn set(Predicate<BigDecimal> condition, NumericColumn<?> other) {
    for (int row = 0; row < size(); row++) {
      if (condition.test(getBigDecimal(row))) {
        set(row, toBigDecimal(other.get(row)));
      }
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public Column<BigDecimal> set(int row, String stringValue, AbstractColumnParser<?> parser) {
    if (parser instanceof BigDecimalParser) {
      return set(row, ((BigDecimalParser)parser).parse(stringValue));
    }
    return set(row, new BigDecimal(stringValue));
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn set(int row, Column<BigDecimal> column, int sourceRow) {
    Preconditions.checkArgument(column.type() == this.type());
    BigDecimalColumn bdColumn = (BigDecimalColumn) column;
    return set(row, bdColumn.getBigDecimal(sourceRow));
  }

  /**
   * Returns a new NumberColumn with only those rows satisfying the predicate
   *
   * @param test the predicate
   * @return a new NumberColumn with only those rows satisfying the predicate
   */
  public BigDecimalColumn filter(Predicate<? super BigDecimal> test) {
    BigDecimalColumn result = BigDecimalColumn.create(name());
    for (int i = 0; i < size(); i++) {
      BigDecimal d = getBigDecimal(i);
      if (test.test(d)) {
        result.append(d);
      }
    }
    return result;
  }

  /** {@inheritDoc}
   * TODO: uses double which is imprecise
   */
  @Override
  public byte[] asBytes(int rowNumber) {
    return ByteBuffer.allocate(BigDecimalColumnType.instance().byteSize())
        .putDouble(getDouble(rowNumber))
        .array();
  }

  /** {@inheritDoc} */
  @Override
  public Set<BigDecimal> asSet() {
    return new HashSet<>(unique().asList());
  }

  /** {@inheritDoc} */
  @Override
  public int countUnique() {
    ObjectSet<BigDecimal> uniqueElements = new ObjectOpenHashSet<>();
    for (int i = 0; i < size(); i++) {
      uniqueElements.add(getBigDecimal(i));
    }
    return uniqueElements.size();
  }

  public boolean isMissingValue(BigDecimal value) {
    return BigDecimalColumnType.valueIsMissing(value);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isMissing(int rowNumber) {
    return isMissingValue(getBigDecimal(rowNumber));
  }

  /** {@inheritDoc} */
  @Override
  public void sortAscending() {
    data.sort(ObjectComparators.NATURAL_COMPARATOR);
  }

  /** {@inheritDoc} */
  @Override
  public void sortDescending() {
    data.sort(ObjectComparators.OPPOSITE_COMPARATOR);
  }


  public BigDecimalColumn fillWith(final ObjectIterator<BigDecimal> iterator) {
    for (int r = 0; r < size(); r++) {
      if (!iterator.hasNext()) {
        break;
      }
      set(r, iterator.next());
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn fillWith(final DoubleIterator iterator) {
    for (int r = 0; r < size(); r++) {
      if (!iterator.hasNext()) {
        break;
      }
      set(r, BigDecimal.valueOf(iterator.nextDouble()));
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn fillWith(final DoubleRangeIterable iterable) {
    DoubleIterator iterator = iterable.iterator();
    for (int r = 0; r < size(); r++) {
      if (!iterator.hasNext()) {
        iterator = iterable.iterator();
        if (!iterator.hasNext()) {
          break;
        }
      }
      set(r, BigDecimal.valueOf(iterator.nextDouble()));
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn fillWith(final DoubleSupplier supplier) {
    for (int r = 0; r < size(); r++) {
      try {
        set(r, BigDecimal.valueOf(supplier.getAsDouble()));
      } catch (final Exception e) {
        break;
      }
    }
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public BigDecimalColumn fillWith(double d) {
    for (int r = 0; r < size(); r++) {
      set(r, BigDecimal.valueOf(d));
    }
    return this;
  }

  /** {@inheritDoc} */
  public BigDecimalColumn fillWith(BigDecimal d) {
    for (int r = 0; r < size(); r++) {
      set(r, d);
    }
    return this;
  }

  /**
   * Returns a new LongColumn containing a value for each value in this column, truncating if
   * necessary
   *
   * <p>A narrowing primitive conversion such as this one may lose information about the overall
   * magnitude of a numeric value and may also lose precision and range. Specifically, if the value
   * is too small (a negative value of large magnitude or negative infinity), the result is the
   * smallest representable value of type long.
   *
   * <p>Similarly, if the value is too large (a positive value of large magnitude or positive
   * infinity), the result is the largest representable value of type long.
   *
   * <p>Despite the fact that overflow, underflow, or other loss of information may occur, a
   * narrowing primitive conversion never results in a run-time exception.
   *
   * <p>A missing value in the receiver is converted to a missing value in the result
   */
  @Override
  public LongColumn asLongColumn() {
    LongColumn result = LongColumn.create(name());
    for (BigDecimal d : data) {
      if (BigDecimalColumnType.valueIsMissing(d)) {
        result.appendMissing();
      } else {
        result.append(d.longValue());
      }
    }
    return result;
  }

  /**
   * Returns a new IntColumn containing a value for each value in this column, truncating if
   * necessary.
   *
   * <p>A narrowing primitive conversion such as this one may lose information about the overall
   * magnitude of a numeric value and may also lose precision and range. Specifically, if the value
   * is too small (a negative value of large magnitude or negative infinity), the result is the
   * smallest representable value of type int.
   *
   * <p>Similarly, if the value is too large (a positive value of large magnitude or positive
   * infinity), the result is the largest representable value of type int.
   *
   * <p>Despite the fact that overflow, underflow, or other loss of information may occur, a
   * narrowing primitive conversion never results in a run-time exception.
   *
   * <p>A missing value in the receiver is converted to a missing value in the result
   */
  @Override
  public IntColumn asIntColumn() {
    IntColumn result = IntColumn.create(name());
    for (BigDecimal d : data) {
      if (BigDecimalColumnType.valueIsMissing(d)) {
        result.appendMissing();
      } else {
        result.append(d.intValue());
      }
    }
    return result;
  }

  /**
   * Returns a new ShortColumn containing a value for each value in this column, truncating if
   * necessary.
   *
   * <p>A narrowing primitive conversion such as this one may lose information about the overall
   * magnitude of a numeric value and may also lose precision and range. Specifically, if the value
   * is too small (a negative value of large magnitude or negative infinity), the result is the
   * smallest representable value of type int.
   *
   * <p>Similarly, if the value is too large (a positive value of large magnitude or positive
   * infinity), the result is the largest representable value of type short.
   *
   * <p>Despite the fact that overflow, underflow, or other loss of information may occur, a
   * narrowing primitive conversion never results in a run-time exception.
   *
   * <p>A missing value in the receiver is converted to a missing value in the result
   */
  @Override
  public ShortColumn asShortColumn() {
    ShortColumn result = ShortColumn.create(name());
    for (BigDecimal d : data) {
      if (BigDecimalColumnType.valueIsMissing(d)) {
        result.appendMissing();
      } else {
        result.append(d.shortValue());
      }
    }
    return result;
  }

  /**
   * Returns a new FloatColumn containing a value for each value in this column, truncating if
   * necessary.
   *
   * <p>A narrowing primitive conversion such as this one may lose information about the overall
   * magnitude of a numeric value and may also lose precision and range. Specifically, if the value
   * is too small (a negative value of large magnitude or negative infinity), the result is the
   * smallest representable value of type float.
   *
   * <p>Similarly, if the value is too large (a positive value of large magnitude or positive
   * infinity), the result is the largest representable value of type float.
   *
   * <p>Despite the fact that overflow, underflow, or other loss of information may occur, a
   * narrowing primitive conversion never results in a run-time exception.
   *
   * <p>A missing value in the receiver is converted to a missing value in the result
   */
  @Override
  public FloatColumn asFloatColumn() {
    FloatColumn result = FloatColumn.create(name());
    for (BigDecimal d : data) {
      if (BigDecimalColumnType.valueIsMissing(d)) {
        result.appendMissing();
      } else {
        result.append(d.floatValue());
      }
    }
    return result;
  }

  protected static BigDecimal toBigDecimal(Number number) {
    if (number instanceof Integer
        || number instanceof Long
        || number instanceof Short
        || number instanceof Byte) {
      return BigDecimal.valueOf(number.longValue());
    }
    return BigDecimal.valueOf(number.doubleValue());
  }

  /**
   * Add all the big decimals in the list to this column
   *
   * @param values a list of values
   */
  public BigDecimalColumn addAll(List<BigDecimal> values) {
    for (BigDecimal value : values) {
      append(value);
    }
    return this;
  }

  protected BigDecimal parse(String val) {
    return parser().parse(val);
  }

  protected BigDecimalColumnFormatter getPrintFormatter() {
    return (BigDecimalColumnFormatter)super.getPrintFormatter();
  }

  public void setPrintFormatter(NumberColumnFormatter formatter) {
    if (formatter instanceof BigDecimalColumnFormatter) {
      super.setPrintFormatter(formatter);
    } else {
      throw new IllegalArgumentException("Formatter must be an instance of BigDecimalColumnFormatter");
    }
  }

  protected void setPrintFormatter(BigDecimalColumnFormatter formatter) {
    super.setPrintFormatter(formatter);
  }

}
