package utils;

import net.jodah.concurrentunit.Waiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.junit.jupiter.api.Test;
import se.alipsa.grade.utils.git.GitUtils;
import se.alipsa.grade.utils.git.SshTransportConfigCallback;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class GitTest {

  private static Logger log = LogManager.getLogger();

  @Test
  public void testGitFetch() throws IOException, URISyntaxException, GitAPIException {
    File curDir = new File(".");
    while (!curDir.getName().equals("grade") && curDir.getParentFile() != null) {
      curDir = curDir.getParentFile();
    }
    File gradeDir = curDir;
    Git git = Git.open(gradeDir);

    String url = git.getRepository().getConfig().getString("remote", "origin", "url");
    log.info("remote origin Url is " + url);
    log.info("Getting credentials");
    SshTransportConfigCallback sshTransportConfigCallback = new SshTransportConfigCallback(url);
    CredentialsProvider credentialsProvider = GitUtils.getStoredCredentials(url);
    log.info("Fetching...");
    FetchCommand fetchCommand = git.fetch()
        .setTransportConfigCallback(sshTransportConfigCallback);
    if (credentialsProvider != null) {
      fetchCommand.setCredentialsProvider(credentialsProvider);
    }
    FetchResult fetchResult = fetchCommand.call();
    log.info("Messages: {}", fetchResult.getMessages());
    log.info("Tracking refs: {}", fetchResult.getTrackingRefUpdates().stream().map(r -> r.toString()).collect(Collectors.joining(", ")));
  }

  @Test
  public void testFetchInThread() throws IOException, InterruptedException, TimeoutException {
    final Waiter waiter = new Waiter();

    File curDir = new File(".");
    while (!curDir.getName().equals("grade") && curDir.getParentFile() != null) {
      curDir = curDir.getParentFile();
    }
    File gradeDir = curDir;
    Git git = Git.open(gradeDir);

    String url = git.getRepository().getConfig().getString("remote", "origin", "url");

    Thread runningThread = new Thread(() -> {
      log.info("Thread call started...");
      try {
        log.info("remote origin Url is " + url);
        log.info("Getting credentials in thread");
        CredentialsProvider credentialsProvider = GitUtils.getStoredCredentials(url);
        log.info("Fetching in thread...");
        SshTransportConfigCallback sshTransportConfigCallback = new SshTransportConfigCallback(url);
        FetchResult fetchResult = git.fetch()
            .setTransportConfigCallback(sshTransportConfigCallback)
            .setCredentialsProvider(credentialsProvider).call();
        log.info("Messages: {}", fetchResult.getMessages());
        log.info("Tracking refs: {}", fetchResult.getTrackingRefUpdates().stream().map(r -> r.toString()).collect(Collectors.joining(", ")));
      } catch (Exception e) {
        // RuntimeExceptions (such as EvalExceptions is not caught so need to wrap all in an exception
        // this way we can get to the original one by extracting the cause from the thrown exception
        log.warn("Exception caught: {}", e.toString());
        waiter.fail(e);
      }
      waiter.resume();
    });
    runningThread.setDaemon(false);
    log.info("Starting thread");
    runningThread.start();
    log.info("Waiting for thread to finish");
    waiter.await(6000);
  }
}
