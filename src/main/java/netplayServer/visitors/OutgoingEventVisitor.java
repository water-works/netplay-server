package netplayServer.visitors;

import netplayprotos.NetplayServiceProto.KeyStatePB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;

public interface OutgoingEventVisitor {
  
  /**
   * Processes incoming events from a client.
   * 
   * Can be used for spectating, logging, etc.
   * @param event
   */
  public void visit(OutgoingEventPB event);
}
