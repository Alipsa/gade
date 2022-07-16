package se.alipsa.grade.utils;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.grade.console.ConsoleComponent;
import se.alipsa.grade.inout.PackagesTab;
import se.alipsa.grade.model.Library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LibraryUtils {

  private static final Logger LOG = LogManager.getLogger();

  /**
   * Scan all ClassLoaders for Extensions (libraries / packages)
   * @param classLoader this is needed if we use AetherPackageLoader to pick upp dynamically fetched libraries
   * @return a set of Libraries (which is an Object equivalent of the dependencies)
   */
  public static Set<Library> getAvailableLibraries(ClassLoader classLoader) throws IOException {
    Map<String, Library> packageNames = new HashMap<>();
    AtomicInteger count = new AtomicInteger();
    try (ScanResult scanResult = new ClassGraph().addClassLoader(classLoader).scan()) {
      scanResult.getResourcesWithLeafName("DESCRIPTION")
          .forEachByteArrayThrowingIOException((Resource res, byte[] fileContent) -> {
            count.incrementAndGet();
            Library library = parseDescription(res.getPath(), new String(fileContent, StandardCharsets.UTF_8));
            if (library != null) {
              Library existing = packageNames.get(library.getFullName());
              if (existing == null || SemanticVersion.compare(existing.getVersion(), library.getVersion()) < 0) {
                packageNames.put(library.getFullName(), library);
              }
            }
      });
      if (count.intValue() != packageNames.size()) {
        LOG.info("Parsed {} DESCRIPTION files, returning {} unique packages " +
            "(you likely have different versions of the same package in your classpath)",
            count.intValue(), packageNames.size());
      }
    } catch (IOException e) {
      throw new IOException("Failed to read DESCRIPTION file", e);
    }
    return new HashSet<>(packageNames.values());
  }

  public static Library parseDescription(String path, String content) throws IOException {
    String packageName = null;
    String groupName = "";
    String title = "";
    String version = "";

    try (BufferedReader reader = new BufferedReader(new StringReader(content))){
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("Package: ")) {
          packageName = line.substring("Package: ".length());
        } else if (line.startsWith("GroupId: ")) {
          groupName = line.substring("GroupId: ".length());
        } else if (line.startsWith("Title: ")) {
          title = line.substring("Title: ".length());
        } else if (line.startsWith("Version: ")) {
          version = line.substring("Version: ".length());
        }
      }
    } catch (IOException e) {
      throw new IOException("Failed to parse DESCRIPTION in " + path, e);
    }
    return packageName == null ? null : new Library(title, groupName, packageName, version);
  }

  public static String extractPackageName(String content) throws IOException {
    String packageName = null;
    String groupName = "";
    try (BufferedReader reader = new BufferedReader(new StringReader(content))){
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("Package: ")) {
          packageName = line.substring("Package: ".length());
        } else if (line.startsWith("GroupId: ")) {
          groupName = line.substring("GroupId: ".length()) + ":";
        }
      }
    } catch (IOException e) {
      throw new IOException("Failed to extract package name and groupId from DESCRIPTION file", e);
    }
    return packageName == null ? null : groupName + packageName;
  }

  public static String extractPackageNameFromResource(String path) throws IOException {
    URL url = FileUtils.getResourceUrl(path);
    if (url == null) {
      System.err.println("Failed to find " + path);
      return null;
    }
    try (InputStream is = url.openStream()){
      List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8);
      for ( String line : lines) {
        if (line.startsWith("Package: ")) {
          return line.substring("Package: ".length());
        }
      }
    } catch (IOException e) {
      throw new IOException("Failed to extract package name and groupId from DESCRIPTION file in " + path, e);
    }
    return null;
  }

  public static String getPackage(String fullPackageName) {
    if (fullPackageName.contains(":")) {
      return fullPackageName.split(":")[1];
    }
    return fullPackageName;
  }

  public static String getGroup(String fullPackageName) {
    if (fullPackageName.contains(":")) {
      return fullPackageName.split(":")[0];
    }
    return "";
  }

  public static void loadOrUnloadLibrary(ConsoleComponent console, PackagesTab.AvailablePackage pkg, Boolean isLoaded) throws Exception {
    // TODO not sure what to do here, comment out the stuff from Ride
    try {
      //Object result;
      if (isLoaded) {
        console.addOutput("Packages", "loading package " + pkg.getLibrary().getFullName(), true, true);
        //result = console.runScript("library('" + pkg.getLibrary().getFullName() + "')");
      } else {
        console.addOutput("Packages", "unloading package " + pkg.getLibrary().getFullName(), true, true);
        //result = console.runScript("detach('package:" + pkg.getLibrary().getPackageName() + "')");
      }
      //LOG.info(result);
    } catch (Exception e) {
      String action = isLoaded ? "load" : "unload";
      throw new Exception("Failed to " + action + " library", e);
    }
  }
}
