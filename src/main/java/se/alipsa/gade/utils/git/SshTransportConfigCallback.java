package se.alipsa.gade.utils.git;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

import java.io.File;

public class SshTransportConfigCallback  implements TransportConfigCallback {

  private final SshSessionFactory sshSessionFactory;

  public SshTransportConfigCallback(String url) {
    sshSessionFactory = new GadeSshSessionFactory(url);
  }

  public SshTransportConfigCallback(String url, File publicKeyFile, File privateKeyFile) {
    sshSessionFactory = new GadeSshSessionFactory(url, publicKeyFile, privateKeyFile);
  }

  @Override
  public void configure(Transport transport) {
    SshTransport sshTransport = (SshTransport) transport;
    sshTransport.setSshSessionFactory(sshSessionFactory);
  }
}
