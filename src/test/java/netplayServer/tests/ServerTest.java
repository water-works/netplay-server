package netplayServer.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Queue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.Queues;

import io.grpc.stub.StreamObserver;
import netplayServer.Server;
import netplayServer.utils.DummyGrpcServer;
import netplayprotos.NetplayServiceProto.ClientReadyPB;
import netplayprotos.NetplayServiceProto.IncomingEventPB;
import netplayprotos.NetplayServiceProto.MakeConsoleRequestPB;
import netplayprotos.NetplayServiceProto.MakeConsoleResponsePB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;
import netplayprotos.NetplayServiceProto.PingPB;
import netplayprotos.NetplayServiceProto.PlugControllerRequestPB;
import netplayprotos.NetplayServiceProto.PlugControllerResponsePB;
import netplayprotos.NetplayServiceProto.PlugControllerResponsePB.Status;
import netplayprotos.NetplayServiceProto.Port;
import netplayprotos.NetplayServiceProto.ShutDownServerRequestPB;
import netplayprotos.NetplayServiceProto.ShutDownServerResponsePB;
import netplayprotos.NetplayServiceProto.StartGameRequestPB;
import netplayprotos.NetplayServiceProto.StartGameResponsePB;

@RunWith(JUnit4.class)
public class ServerTest {

  private Server server;
  ResponseObserver<MakeConsoleResponsePB> makeConsoleObserver;
  ResponseObserver<PlugControllerResponsePB> plugControllerObserver;
  ResponseObserver<StartGameResponsePB> startGameObserver;

  MakeConsoleRequestPB makeConsoleReq = MakeConsoleRequestPB.newBuilder().build();

  @Before
  public void setUp() {
    server = new Server(true);
    makeConsoleObserver = new ResponseObserver<>();
    plugControllerObserver = new ResponseObserver<>();
    startGameObserver = new ResponseObserver<>();
  }

  /**
   * Requests MakeConsole and asserts that the console was successfully created.
   * 
   * @return the console ID of the created console
   */
  public long makeDefaultConsole() {
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    MakeConsoleResponsePB resp = makeConsoleObserver.getNextValue();
    assertEquals(null, makeConsoleObserver.getNextValue());
    assertTrue(makeConsoleObserver.completed);

    assertNotEquals(resp, null);
    assertEquals(MakeConsoleResponsePB.Status.SUCCESS, resp.getStatus());
    long id = resp.getConsoleId();
    assertTrue(id > 0);

    return id;
  }

  @Test
  public void testPingWriter() {
    ResponseObserver<PingPB> pingObserver = new ResponseObserver<>();
    server.ping(PingPB.newBuilder().build(), pingObserver);
    assertNotEquals(null, pingObserver.getNextValue());
  }

  @Test
  public void testMakeConsole() {
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    MakeConsoleResponsePB resp = makeConsoleObserver.getNextValue();
    long id1 = resp.getConsoleId();

    makeConsoleObserver = new ResponseObserver<>();
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    resp = makeConsoleObserver.getNextValue();
    assertNotEquals(id1, resp.getConsoleId());
  }

  @Test
  public void testMakeConsoleTooManyConsolesOnDebugServer() {
    for (int i = 0; i < 10; ++i) {
      makeConsoleObserver = new ResponseObserver<>();
      server.makeConsole(makeConsoleReq, makeConsoleObserver);
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS,
          makeConsoleObserver.getNextValue().getStatus());
    }
    try {
      makeConsoleObserver = new ResponseObserver<>();
      server.makeConsole(makeConsoleReq, makeConsoleObserver);
      fail("Expected that the test server will refuse to create more than ten consoles");
    } catch (IllegalStateException e) {
    }
  }

  @Test
  public void testMakeConsoleNonDebugServerCreatesPlentyOfConsoles() {
    Server nonDebugServer = new Server(false);
    for (int i = 0; i < 100; ++i) {
      makeConsoleObserver = new ResponseObserver<>();
      nonDebugServer.makeConsole(makeConsoleReq, makeConsoleObserver);
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS,
          makeConsoleObserver.getNextValue().getStatus());
    }
  }

  @Test
  public void testPlugWithNoConsole() {
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(1)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    PlugControllerResponsePB resp = plugControllerObserver.getNextValue();
    assertEquals(Status.UNSPECIFIED_FAILURE, resp.getStatus());
  }

  @Test
  public void testMakeAndJoinConsole() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    PlugControllerResponsePB resp = plugControllerObserver.getNextValue();
    assertEquals(id, resp.getConsoleId());
  }

  @Test
  public void testMakeAndJoinConsole_TwoPlayers() {
    long id = makeDefaultConsole();

    // Plug in two controllers in two requests. The ports should not be
    // equal.

    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    PlugControllerResponsePB resp = plugControllerObserver.getNextValue();
    assertEquals(id, resp.getConsoleId());
    assertEquals(1, resp.getPortList().size());
    Port port1 = resp.getPortList().get(0);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_ANY).build();

    plugControllerObserver = new ResponseObserver<>();
    server.plugController(pReq, plugControllerObserver);
    resp = plugControllerObserver.getNextValue();
    assertEquals(id, resp.getConsoleId());
    assertEquals(1, resp.getPortList().size());
    Port port2 = resp.getPortList().get(0);
    assertNotEquals(port1, port2);
  }

  @Test
  public void testMakeConsoleNoPortsRequested() {
    long id = makeDefaultConsole();

    PlugControllerRequestPB pReq =
        PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2).build();
    server.plugController(pReq, plugControllerObserver);
    PlugControllerResponsePB resp = plugControllerObserver.getNextValue();
    assertNotEquals(null, resp);
    assertEquals(id, resp.getConsoleId());
    assertEquals(PlugControllerResponsePB.Status.NO_PORTS_REQUESTED, resp.getStatus());
  }

  /**
   * Tests that multiple players from a single client can plug properly.
   */
  @Test
  public void testMultiplePlayersOneClient() {
    ResponseObserver<PlugControllerResponsePB> plugControllerObserver1 = new ResponseObserver<>();
    ResponseObserver<PlugControllerResponsePB> plugControllerObserver2 = new ResponseObserver<>();

    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).setRequestedPort2(Port.PORT_2)
        .setRequestedPort3(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver1);

    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver2);

    PlugControllerResponsePB resp = plugControllerObserver1.getNextValue();
    assertEquals(3, resp.getPortCount());
    assertTrue(resp.getPortList().contains(Port.PORT_2));

    resp = plugControllerObserver2.getNextValue();
    assertEquals(1, resp.getPortCount());
  }

  /**
   * Tests the normal flow for two players joining and then starting a game.
   */
  @Test
  public void testReadyAndStartGame() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    long clientId1 = plugControllerObserver.getNextValue().getClientId();

    plugControllerObserver = new ResponseObserver<>();
    server.plugController(pReq, plugControllerObserver);
    long clientId2 = plugControllerObserver.getNextValue().getClientId();
    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    ResponseObserver<IncomingEventPB> eventObserver1 = new ResponseObserver<>();

    // Begin the streams
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);

    readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId2).setConsoleId(id).build())
        .build();
    ResponseObserver<IncomingEventPB> eventObserver2 = new ResponseObserver<>();
    StreamObserver<OutgoingEventPB> outgoingSender2 = server.sendEvent(eventObserver2);
    outgoingSender2.onNext(readyPb);

    StartGameRequestPB startRequest = StartGameRequestPB.newBuilder().setConsoleId(id).build();
    server.startGame(startRequest, startGameObserver);

    IncomingEventPB startResp1 = eventObserver1.getNextValue();
    IncomingEventPB startResp2 = eventObserver2.getNextValue();
    assertTrue(startResp1.hasStartGame());
    assertTrue(startResp2.hasStartGame());
    assertEquals(startResp1.getStartGame().getConsoleId(), id);
    assertEquals(2, startResp1.getStartGame().getConnectedPortsCount());
    assertEquals(2, startResp2.getStartGame().getConnectedPortsCount());
  }

  /**
   * Test when start game has been called but not all players are ready.
   */
  @Test
  public void testNotAllPlayersReady() {
    long id = makeDefaultConsole();

    // Plug controller 1
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_1).build();
    server.plugController(pReq, plugControllerObserver);
    PlugControllerResponsePB plugControllerResponse = plugControllerObserver.getNextValue();
    assertEquals(PlugControllerResponsePB.Status.SUCCESS, plugControllerResponse.getStatus());

    long clientId1 = plugControllerResponse.getClientId();

    // Plug controller 2
    plugControllerObserver = new ResponseObserver<>();
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_2).build();
    server.plugController(pReq, plugControllerObserver);
    plugControllerResponse = plugControllerObserver.getNextValue();
    assertEquals(PlugControllerResponsePB.Status.SUCCESS, plugControllerResponse.getStatus());

    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    ResponseObserver<IncomingEventPB> eventObserver1 = new ResponseObserver<>();

    // Send ready only for player 1
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);
    ResponseObserver<IncomingEventPB> eventObserver2 = new ResponseObserver<>();
    server.sendEvent(eventObserver2);

    StartGameRequestPB startRequest = StartGameRequestPB.newBuilder().setConsoleId(id).build();
    server.startGame(startRequest, startGameObserver);
    assertEquals(StartGameResponsePB.Status.UNSPECIFIED_FAILURE,
        startGameObserver.getNextValue().getStatus());

    assertEquals(null, eventObserver1.getNextValue());
    assertEquals(null, eventObserver2.getNextValue());
  }

  @Test
  public void testSendKeypresses() {
    long id = makeDefaultConsole();

    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_1).build();
    server.plugController(pReq, plugControllerObserver);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_2).build();

    long clientId1 = plugControllerObserver.getNextValue().getClientId();

    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    ResponseObserver<IncomingEventPB> eventObserver1 = new ResponseObserver<>();

    // Send ready only for player 1
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);
    ResponseObserver<IncomingEventPB> eventObserver2 = new ResponseObserver<>();
    server.sendEvent(eventObserver2);

  }

  @Test
  public void testTestServerWithMoreThanTenConsoles() {
    Server testServer = new Server(true);
    Server prodServer = new Server(false);

    for (int i = 0; i < 10; ++i) {
      makeConsoleObserver = new ResponseObserver<>();
      testServer.makeConsole(makeConsoleReq, makeConsoleObserver);
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS,
          makeConsoleObserver.getNextValue().getStatus());

      makeConsoleObserver = new ResponseObserver<>();
      prodServer.makeConsole(makeConsoleReq, makeConsoleObserver);
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS,
          makeConsoleObserver.getNextValue().getStatus());
    }

    // The test server should fail to create a new console.
    try {
      makeConsoleObserver = new ResponseObserver<>();
      testServer.makeConsole(makeConsoleReq, makeConsoleObserver);
      fail();
    } catch (IllegalStateException e) {
    }

    // The prod server should not fail
    makeConsoleObserver = new ResponseObserver<>();
    prodServer.makeConsole(makeConsoleReq, makeConsoleObserver);
    assertEquals(MakeConsoleResponsePB.Status.SUCCESS,
        makeConsoleObserver.getNextValue().getStatus());
  }

  @Test
  public void testShutdownTestServer() {
    // The server implementation was not passed.
    ShutDownServerRequestPB req = ShutDownServerRequestPB.newBuilder().build();
    ResponseObserver<ShutDownServerResponsePB> shutdownObserver = new ResponseObserver<>();
    server.shutDownServer(req, shutdownObserver);
    ShutDownServerResponsePB resp = shutdownObserver.getNextValue();
    assertNotEquals(null, resp);
    assertTrue(!resp.getServerWillDie());

    // The server implementation was passed
    server.setServerImpl(new DummyGrpcServer());
    shutdownObserver = new ResponseObserver<>();
    server.shutDownServer(req, shutdownObserver);
    resp = shutdownObserver.getNextValue();
    assertNotEquals(null, resp);
    assertTrue(resp.getServerWillDie());
  }

  @Test
  public void testShutdownProdServer() {
    server = new Server(false);
    server.setServerImpl(new DummyGrpcServer());
    ShutDownServerRequestPB req = ShutDownServerRequestPB.newBuilder().build();
    ResponseObserver<ShutDownServerResponsePB> shutdownObserver = new ResponseObserver<>();
    server.shutDownServer(req, shutdownObserver);
    ShutDownServerResponsePB resp = shutdownObserver.getNextValue();
    assertNotEquals(null, resp);
    assertTrue(!resp.getServerWillDie());
  }

  private class ResponseObserver<T> implements StreamObserver<T> {

    private Queue<T> respQueue = Queues.newConcurrentLinkedQueue();
    public Throwable lastThrowable;
    public boolean completed = false;

    @Override
    public void onNext(T value) {
      assertTrue(!completed);
      respQueue.add(value);
    }

    @Override
    public void onError(Throwable t) {
      lastThrowable = t;
    }

    @Override
    public void onCompleted() {
      assertTrue(!completed);
      completed = true;
    }

    /*
     * Return null if there is no next value.
     */
    public T getNextValue() {
      if (respQueue.isEmpty()) {
        return null;
      }
      return respQueue.remove();
    }

    /**
     * Flushes all messages from the queue.
     */
    public void clear() {
      respQueue.clear();
    }
  }

}
