package se.alipsa.gade.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for file operations, particularly large file handling.
 * Tests scenarios mentioned in roadmap Task #27 (10MB+ files)
 *
 * Run with: ./gradlew jmh -Pjmh="FileOperations"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class FileOperationsBenchmark {

  private File smallFile;   // 1KB
  private File mediumFile;  // 1MB
  private File largeFile;   // 10MB

  @Setup
  public void setup() throws IOException {
    // Create test files
    smallFile = createTestFile("small", 1024); // 1KB
    mediumFile = createTestFile("medium", 1024 * 1024); // 1MB
    largeFile = createTestFile("large", 10 * 1024 * 1024); // 10MB
  }

  @TearDown
  public void teardown() {
    smallFile.delete();
    mediumFile.delete();
    largeFile.delete();
  }

  @Benchmark
  public void readSmallFile(Blackhole blackhole) throws IOException {
    String content = Files.readString(smallFile.toPath());
    blackhole.consume(content);
  }

  @Benchmark
  public void readMediumFile(Blackhole blackhole) throws IOException {
    String content = Files.readString(mediumFile.toPath());
    blackhole.consume(content);
  }

  @Benchmark
  public void readLargeFile(Blackhole blackhole) throws IOException {
    String content = Files.readString(largeFile.toPath());
    blackhole.consume(content);
  }

  @Benchmark
  public void bufferedReadSmall(Blackhole blackhole) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(smallFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    blackhole.consume(sb.toString());
  }

  @Benchmark
  public void bufferedReadMedium(Blackhole blackhole) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(mediumFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    blackhole.consume(sb.toString());
  }

  @Benchmark
  public void bufferedReadLarge(Blackhole blackhole) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(largeFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
    }
    blackhole.consume(sb.toString());
  }

  /**
   * Create a test file with specified size
   */
  private File createTestFile(String prefix, int sizeBytes) throws IOException {
    Path tempFile = Files.createTempFile(prefix + "-bench", ".txt");
    File file = tempFile.toFile();
    file.deleteOnExit();

    // Fill with repeating pattern
    StringBuilder content = new StringBuilder(sizeBytes);
    String pattern = "This is a test line for benchmarking file operations.\n";
    int patternLength = pattern.length();

    while (content.length() + patternLength < sizeBytes) {
      content.append(pattern);
    }

    // Fill remaining bytes
    while (content.length() < sizeBytes) {
      content.append('x');
    }

    Files.writeString(tempFile, content.toString());
    return file;
  }
}
