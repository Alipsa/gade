package se.alipsa.gade.utils.m2;

import groovy.lang.GroovyClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import se.alipsa.gade.interaction.UrlUtil;
import se.alipsa.gade.model.Dependency;
import se.alipsa.gade.utils.MavenRepoLookup;
import se.alipsa.mavenutils.DependenciesResolveException;
import se.alipsa.mavenutils.MavenUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;


public class DependencyResolver {

  private static final Logger log = LogManager.getLogger();

  private final UrlUtil urlUtil = new UrlUtil();

  private GroovyClassLoader classLoader = null;

  public DependencyResolver() {
  }

  public DependencyResolver(GroovyClassLoader classLoader) {
    this();
    this.classLoader = classLoader;
  }

  public void addDependency(String groupId, String artifactId, String version) throws ResolvingException {
    Dependency dep = new Dependency(groupId, artifactId, version);
    addDependency(dep);
  }

  public void addDependency(String dependency) throws ResolvingException {
    Dependency dep = new Dependency(dependency);
    addDependency(dep);
  }

  private void addDependency(Dependency dep) throws ResolvingException {

    List<File> artifacts = resolve(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    if (classLoader == null) {
      log.error("You must add a GroovyClassloader before adding dependencies");
      throw new ResolvingException("You must add a GroovyClassloader before adding dependencies");
    }
    try {
      for (File artifact : artifacts) {
        classLoader.addURL(artifact.toURI().toURL());
      }
    } catch (MalformedURLException e) {
      log.warn("Failed to convert the downloaded file to a URL", e);
      throw new ResolvingException("Failed to convert the downloaded file to a URL", e);
    }
  }

  public List<File> resolve(String groupId, String artifactId, String version) throws ResolvingException {
    List<File> jarFiles = new ArrayList<>();
    resolve(new Dependency(groupId, artifactId, version), jarFiles);
    return jarFiles;
  }

  public List<File> resolve(String dependency) throws ResolvingException {
    List<File> jarFiles = new ArrayList<>();
    resolve(new Dependency(dependency), jarFiles);
    return jarFiles;
  }

  private void resolve(Dependency dependency, List<File> jarFiles) throws ResolvingException {
    MavenUtils mavenUtils = new MavenUtils();
    File artifact;
    try {
      artifact = mavenUtils.resolveArtifact(
          dependency.getGroupId(),
          dependency.getArtifactId(),
          null,
          "jar",
          dependency.getVersion()
      );
    } catch (SettingsBuildingException | ArtifactResolutionException e) {
      log.warn("Failed to resolve artifact " + dependency);
      throw new ResolvingException("Failed to resolve artifact " + dependency);
    }
    jarFiles.add(artifact);
    File pomFile = new File(artifact.getParent(), artifact.getName().replace(".jar", ".pom"));
    if (!pomFile.exists()) {
      String url = null;
      for (RemoteRepository remoteRepository : mavenUtils.getRemoteRepositories()) {
        url = MavenRepoLookup.pomUrl(dependency, remoteRepository.getUrl());
        if (urlUtil.exists(url, 10)) {
          break;
        }
      }
      if (url != null) {
        try {
          download(url, pomFile);
        } catch (IOException e) {
          throw new ResolvingException("Failed to resolve pom file for " + dependency, e);
        }
      }
    }
    try {
      for (File file : mavenUtils.resolveDependencies(pomFile)) {
        if (file.getName().toLowerCase().endsWith(".jar")) {
          jarFiles.add(file);
        }
      }
    } catch (SettingsBuildingException | ModelBuildingException | DependenciesResolveException e) {
      log.warn("Failed to resolve pom file " + pomFile);
      throw new ResolvingException("Failed to resolve pom file " + pomFile);
    }
  }

  private void download(String urlString, File localFilename) throws IOException {
    URL url = new URL(urlString);
    try (
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        FileOutputStream fileOutputStream = new FileOutputStream(localFilename);
        FileChannel fileChannel = fileOutputStream.getChannel()) {
      fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    }
  }
}
