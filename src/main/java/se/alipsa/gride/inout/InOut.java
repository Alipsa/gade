package se.alipsa.gride.inout;

import se.alipsa.gride.environment.connections.ConnectionInfo;

public interface InOut {

  /** Return a connections for the name defined in Ride */
  ConnectionInfo connection(String name);

}
