package se.alipsa.gade.utils.m2;

import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.alipsa.gade.model.Dependency;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.MavenRepoLookup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashSet;
import java.util.Set;


public class DependencyResolver {

  private static final Logger log = LogManager.getLogger();
  public static final URL CENTRAL_MAVEN_REPOSITORY;
  static {
    try {
      CENTRAL_MAVEN_REPOSITORY = new URL("https://repo1.maven.org/maven2/");
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
  private final Set<URL> remoteRepositories;
  private GroovyClassLoader classLoader = null;

  public DependencyResolver() {
    remoteRepositories = new LinkedHashSet<>();
    remoteRepositories.add(CENTRAL_MAVEN_REPOSITORY);
  }

  public DependencyResolver(GroovyClassLoader classLoader) {
    this();
    this.classLoader = classLoader;
  }

  public void addDependency(String groupId, String artifactId, String version) throws ResolvingException {
    File artifact = resolve(groupId, artifactId, version);
    if (classLoader == null) {
      throw new ResolvingException("You must add a GroovyClassloader before adding dependencies");
    }
    try {
      classLoader.addURL(artifact.toURI().toURL());
    } catch (MalformedURLException e) {
      throw new ResolvingException("Failed to convert the downloaded file to a URL", e);
    }
  }

  public File resolve(String groupId, String artifactId, String version) {
    return resolve(new Dependency(groupId, artifactId, version));
  }

  public File resolve(String dependendency) {
    return resolve(new Dependency(dependendency));
  }

  private File resolve(Dependency dependency) {
    for (URL repoUrl : remoteRepositories) {
      String url = MavenRepoLookup.artifactUrl(dependency, repoUrl.toExternalForm());
      File localFile = null;
      try {
        localFile = download(url);
      } catch (IOException e) {
        log.info("Failed to download from {}", url, e);
      }
      if (localFile != null) {
        return localFile;
      }
    }
    return null;
  }

  private File download(String urlString) throws IOException {
    URL url = new URL(urlString);
    File localFilename = new File(localStorage(), url.getFile());
    try (ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
         FileOutputStream fileOutputStream = new FileOutputStream(localFilename);
         FileChannel fileChannel = fileOutputStream.getChannel()) {

      fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    }
    if (localFilename.exists()) {
      return localFilename;
    }
    return null;
  }

  File localStorage() {
    return new File(FileUtils.getUserHome(), ".gade/repo");
  }

}
