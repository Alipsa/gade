package se.alipsa.gade.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import se.alipsa.gade.code.completion.*;
import se.alipsa.gade.code.completion.groovy.GroovyCompletionEngine;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for code completion engine performance.
 * Target: < 100ms for completion suggestions (per roadmap Task #27)
 *
 * Run with: ./gradlew jmh
 * Or specific benchmark: ./gradlew jmh -Pjmh="CompletionEngine"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CompletionEngineBenchmark {

  private CompletionRegistry registry;
  private GroovyCompletionEngine groovyEngine;

  // Sample Groovy code for completion testing
  private static final String SIMPLE_GROOVY = "def x = 'hello'\nx.";
  private static final String COMPLEX_GROOVY =
      "import groovy.sql.Sql\n" +
      "def sql = Sql.newInstance('jdbc:h2:mem:test', 'sa', '')\n" +
      "sql.";

  private static final String MEMBER_ACCESS =
      "class Person { String name; int age }\n" +
      "def p = new Person()\n" +
      "p.";

  @Setup
  public void setup() {
    registry = CompletionRegistry.getInstance();
    groovyEngine = new GroovyCompletionEngine();
    registry.register(groovyEngine);
  }

  @Benchmark
  public void simpleStringCompletion(Blackhole blackhole) {
    CompletionContext context = CompletionContext.builder()
        .fullText(SIMPLE_GROOVY)
        .caretPosition(SIMPLE_GROOVY.length())
        .build();

    List<CompletionItem> items = groovyEngine.complete(context);
    blackhole.consume(items);
  }

  @Benchmark
  public void complexImportCompletion(Blackhole blackhole) {
    CompletionContext context = CompletionContext.builder()
        .fullText(COMPLEX_GROOVY)
        .caretPosition(COMPLEX_GROOVY.length())
        .build();

    List<CompletionItem> items = groovyEngine.complete(context);
    blackhole.consume(items);
  }

  @Benchmark
  public void memberAccessCompletion(Blackhole blackhole) {
    CompletionContext context = CompletionContext.builder()
        .fullText(MEMBER_ACCESS)
        .caretPosition(MEMBER_ACCESS.length())
        .build();

    List<CompletionItem> items = groovyEngine.complete(context);
    blackhole.consume(items);
  }

  @Benchmark
  public void contextBuildingOverhead(Blackhole blackhole) {
    // Measure just the context building time
    CompletionContext context = CompletionContext.builder()
        .fullText(SIMPLE_GROOVY)
        .caretPosition(SIMPLE_GROOVY.length())
        .build();

    blackhole.consume(context);
  }

  /**
   * Baseline measurement - registry lookup only
   */
  @Benchmark
  public void registryLookup(Blackhole blackhole) {
    boolean hasEngine = registry.hasEngine("groovy");
    blackhole.consume(hasEngine);
  }
}
