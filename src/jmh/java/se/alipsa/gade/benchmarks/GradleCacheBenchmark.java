package se.alipsa.gade.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import se.alipsa.gade.utils.gradle.GradleUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for Gradle cache operations.
 * Tests cache directory access patterns (per roadmap Task #27)
 *
 * Run with: ./gradlew jmh -Pjmh="GradleCache"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class GradleCacheBenchmark {

  private File tempProjectDir;
  private File tempCacheFile;

  @Setup
  public void setup() throws Exception {
    // Create temporary project directory
    tempProjectDir = Files.createTempDirectory("gradle-bench").toFile();
    tempProjectDir.deleteOnExit();

    // Create minimal build.gradle
    File buildFile = new File(tempProjectDir, "build.gradle");
    Files.writeString(buildFile.toPath(),
        "plugins { id 'groovy' }\n" +
        "repositories { mavenCentral() }\n" +
        "dependencies { implementation 'org.apache.commons:commons-lang3:3.12.0' }\n");

    // Create a temporary cache file for testing
    Path cachePath = Files.createTempFile("cache-bench", ".json");
    tempCacheFile = cachePath.toFile();
    tempCacheFile.deleteOnExit();
    Files.writeString(cachePath, "{\"dependencies\":[\"org.apache.commons:commons-lang3:3.12.0\"]}");
  }

  @TearDown
  public void teardown() {
    // Clean up temp directory
    deleteRecursively(tempProjectDir);
    if (tempCacheFile != null && tempCacheFile.exists()) {
      tempCacheFile.delete();
    }
  }

  /**
   * Benchmark cache directory access
   */
  @Benchmark
  public void cacheDirectoryAccess(Blackhole blackhole) {
    File cacheDir = GradleUtils.getCacheDir();
    blackhole.consume(cacheDir);
  }

  /**
   * Benchmark file existence check (typical cache hit scenario)
   */
  @Benchmark
  public void cacheHitCheck(Blackhole blackhole) {
    boolean exists = tempCacheFile.exists();
    blackhole.consume(exists);
  }

  /**
   * Benchmark reading a cache file
   */
  @Benchmark
  public void cacheFileRead(Blackhole blackhole) throws Exception {
    String content = Files.readString(tempCacheFile.toPath());
    blackhole.consume(content);
  }

  private void deleteRecursively(File file) {
    if (file == null || !file.exists()) return;
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File f : files) {
          deleteRecursively(f);
        }
      }
    }
    file.delete();
  }
}
