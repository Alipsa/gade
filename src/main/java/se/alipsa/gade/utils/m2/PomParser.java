package se.alipsa.gade.utils.m2;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import se.alipsa.gade.model.Dependency;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PomParser {
  public static List<Dependency> getDependencies(File pomFile) throws DocumentException {
    SAXReader reader = new SAXReader();
    Document document = reader.read(pomFile);

    // TODO: if there is a parent, follow the stream of parents to the end

    Element properties = document.getRootElement().element("properties");
    Map<String, String> propertiesMap = new HashMap<>();
    if (properties != null) {
      for (Element prop : properties.elements()) {
        propertiesMap.put(prop.getName(), prop.getTextTrim());
      }
    }
    Element dependencies = document.getRootElement().element("dependencies");
    List<Dependency> result = new ArrayList<>();
    if (dependencies != null) {
      for (Element dependency : dependencies.elements("dependency")) {
        if (relevantDependency(dependency)) {
          result.add(createDependency(dependency, propertiesMap));
        }
      }
    }
    return result;
  }

  private static boolean relevantDependency(Element dependency) {
    Element scope = dependency.element("scope");
    if (scope == null) {
      return true;
    }
    String scopeText = scope.getTextTrim();
    if ("compile".equals(scopeText) || "runtime".equals(scopeText)) {
      return true;
    }
    return false;
  }

  private static Dependency createDependency(Element dependency, Map<String, String> properties) {

    String groupId = dependency.elementText("groupId");
    groupId = properties.getOrDefault(groupId, groupId);

    String artifactId = dependency.elementText("artifactId");
    artifactId = properties.getOrDefault(artifactId, artifactId);

    String version = dependency.elementText("version");
    version = properties.getOrDefault(version, version);

    return new Dependency(groupId, artifactId, version);
  }
}
