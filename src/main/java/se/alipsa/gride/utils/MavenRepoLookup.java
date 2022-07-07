package se.alipsa.gride.utils;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenRepoLookup {

  private static final Logger log = LoggerFactory.getLogger(MavenRepoLookup.class);

  /**
   * @param dependency in the "short form" i.e. groupid:artifact:id:version
   * @param repositoryUrl e.g. https://repo1.maven.org/maven2/
   * @return
   */
  public static Artifact fetchLatestArtifact(String dependency, String repositoryUrl) throws ParserConfigurationException, IOException, SAXException {
    String[] depExp = dependency.split(":");
    String groupId = depExp[0];
    String artifactId = depExp[1];
   return fetchLatestArtifact(groupId, artifactId, repositoryUrl);
  }

  public static String fetchLatestArtifactShortString(String dependency, String repositoryUrl) {
    try {
      Artifact artifact = fetchLatestArtifact(dependency, repositoryUrl);
      return toShortDependency(artifact.getGroupId(),artifact.getArtifactId(), artifact.getVersion());
    } catch (ParserConfigurationException | IOException | SAXException e) {
      return dependency;
    }
  }

  public static Artifact fetchLatestArtifact(String groupId, String artifactId, String repositoryUrl) throws ParserConfigurationException, IOException, SAXException {
    String url = metaDataUrl(groupId,artifactId, repositoryUrl);

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(url);
    Element versioning = (Element)doc.getDocumentElement().getElementsByTagName("versioning").item(0);
    Element release = (Element)versioning.getElementsByTagName("release").item(0);
    String version = release.getTextContent();

    return new DefaultArtifact(groupId, artifactId, "jar", version);
  }

  public static List<String> fetchVersions(String groupId, String artifactId, String repositoryUrl) throws ParserConfigurationException, IOException, SAXException {
    String url = metaDataUrl(groupId,artifactId, repositoryUrl);

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(url);
    Element versioning = (Element)doc.getDocumentElement().getElementsByTagName("versioning").item(0);
    Element versions = (Element)versioning.getElementsByTagName("versions").item(0);
    NodeList nodeList = versions.getElementsByTagName("version");
    List<String> versionList = new ArrayList<>(nodeList.getLength());
    for (int i = 0; i < nodeList.getLength(); i++) {
      var node = (Element)nodeList.item(i);
      versionList.add(node.getTextContent());
    }

    return versionList;
  }

  public static String metaDataUrl(String groupId, String artifactId, String repositoryUrl) {
    String groupUrlPart = groupId.replace('.', '/') + "/";
    return repositoryUrl + groupUrlPart + artifactId + "/maven-metadata.xml";
  }

  public static String toShortDependency(String groupId, String artifactId, String version) {
    return groupId + ":" + artifactId + ":" + version;
  }
}
