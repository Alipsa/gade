package utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.m2.DependencyResolver;
import se.alipsa.gade.utils.m2.ResolvingException;

import java.io.File;
import java.util.List;

public class DependencyResolverTest {

  @Test
  public void testResolveDependency() throws ResolvingException {
    DependencyResolver resolver = new DependencyResolver();

    List<File> dependencies = resolver.resolve("org.apache.commons:commons-lang3:3.13.0");
    Assertions.assertEquals(1, dependencies.size());
  }
}
