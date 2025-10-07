package se.alipsa.gade.utils.git;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.util.FS;
import se.alipsa.gade.utils.FileUtils;
import se.alipsa.gade.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/** If the DefaultSshSessionFactory is not working, we can roll our own and
 * insert it into the command (like below for a pullCommand):
 * <code>
 * SshSessionFactory sshSessionFactory = new GadeSshSessionFactory();
 *       pullCommand.setTransportConfigCallback( new TransportConfigCallback() {
 *         public void configure( Transport transport ) {
 *           SshTransport sshTransport = ( SshTransport )transport;
 *           sshTransport.setSshSessionFactory( sshSessionFactory );
 *         }
 *       } );
 * </code>
 */
public class GadeSshSessionFactory extends JschConfigSessionFactory {

  private static final Logger log = LogManager.getLogger(GadeSshSessionFactory.class);

  String url;
  File publicKeyFile;
  File privateKeyFile;

  public GadeSshSessionFactory(String url) {
    log.trace("GadeSshSessionFactory for {}", url);
    this.url = url;
  }

  public GadeSshSessionFactory(String url, File publicKeyFile, File privateKeyFile) {
    this(url);
    this.publicKeyFile = publicKeyFile;
    this.privateKeyFile = privateKeyFile;
  }

  @Override
  protected void configure(OpenSshConfig.Host hc, Session session) {
    session.setConfig("StrictHostKeyChecking", "no");
  }

  @Override
  public synchronized RemoteSession getSession(URIish uri,
                                               CredentialsProvider credentialsProvider, FS fs, int tms)
      throws TransportException {
    try {
      var usrPwd = GitUtils.findUserNamePassword(url);
      if (usrPwd != null && StringUtils.isBlank(uri.getPass())) {
        uri.setPass(usrPwd.get("password"));
      }
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
      throw new TransportException("Failed to get password info", e);
    }
    log.info("Get session: uri = {}", uri);
    return super.getSession(uri, credentialsProvider, fs, tms);
  }

  @Override
  protected JSch createDefaultJSch(FS fs) throws JSchException {
    File sshDir = new File(FileUtils.getUserHome(), ".ssh");
    File knownHosts = new File(sshDir, "known_hosts");
    JSch defaultJSch = super.createDefaultJSch(fs);
    if (knownHosts.exists()) {
      defaultJSch.setKnownHosts(knownHosts.getAbsolutePath());
    }
    String privateKey;
    String publicKey;
    if (privateKeyFile == null) {
      privateKey = System.getProperty("user.home") + "/.ssh/id_rsa";
      publicKey = System.getProperty("user.home") + "/.ssh/id_rsa.pub";
      if (!new File(publicKey).exists()) {
        File dir = new File(System.getProperty("user.home") + "/.ssh");
        var files = FileUtils.findFilesWithExt(dir, ".pub");
        if (files.size() == 0) {
          throw new JSchException("~/.ssh/id_rsa.pub does not exist and no other public keys fund");
        }
        publicKey = files.get(0).getAbsolutePath();
        privateKey = publicKey.substring(0, publicKey.length() - ".pub".length());
      }
    } else {
      publicKey = publicKeyFile.getAbsolutePath();
      privateKey = privateKeyFile.getAbsolutePath();
    }
    log.info("Using publicKey {}, and privateKey {}", publicKey, privateKey);
    KeyPair keyPair = KeyPair.load(defaultJSch, privateKey, publicKey);
    if (keyPair.isEncrypted()) {
      try {
        var usrPwd = GitUtils.findUserNamePassword(url);
        if (usrPwd != null) {
          String pwd = usrPwd.get("password");
          //defaultJSch.addIdentity(privateKey, pwd.getBytes());
          defaultJSch.addIdentity(privateKey, pwd);
        } else {
          log.info("Password required but no password found for {}", url);
          throw new JSchException("Password required but no password found for " + url + " in " + GitUtils.getCredentialsFile());
        }
      } catch (IOException | URISyntaxException e) {
        log.warn("Failed to get stored credentials", e);
        throw new JSchException("Failed to get stored credentials", e);
      }
    }
    return defaultJSch;
  }
}
