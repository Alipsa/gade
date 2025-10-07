package utils;

import net.jodah.concurrentunit.Waiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.git.GitUtils;
import se.alipsa.gade.utils.git.SshTransportConfigCallback;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class GitTest {

  private static Logger log = LogManager.getLogger(GitTest.class);

  /**
   * If you have a password for your certificate, you need to add credentials to your
   * ~/.git-credentials file for this test to work.
   * The format of the string in ~/.git-credentials is:
   * <pre>
   * git:myPassword@github.com:Alipsa/gade.git
   * </pre>
   */
  @Test
  public void testGitFetch() throws IOException, URISyntaxException, GitAPIException {
    File curDir = new File(".");
    while (!curDir.getName().equals("gade") && curDir.getParentFile() != null) {
      curDir = curDir.getParentFile();
    }
    File gadeDir = curDir;
    var fetchResult = GitUtils.fetch(gadeDir);
    log.info("Messages: {}", fetchResult.getMessages());
    log.info("Tracking refs: {}", fetchResult.getTrackingRefUpdates().stream().map(r -> r.toString()).collect(Collectors.joining(", ")));
  }

  @Test
  public void testFetchInThread() throws IOException, InterruptedException, TimeoutException {
    final Waiter waiter = new Waiter();

    File curDir = new File(".");
    while (!curDir.getName().equals("gade") && curDir.getParentFile() != null) {
      curDir = curDir.getParentFile();
    }
    File gadeDir = curDir;
    Thread runningThread = new Thread(() -> {
      log.info("Thread call started...");

      try {
        log.info("Getting credentials in thread");
        FetchResult fetchResult = GitUtils.fetch(gadeDir);
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
    waiter.await(8000);
  }

  @Test
  public void testGitClone() throws Exception {
    GitServer server = new GitServer();
    server.start(8085);
    File targetDir = Files.createTempDirectory("gitrepo").toFile();
    String url = "http://localhost:8085/TestRepo";
    log.info("Cloning {} to {}", url, targetDir);
    try(var call = Git.cloneRepository()
            .setURI(url)
            .setDirectory(targetDir)
            .call()){
      log.info("Call = {}", call);
    }
    server.stop();
    FileUtils.delete(targetDir);
  }
}
