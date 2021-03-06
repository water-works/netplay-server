package netplayServer;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.Maps;

import io.grpc.stub.StreamObserver;
import netplayprotos.NetPlayServerServiceGrpc.NetPlayServerService;
import netplayprotos.NetplayServiceProto.IncomingEventPB;
import netplayprotos.NetplayServiceProto.MakeConsoleRequestPB;
import netplayprotos.NetplayServiceProto.MakeConsoleResponsePB;
import netplayprotos.NetplayServiceProto.MakeConsoleResponsePB.Status;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;
import netplayprotos.NetplayServiceProto.PingPB;
import netplayprotos.NetplayServiceProto.PlugControllerRequestPB;
import netplayprotos.NetplayServiceProto.PlugControllerResponsePB;
import netplayprotos.NetplayServiceProto.Port;
import netplayprotos.NetplayServiceProto.ShutDownServerRequestPB;
import netplayprotos.NetplayServiceProto.ShutDownServerResponsePB;
import netplayprotos.NetplayServiceProto.StartGameRequestPB;
import netplayprotos.NetplayServiceProto.StartGameResponsePB;

/**
 * The main server, which implements the grpc interface.
 * 
 * This class should contain as little functionality as possible and pass off logic to other
 * classes.
 *
 */
public class Server implements NetPlayServerService {

  private Log log = LogFactory.getLog(Server.class);

  private Map<Long, Console> consoleMap;
  private final boolean testMode;

  public Server(boolean testMode) {
    consoleMap = Maps.newConcurrentMap();
    this.testMode = testMode;
  }

  @Override
  public void ping(PingPB request, StreamObserver<PingPB> responseObserver) {
    responseObserver.onNext(request);
    responseObserver.onCompleted();
  }

  private int numConsolesCreated = 0;

  @Override
  public void makeConsole(MakeConsoleRequestPB request,
      StreamObserver<MakeConsoleResponsePB> responseObserver) {
    log.debug("Received makeConsole request: " + request.toString());

    if (testMode && numConsolesCreated >= 10) {
      throw new IllegalStateException(
          "Server running in test mode created a client with ID greater than 10");
    }

    Console newConsole = new Console(this);
    numConsolesCreated++;

    long id = newConsole.getId();
    consoleMap.put(id, newConsole);
    responseObserver.onNext(createMakeConsoleResponse(id));
    responseObserver.onCompleted();
  }

  private MakeConsoleResponsePB createMakeConsoleResponse(long id) {
    if (id > 0) {
      return MakeConsoleResponsePB.newBuilder().setConsoleId(id).setStatus(Status.SUCCESS).build();
    } else {
      return MakeConsoleResponsePB.newBuilder().setConsoleId(id)
          .setStatus(Status.UNSPECIFIED_FAILURE).build();
    }
  }

  @Override
  public void plugController(PlugControllerRequestPB request,
      StreamObserver<PlugControllerResponsePB> responseObserver) {
    if (request.getRequestedPort1() == Port.UNKNOWN && request.getRequestedPort2() == Port.UNKNOWN
        && request.getRequestedPort3() == Port.UNKNOWN
        && request.getRequestedPort4() == Port.UNKNOWN) {
      responseObserver.onNext(PlugControllerResponsePB.newBuilder()
          .setStatus(PlugControllerResponsePB.Status.NO_PORTS_REQUESTED)
          .setConsoleId(request.getConsoleId()).build());
      responseObserver.onCompleted();
      return;
    }
    if (!consoleMap.containsKey(request.getConsoleId())) {
      PlugControllerResponsePB resp = PlugControllerResponsePB.newBuilder()
          .setStatus(PlugControllerResponsePB.Status.NO_SUCH_CONSOLE)
          .setConsoleId(request.getConsoleId()).build();
      responseObserver.onNext(resp);
      responseObserver.onCompleted();
      return;
    }
    Console console = consoleMap.get(request.getConsoleId());
    Client client = null;
    try {
      client = console.tryAddPlayers(request.getDelayFrames(), request.getRequestedPort1(),
          request.getRequestedPort2(), request.getRequestedPort3(), request.getRequestedPort4());
    } catch (PlugRequestException e) {
      responseObserver.onNext(PlugControllerResponsePB.newBuilder()
          .setStatus(PlugControllerResponsePB.Status.PORT_REQUEST_REJECTED)
          .addAllPortRejections(e.getRejections()).build());
      responseObserver.onCompleted();
      return;
    }
    long clientId = client.getId();
    PlugControllerResponsePB resp = PlugControllerResponsePB.newBuilder()
        .setStatus(PlugControllerResponsePB.Status.SUCCESS).addAllPort(client.getPorts())
        .setClientId(clientId).setConsoleId(console.getId()).build();
    responseObserver.onNext(resp);
    responseObserver.onCompleted();
  }

  /**
   * Request from a client to start the game. Note that all connected clients must be ready before
   * game begins.
   */
  @Override
  public void startGame(StartGameRequestPB request,
      StreamObserver<StartGameResponsePB> responseObserver) {
    if (!consoleMap.containsKey(request.getConsoleId())) {
      StartGameResponsePB resp = StartGameResponsePB.newBuilder()
          .setStatus(StartGameResponsePB.Status.NO_SUCH_CONSOLE).build();
      responseObserver.onNext(resp);
      responseObserver.onCompleted();
      return;
    }
    Console console = consoleMap.get(request.getConsoleId());
    if (!console.verifyClientsReady()) {
      StartGameResponsePB resp = StartGameResponsePB.newBuilder()
          .setStatus(StartGameResponsePB.Status.CLIENTS_NOT_READY).build();
      responseObserver.onNext(resp);
      responseObserver.onCompleted();
      return;
    }
    console.broadcastStartGame();
    StartGameResponsePB resp = StartGameResponsePB.newBuilder().setConsoleId(console.getId())
        .setStatus(StartGameResponsePB.Status.SUCCESS).build();
    responseObserver.onNext(resp);
    responseObserver.onCompleted();
  }

  @Override
  public StreamObserver<OutgoingEventPB> sendEvent(
      StreamObserver<IncomingEventPB> responseObserver) {
    return new ClientHandoffStreamObserver<OutgoingEventPB>(responseObserver, consoleMap);
  }

  public void tearDownConsole(long consoleId) {
    consoleMap.remove(consoleId);
  }

  private io.grpc.Server serverImpl;

  /**
   * Gives this server a handle to the server implementation wrapper that wraps it.
   */
  public void setServerImpl(io.grpc.Server serverImpl) {
    this.serverImpl = serverImpl;
  }

  /**
   * Requests that the server shut itself down. The server will only shut itself down if it is in
   * test mode and if it has had a server handle passed to it via {@link setServerImpl}.
   */
  @Override
  public void shutDownServer(ShutDownServerRequestPB request,
      StreamObserver<ShutDownServerResponsePB> responseObserver) {
    if (testMode && serverImpl != null) {
      ShutDownServerResponsePB resp =
          ShutDownServerResponsePB.newBuilder().setServerWillDie(true).build();
      responseObserver.onNext(resp);
      responseObserver.onCompleted();
      log.info("Shutting down test server.");
      serverImpl.shutdown();
    } else {
      ShutDownServerResponsePB resp =
          ShutDownServerResponsePB.newBuilder().setServerWillDie(false).build();
      responseObserver.onNext(resp);
      responseObserver.onCompleted();
    }
  }
}
