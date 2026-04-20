# Matrix Charts API Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate gade from the old `se.alipsa.matrix.charts` package to the new `se.alipsa.matrix.pict` package and `se.alipsa.matrix.chartexport` API introduced in matrix-charts 0.5.0.

**Architecture:** Package rename (`charts` -> `pict`), replace deprecated `Plot` class with `ChartToJfx` / `ChartToPng` from the `chartexport` package. xchart examples rewritten to use `se.alipsa.matrix.xchart` directly.

**Tech Stack:** Java 21, Groovy 5, JavaFX 23, matrix-charts 0.5.0 (BOM 2.5.0-SNAPSHOT)

---

### Task 1: Update InOut.java

**Files:**
- Modify: `src/main/java/se/alipsa/gade/interaction/InOut.java:25-26,347-349,591-611`

- [ ] **Step 1: Update imports**

Replace the two chart imports at lines 25-26:

```java
// Old:
import se.alipsa.matrix.charts.Chart;
import se.alipsa.matrix.charts.Plot;

// New:
import se.alipsa.matrix.pict.Chart;
import se.alipsa.matrix.chartexport.ChartToJfx;
```

- [ ] **Step 2: Update display(Chart) method**

At line 349, replace `Plot.jfx(chart)` with `ChartToJfx.export(chart)`:

```java
// Old:
display(Plot.jfx(chart), false, title);

// New:
display(ChartToJfx.export(chart), false, title);
```

- [ ] **Step 3: Update save(Chart) FQCNs and method bodies**

At lines 591, 597, 610: replace `se.alipsa.matrix.charts.Chart` with `se.alipsa.matrix.pict.Chart`.

At line 611: replace `se.alipsa.matrix.charts.Plot.jfx(chart)` with `ChartToJfx.export(chart)`.

```java
// Line 591 - old:
public void save(se.alipsa.matrix.charts.Chart chart, File file) {
// Line 591 - new:
public void save(se.alipsa.matrix.pict.Chart chart, File file) {

// Line 597 - old:
public void save(se.alipsa.matrix.charts.Chart chart, File file, double width, double height) {
// Line 597 - new:
public void save(se.alipsa.matrix.pict.Chart chart, File file, double width, double height) {

// Line 610 - old:
public void save(se.alipsa.matrix.charts.Chart chart, File file, double width, double height, boolean useGadeStyle) {
// Line 610 - new:
public void save(se.alipsa.matrix.pict.Chart chart, File file, double width, double height, boolean useGadeStyle) {

// Line 611 - old:
save(se.alipsa.matrix.charts.Plot.jfx(chart), file, width, height, useGadeStyle, false);
// Line 611 - new:
save(ChartToJfx.export(chart), file, width, height, useGadeStyle, false);
```

`ChartToJfx.export(Chart)` returns `SVGImage` which extends `Group` -> `Parent`, so the `save(Parent, ...)` overload resolves cleanly without casts.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/se/alipsa/gade/interaction/InOut.java
git commit -m "update InOut to use matrix-charts 0.5.0 pict/chartexport API"
```

---

### Task 2: Update DeepCopyTest.java

**Files:**
- Modify: `src/test/java/utils/DeepCopyTest.java:12-13,146-181`

- [ ] **Step 1: Update imports**

At lines 12-13, replace:

```java
// Old:
import se.alipsa.matrix.charts.ChartType;
import se.alipsa.matrix.charts.Plot;

// New:
import se.alipsa.matrix.pict.ChartType;
import se.alipsa.matrix.chartexport.ChartToJfx;
```

Add missing assertions import (needed for new test):

```java
import static org.junit.jupiter.api.Assertions.assertNotNull;
```

- [ ] **Step 2: Update testCopyBarchart2 method**

`ChartToJfx.export()` returns an `SVGImage` (not a `javafx.scene.chart.BarChart`), so the existing casts and JFX-chart-specific assertions no longer apply. Update the test to verify the export produces a valid node that can be deep-copied:

```java
@Test
public void testCopyBarchart2() {

  var empData = Matrix.builder().columns(Map.of(
          "emp_id", Arrays.asList(1, 2, 3, 4, 5),
          "emp_name", Arrays.asList("Rick", "Dan", "Michelle", "Ryan", "Gary"),
          "salary", Arrays.asList(623.3, 515.2, 611.0, 729.0, 843.25),
          "start_date", toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27")))
      .types(int.class, String.class, Number.class, LocalDate.class)
      .build();

  var chart = se.alipsa.matrix.pict.BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary");
  Node jfxNode = ChartToJfx.export(chart);
  assertNotNull(jfxNode, "ChartToJfx.export should return a non-null node");
  var copy = DeepCopier.deepCopy(jfxNode);
  assertNotNull(copy, "Deep copy should return a non-null node");
  assertEquals(jfxNode.getClass(), copy.getClass(), "Copy should be same class");
}
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/utils/DeepCopyTest.java
git commit -m "update DeepCopyTest for matrix-charts 0.5.0 ChartToJfx API"
```

---

### Task 3: Build and verify compilation

- [ ] **Step 1: Run build**

```bash
./gradlew test -g ./.gradle-user
```

Expected: compilation succeeds and tests pass. If `testCopyBarchart2` fails because `DeepCopier` doesn't support `SVGImage`, remove the deep-copy assertions and keep only the `ChartToJfx.export` assertion.

---

### Task 4: Update examples/xcharts/ to use matrix-xchart

These examples should use `se.alipsa.matrix.xchart.*` classes directly. Remove all references to deleted `SwingPlot` and old `se.alipsa.matrix.charts.*` package.

**Files:**
- Modify: `examples/xcharts/src/BarChart.groovy`
- Modify: `examples/xcharts/src/BoxChart.groovy`
- Modify: `examples/xcharts/src/PieChart.groovy`

- [ ] **Step 1: Rewrite BarChart.groovy**

```groovy
import java.time.LocalDate
import se.alipsa.matrix.core.*
import se.alipsa.matrix.xchart.BarChart
import static se.alipsa.matrix.core.ListConverter.*

empData = Matrix.builder().data(
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3, 515.2, 611.0, 729.0, 843.25],
    bonus: [12.2, 10.4, 75.2, 19.1, 55.1],
  )
  .types(String, BigDecimal, Number)
  .build()

println empData.content()

chart = BarChart.create(empData)
io.display(chart, "xchart barchart")
```

- [ ] **Step 2: Rewrite BoxChart.groovy**

```groovy
import se.alipsa.matrix.core.*
import se.alipsa.matrix.xchart.BoxChart

boxData = Matrix.builder().data(
    aaa: [40, 30, 20, 60, 50],
    bbb: [-20, -10, -30, -15, -25],
    ccc: [50, -20, 10, 15, 25]
  )
  .types(int, int, int)
  .build()

io.view(boxData)

chart = BoxChart.create(boxData)
io.display(chart, 'xchart boxchart')
```

- [ ] **Step 3: Rewrite PieChart.groovy**

```groovy
import java.time.LocalDate
import se.alipsa.matrix.core.*
import se.alipsa.matrix.xchart.PieChart
import static se.alipsa.matrix.core.ListConverter.*

empData = Matrix.builder().data(
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3, 515.2, 611.0, 729.0, 843.25],
  )
  .types(String, BigDecimal)
  .build()

chart = PieChart.create(empData)
io.display(chart, "xchart piechart")
```

- [ ] **Step 4: Commit**

```bash
git add examples/xcharts/src/BarChart.groovy examples/xcharts/src/BoxChart.groovy examples/xcharts/src/PieChart.groovy
git commit -m "rewrite xchart examples to use matrix-xchart module directly"
```

---

### Task 5: Update examples/charts/ for pict package

These examples use the very old `se.alipsa.groovy.charts.*` package AND deleted classes (`SwingPlot`, `Plot.jfx`, `Plot.png`). Update to `se.alipsa.matrix.pict.*` and `se.alipsa.matrix.chartexport.*`.

**Files:**
- Modify: `examples/charts/Barchart.groovy`
- Modify: `examples/charts/piechart.groovy`

- [ ] **Step 1: Rewrite Barchart.groovy**

```groovy
import java.time.LocalDate
import se.alipsa.matrix.core.*
import se.alipsa.matrix.pict.*
import se.alipsa.matrix.chartexport.ChartToJfx
import se.alipsa.matrix.chartexport.ChartToPng

import static se.alipsa.matrix.core.ListConverter.*

empData = Matrix.builder().data(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
  )
  .types(int, String, Number, LocalDate)
  .build()

chart = BarChart.createVertical("Salaries", empData, "emp_name", ChartType.BASIC, "salary")
chart.style.legendVisible = true
chart.style.setLegendPosition('LEFT')
io.display(chart, "charts barchart")

jfxChart = ChartToJfx.export(chart)
io.display(jfxChart, "jfx barchart")

file = io.projectFile("barchart.png")
ChartToPng.export(chart, file, 800, 600)
io.display(file)

file2 = io.projectFile("barchart2.png")
io.save(chart, file2)
io.display(file2)

""
```

- [ ] **Step 2: Rewrite piechart.groovy**

```groovy
import java.time.LocalDate
import se.alipsa.matrix.core.*
import se.alipsa.matrix.pict.*
import se.alipsa.matrix.chartexport.ChartToJfx
import se.alipsa.matrix.chartexport.ChartToPng

import static se.alipsa.matrix.core.ListConverter.*

empData = Matrix.builder().data(
    emp_id: 1..5,
    emp_name: ["Rick","Dan","Michelle","Ryan","Gary"],
    salary: [623.3,515.2,611.0,729.0,843.25],
    start_date: toLocalDates("2012-01-01", "2013-09-23", "2014-11-15", "2014-05-11", "2015-03-27"),
  )
  .types(int, String, Number, LocalDate)
  .build()

chart = PieChart.create("Salaries", empData, "emp_name", "salary")
chart.style.plotBackgroundColor = new java.awt.Color(30, 30, 128)
chart.style.chartBackgroundColor = new java.awt.Color(60, 100, 170)
chart.style.legendVisible = true
chart.style.legendPosition = 'BOTTOM'
chart.style.titleVisible = true

io.display(chart, "pict piechart")

file = io.projectFile("piechart.png")
ChartToPng.export(chart, file, 542, 345)
io.display(file)

file2 = io.projectFile("piechart2.png")
io.save(chart, file2)
io.display(file2)
```

- [ ] **Step 3: Commit**

```bash
git add examples/charts/Barchart.groovy examples/charts/piechart.groovy
git commit -m "update chart examples for matrix-charts 0.5.0 pict/chartexport API"
```

---

### Task 6: Update javadoc and documentation

**Files:**
- Modify: `src/main/java/se/alipsa/gade/runner/GadeScript.java:14`
- Modify: `src/main/java/se/alipsa/gade/runner/PackageProxy.java:9`
- Modify: `docs/improvements/fqcn-resolution-error-messages.md:11,27`
- Modify: `docs/improvements/CLAUDE_PROMPT_FOR_GI_CONSOLE.md:74`

- [ ] **Step 1: Update GadeScript.java javadoc**

At line 14, replace:

```java
// Old:
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.charts.BoxChart.create(...)},
// New:
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.pict.BoxChart.create(...)},
```

- [ ] **Step 2: Update PackageProxy.java javadoc**

At line 9, replace:

```java
// Old:
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.charts.BoxChart.create(...)},
// New:
 * When a Groovy script uses an inline FQCN like {@code se.alipsa.matrix.pict.BoxChart.create(...)},
```

- [ ] **Step 3: Update fqcn-resolution-error-messages.md**

At line 11, replace `se.alipsa.matrix.charts.BoxChart` with `se.alipsa.matrix.pict.BoxChart`.

At line 27, replace `se.alipsa.matrix.charts.BoxChart` with `se.alipsa.matrix.pict.BoxChart`.

- [ ] **Step 4: Update CLAUDE_PROMPT_FOR_GI_CONSOLE.md**

At line 74, replace:

```
import se.alipsa.matrix.charts.Chart
```

with:

```
import se.alipsa.matrix.pict.Chart
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/se/alipsa/gade/runner/GadeScript.java \
  src/main/java/se/alipsa/gade/runner/PackageProxy.java \
  docs/improvements/fqcn-resolution-error-messages.md \
  docs/improvements/CLAUDE_PROMPT_FOR_GI_CONSOLE.md
git commit -m "update javadoc and docs for matrix-charts 0.5.0 package rename"
```

---

### Task 7: Final build and test

- [ ] **Step 1: Run full test suite**

```bash
./gradlew test -g ./.gradle-user
```

Expected: all tests pass, no compilation errors.

- [ ] **Step 2: Verify no remaining old references**

```bash
grep -r "se\.alipsa\.matrix\.charts" src/ examples/ docs/ --include="*.java" --include="*.groovy" --include="*.md" | grep -v "matrix-charts" | grep -v "build.gradle"
```

Expected: no matches (all old `se.alipsa.matrix.charts` package references are gone). References to the `matrix-charts` module name (artifact ID) are expected and correct.
