package netplayServer;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.grpc.stub.StreamObserver;
import netplayprotos.NetplayServiceProto.IncomingEventPB;
import netplayprotos.NetplayServiceProto.InvalidDataPB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;

/**
 * Implementation of an observer that handles incoming messages to the server. We unfortunately do
 * not know what client this is for until after we receive a request.
 * 
 * @param <T> The proto message to observe
 */
public class ClientHandoffStreamObserver<T> implements StreamObserver<OutgoingEventPB> {

  private Log log = LogFactory.getLog(Server.class);

  private Map<Long, Console> consoleMap;
  private StreamObserver<IncomingEventPB> responseObserver;

  public ClientHandoffStreamObserver(StreamObserver<IncomingEventPB> responseObserver,
      Map<Long, Console> consoleMap) {
    this.consoleMap = consoleMap;
    this.responseObserver = responseObserver;
  }

  private Client client;

  @Override
  public void onNext(OutgoingEventPB value) {
    if (client != null) {
      client.onNext(value);
    } else if (value.hasClientReady()) {
      Console console = consoleMap.get(value.getClientReady().getConsoleId());
      if (console != null) {
        this.client = console.getClientById(value.getClientReady().getClientId());
        client.setStreamObserver(responseObserver);
        client.setReady();
      } else {
        IncomingEventPB invalidEvent =
            IncomingEventPB.newBuilder()
                .addInvalidData(
                    InvalidDataPB.newBuilder().setStatus(InvalidDataPB.Status.INVALID_CONSOLE))
                .build();
        responseObserver.onNext(invalidEvent);
        responseObserver.onCompleted();
      }
    } else {
      log.warn(
          String.format("Stream message sent without client ready first - ignoring: %s", value));
    }
  }

  @Override
  public void onError(Throwable t) {
    if (client == null) {
      log.warn(String.format("Error with no client set"), t);
    } else {
      client.onError(t);
    }
  }

  @Override
  public void onCompleted() {
    if (client == null) {
      log.warn(String.format("Completed with no client set"));
    } else {
      client.onCompleted();
    }
  }

}
