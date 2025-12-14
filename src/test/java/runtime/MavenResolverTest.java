package runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.runtime.MavenResolver;

class MavenResolverTest {

  @Test
  void parsesPomDependencies() throws Exception {
    File pom = File.createTempFile("resolver", ".pom");
    pom.deleteOnExit();
    try (FileWriter fw = new FileWriter(pom)) {
      fw.write("""
          <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>
            <groupId>org.example</groupId>
            <artifactId>demo</artifactId>
            <version>1.0</version>
            <properties>
              <commons.version>3.12.0</commons.version>
            </properties>
            <dependencies>
              <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>${commons.version}</version>
              </dependency>
              <dependency>
                <groupId>junit</groupId>
                <artifactId>junit</artifactId>
                <version>4.13.2</version>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </project>
          """);
    }
    List<String> deps = MavenResolver.dependenciesFromPom(pom);
    assertEquals(1, deps.size());
    assertEquals("org.apache.commons:commons-lang3:3.12.0", deps.getFirst());
    assertFalse(deps.stream().anyMatch(s -> s.contains("junit")));
  }
}
