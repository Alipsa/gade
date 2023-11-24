package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.m2.DependencyResolver;
import se.alipsa.gade.utils.m2.ResolvingException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyResolverTest {

  private static final Logger log = LogManager.getLogger();
  @Test
  public void testResolveDependency() throws ResolvingException {
    DependencyResolver resolver = new DependencyResolver();

    List<File> dependencies = resolver.resolve("org.apache.commons:commons-lang3:3.13.0");
    assertEquals(1, dependencies.size());
  }

  @Test
  public void testResolveNonLocalDependency() throws IOException, ResolvingException {
    DependencyResolver resolver = new DependencyResolver();
    File libPhoneNumberDir = new File(System.getProperty("user.home"), "/.m2/repository/com/googlecode/libphonenumber");
    if (libPhoneNumberDir.exists()) {
      log.info("Deleting existing " + libPhoneNumberDir);
      FileUtils.delete(libPhoneNumberDir);
    }

    log.info("Resolving libphonenumber:8.13.26");
    List<File> dependencies = resolver.resolve("com.googlecode.libphonenumber:libphonenumber:8.13.26");
    File dir = new File(libPhoneNumberDir, "/libphonenumber/8.13.26");
    List<String> fileNames = List.of("libphonenumber-8.13.26.jar", "libphonenumber-8.13.26.jar.sha1", "libphonenumber-8.13.26.pom");
    assertEquals(1, dependencies.size());
    Arrays.asList(dir.listFiles()).forEach(f -> {
      if (!"_remote.repositories".equals(f.getName())) {
        assertTrue(fileNames.contains(f.getName()), f.getName() + " was unexpected");
      }
    });
  }
}
