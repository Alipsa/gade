package se.alipsa.grade.utils.git;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

public class SshTransportConfigCallback  implements TransportConfigCallback {

  private final SshSessionFactory sshSessionFactory;

  public SshTransportConfigCallback(String url) {
    sshSessionFactory = new GradeSshSessionFactory(url);
  }

  @Override
  public void configure(Transport transport) {
    SshTransport sshTransport = (SshTransport) transport;
    sshTransport.setSshSessionFactory(sshSessionFactory);
  }
}
