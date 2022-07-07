package se.alipsa.gride.utils;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class MavenRepoLookup {

  private static final Logger log = LoggerFactory.getLogger(MavenRepoLookup.class);

  /**
   * @param dependency in the "short form" i.e. groupid:artifact:id:version
   * @param repositoryUrl e.g. https://repo1.maven.org/maven2/
   * @return
   */
  public static Artifact getLatestArtifact(String dependency, String repositoryUrl) throws ParserConfigurationException, IOException, SAXException {
    String[] depExp = dependency.split(":");
    String groupId = depExp[0];
    String artifactId = depExp[1];
   return getLatestArtifact(groupId, artifactId, repositoryUrl);
  }

  public static String getLatestArtifactShortString(String dependency, String repositoryUrl) {
    try {
      Artifact artifact = getLatestArtifact(dependency, repositoryUrl);
      return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    } catch (ParserConfigurationException | IOException | SAXException e) {
      return dependency;
    }
  }

  public static Artifact getLatestArtifact(String groupId, String artifactId, String repositoryUrl) throws ParserConfigurationException, IOException, SAXException {
    String url = metaDataUrl(groupId,artifactId, repositoryUrl);

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(url);
    Element versioning = (Element)doc.getDocumentElement().getElementsByTagName("versioning").item(0);
    Element release = (Element)versioning.getElementsByTagName("release").item(0);
    String version = release.getTextContent();

    return new DefaultArtifact(groupId, artifactId, "jar", version);
  }

  public static String metaDataUrl(String groupId, String artifactId, String repositoryUrl) {
    String groupUrlPart = groupId.replace('.', '/') + "/";
    return repositoryUrl + groupUrlPart + artifactId + "/maven-metadata.xml";
  }
}
