# Matrix Charts API Migration (0.5.0)

## Problem

The matrix project's `matrix-charts` module has breaking changes in v0.5.0:

1. **Package rename**: `se.alipsa.matrix.charts` → `se.alipsa.matrix.pict`
2. **Deleted backends**: `charts.swing.SwingPlot`, `charts.jfx.*`, `charts.png.*`, `charts.svg.*` — all removed
3. **`Plot` deprecated**: `Plot.jfx()` return type changed from `javafx.scene.chart.Chart` to `javafx.scene.Node`
4. **New export API**: `se.alipsa.matrix.chartexport.ChartToJfx`, `ChartToPng`, etc. replace deleted backends

Gade's BOM has been updated to `2.5.0-SNAPSHOT` which pulls in these changes.

## Solution

### InOut.java

Replace `Plot` with `ChartToJfx` from the `chartexport` package:

- Import `se.alipsa.matrix.pict.Chart` (was `se.alipsa.matrix.charts.Chart`)
- Import `se.alipsa.matrix.chartexport.ChartToJfx` (was `se.alipsa.matrix.charts.Plot`)
- `display(Chart chart, ...)`: `Plot.jfx(chart)` → `ChartToJfx.export(chart)`
- `save(Chart chart, ...)` (3 overloads): update FQCNs and replace `Plot.jfx(chart)` → `ChartToJfx.export(chart)`
- `ChartToJfx.export(Chart)` returns `SVGImage` (extends `Group` → `Parent`), so `save(Parent, ...)` compiles without casts

### DeepCopyTest.java

Update imports:
- `se.alipsa.matrix.charts.ChartType` → `se.alipsa.matrix.pict.ChartType`
- `se.alipsa.matrix.charts.Plot` → `se.alipsa.matrix.chartexport.ChartToJfx`
- `se.alipsa.matrix.charts.BarChart` → `se.alipsa.matrix.pict.BarChart`

### Example scripts (examples/xcharts/)

These examples should use the `matrix-xchart` module (`se.alipsa.matrix.xchart.*`) to produce charts, not the pict/charts module. Remove all `SwingPlot` references (deleted in 0.5.0):

- `BarChart.groovy` — use `se.alipsa.matrix.xchart.BarChart`
- `BoxChart.groovy` — use `se.alipsa.matrix.xchart.BoxChart`
- `PieChart.groovy` — use `se.alipsa.matrix.xchart.PieChart`

### Javadoc and documentation

Update package references in:
- `GadeScript.java` — comment mentions `se.alipsa.matrix.charts.BoxChart`
- `PackageProxy.java` — comment mentions `se.alipsa.matrix.charts.BoxChart`
- `docs/improvements/fqcn-resolution-error-messages.md`
- `docs/improvements/CLAUDE_PROMPT_FOR_GI_CONSOLE.md`

## Testing

- Run `./gradlew test -g ./.gradle-user` to verify compilation and tests pass
- Manually verify example scripts if possible
