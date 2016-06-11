package netplayServer.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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

@RunWith(MockitoJUnitRunner.class)
public class ServerTest {

  private Server server;
  @Mock
  StreamObserver<PingPB> pingObserver;
  @Mock
  StreamObserver<MakeConsoleResponsePB> makeConsoleObserver;
  @Mock
  StreamObserver<PlugControllerResponsePB> plugControllerObserver;
  @Mock
  StreamObserver<StartGameResponsePB> startGameObserver;

  ArgumentCaptor<MakeConsoleResponsePB> makeConsoleCaptor;
  ArgumentCaptor<PlugControllerResponsePB> plugControllerCaptor;
  ArgumentCaptor<StartGameResponsePB> startGameCaptor;


  MakeConsoleRequestPB makeConsoleReq = MakeConsoleRequestPB.newBuilder().build();

  @Before
  public void setUp() {
    server = new Server(true); // debug server
    makeConsoleCaptor = ArgumentCaptor.forClass(MakeConsoleResponsePB.class);
    plugControllerCaptor = ArgumentCaptor.forClass(PlugControllerResponsePB.class);
    startGameCaptor = ArgumentCaptor.forClass(StartGameResponsePB.class);
  }

  /**
   * Requests MakeConsole and asserts that the console was successfully created.
   * 
   * @return the console ID of the created console
   */
  public long makeDefaultConsole() {
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    verify(makeConsoleObserver).onNext(makeConsoleCaptor.capture());
    verify(makeConsoleObserver).onCompleted();
    MakeConsoleResponsePB resp = makeConsoleCaptor.getValue();

    assertEquals(MakeConsoleResponsePB.Status.SUCCESS, resp.getStatus());
    long id = resp.getConsoleId();
    assertTrue(id > 0);

    return id;
  }

  @Test
  public void testPingWriter() {
    server.ping(PingPB.newBuilder().build(), pingObserver);
    verify(pingObserver).onNext(any(PingPB.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testMakeConsole() {
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    verify(makeConsoleObserver).onNext(makeConsoleCaptor.capture());
    MakeConsoleResponsePB resp = makeConsoleCaptor.getValue();
    long id1 = resp.getConsoleId();

    reset(makeConsoleObserver);
    server.makeConsole(makeConsoleReq, makeConsoleObserver);
    verify(makeConsoleObserver).onNext(makeConsoleCaptor.capture());
    resp = makeConsoleCaptor.getValue();
    assertNotEquals(id1, resp.getConsoleId());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testMakeConsoleTooManyConsolesOnDebugServer() {
    for (int i = 0; i < 10; ++i) {
      reset(makeConsoleObserver);
      server.makeConsole(makeConsoleReq, makeConsoleObserver);
      verify(makeConsoleObserver).onNext(makeConsoleCaptor.capture());
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS, makeConsoleCaptor.getValue().getStatus());
    }
    try {
      reset(makeConsoleObserver);
      server.makeConsole(makeConsoleReq, makeConsoleObserver);
      fail("Expected that the test server will refuse to create more than ten consoles");
    } catch (IllegalStateException e) {
      // success
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testMakeConsoleNonDebugServerCreatesPlentyOfConsoles() {
    Server nonDebugServer = new Server(false);
    for (int i = 0; i < 100; ++i) {
      reset(makeConsoleObserver);
      nonDebugServer.makeConsole(makeConsoleReq, makeConsoleObserver);
      verify(makeConsoleObserver).onNext(makeConsoleCaptor.capture());
      assertEquals(MakeConsoleResponsePB.Status.SUCCESS, makeConsoleCaptor.getValue().getStatus());
    }
  }

  @Test
  public void testPlugWithNoConsole() {
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(1)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    assertEquals(Status.NO_SUCH_CONSOLE, plugControllerCaptor.getValue().getStatus());
  }

  @Test
  public void testMakeAndJoinConsole() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    assertEquals(id, plugControllerCaptor.getValue().getConsoleId());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testMakeAndJoinConsole_TwoPlayers() {
    long id = makeDefaultConsole();

    // Plug in two controllers in two requests. The ports should not be
    // equal.

    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    PlugControllerResponsePB resp = plugControllerCaptor.getValue();
    assertEquals(id, resp.getConsoleId());
    assertEquals(1, resp.getPortList().size());
    Port port1 = resp.getPortList().get(0);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_ANY).build();

    reset(plugControllerObserver);
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    resp = plugControllerCaptor.getValue();
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
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    PlugControllerResponsePB resp = plugControllerCaptor.getValue();
    assertEquals(id, resp.getConsoleId());
    assertEquals(PlugControllerResponsePB.Status.NO_PORTS_REQUESTED, resp.getStatus());
  }

  /**
   * Tests that multiple players from a single client can plug properly.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testMultiplePlayersOneClient() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).setRequestedPort2(Port.PORT_2)
        .setRequestedPort3(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());

    PlugControllerResponsePB resp = plugControllerCaptor.getValue();
    assertEquals(3, resp.getPortCount());
    assertTrue(resp.getPortList().contains(Port.PORT_2));

    reset(plugControllerObserver);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    assertEquals(1, plugControllerCaptor.getValue().getPortCount());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testStartGameNoSuchConsoleId() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    long clientId1 = plugControllerCaptor.getValue().getClientId();

    reset(plugControllerObserver);
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    long clientId2 = plugControllerCaptor.getValue().getClientId();

    // Begin the streams
    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    StreamObserver<IncomingEventPB> eventObserver1 = mock(StreamObserver.class);
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);

    readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId2).setConsoleId(id).build())
        .build();
    StreamObserver<IncomingEventPB> eventObserver2 = mock(StreamObserver.class);
    StreamObserver<OutgoingEventPB> outgoingSender2 = server.sendEvent(eventObserver2);
    outgoingSender2.onNext(readyPb);

    StartGameRequestPB startRequest = StartGameRequestPB.newBuilder().setConsoleId(id + 1).build();
    server.startGame(startRequest, startGameObserver);
    verify(startGameObserver).onNext(startGameCaptor.capture());
    assertEquals(StartGameResponsePB.Status.NO_SUCH_CONSOLE,
        startGameCaptor.getValue().getStatus());
  }

  /**
   * Tests the normal flow for two players joining and then starting a game.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testReadyAndStartGame() {
    long id = makeDefaultConsole();
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_ANY).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    long clientId1 = plugControllerCaptor.getValue().getClientId();

    reset(plugControllerObserver);
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    long clientId2 = plugControllerCaptor.getValue().getClientId();

    // Begin the streams.
    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    StreamObserver<IncomingEventPB> eventObserver1 = mock(StreamObserver.class);
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);

    // Send the ready message.
    readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId2).setConsoleId(id).build())
        .build();
    StreamObserver<IncomingEventPB> eventObserver2 = mock(StreamObserver.class);
    StreamObserver<OutgoingEventPB> outgoingSender2 = server.sendEvent(eventObserver2);
    outgoingSender2.onNext(readyPb);

    // Verify that the game was started successfully.
    StartGameRequestPB startRequest = StartGameRequestPB.newBuilder().setConsoleId(id).build();
    ArgumentCaptor<IncomingEventPB> incomingCaptor1 =
        ArgumentCaptor.forClass(IncomingEventPB.class);
    ArgumentCaptor<IncomingEventPB> incomingCaptor2 =
        ArgumentCaptor.forClass(IncomingEventPB.class);
    server.startGame(startRequest, startGameObserver);
    verify(startGameObserver).onNext(startGameCaptor.capture());
    assertEquals(startGameCaptor.getValue().getStatus(), StartGameResponsePB.Status.SUCCESS);

    // Verify that the stream clients were notified of the game starting.
    verify(eventObserver1).onNext(incomingCaptor1.capture());
    verify(eventObserver2).onNext(incomingCaptor2.capture());
    IncomingEventPB startResp1 = incomingCaptor1.getValue();
    IncomingEventPB startResp2 = incomingCaptor2.getValue();
    assertTrue(startResp1.hasStartGame());
    assertTrue(startResp2.hasStartGame());
    assertEquals(startResp1.getStartGame().getConsoleId(), id);
    assertEquals(startResp2.getStartGame().getConsoleId(), id);
    assertEquals(2, startResp1.getStartGame().getConnectedPortsCount());
    assertEquals(2, startResp2.getStartGame().getConnectedPortsCount());
  }

  /**
   * Test when start game has been called but not all players are ready.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testNotAllPlayersReady() {
    long id = makeDefaultConsole();

    // Plug controller 1
    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_1).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    PlugControllerResponsePB plugControllerResponse = plugControllerCaptor.getValue();
    assertEquals(PlugControllerResponsePB.Status.SUCCESS, plugControllerResponse.getStatus());

    long clientId1 = plugControllerResponse.getClientId();

    // Plug controller 2
    reset(plugControllerObserver);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_2).build();
    server.plugController(pReq, plugControllerObserver);
    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    plugControllerResponse = plugControllerCaptor.getValue();
    assertEquals(PlugControllerResponsePB.Status.SUCCESS, plugControllerResponse.getStatus());

    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    StreamObserver<IncomingEventPB> eventObserver1 = mock(StreamObserver.class);

    // Send ready only for player 1
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);
    StreamObserver<IncomingEventPB> eventObserver2 = mock(StreamObserver.class);
    server.sendEvent(eventObserver2);

    // Expect the game start event to fail because player 2 isn't ready
    StartGameRequestPB startRequest = StartGameRequestPB.newBuilder().setConsoleId(id).build();
    server.startGame(startRequest, startGameObserver);
    verify(startGameObserver).onNext(startGameCaptor.capture());
    assertEquals(StartGameResponsePB.Status.CLIENTS_NOT_READY,
        startGameCaptor.getValue().getStatus());
    verify(eventObserver1, never()).onNext(any(IncomingEventPB.class));
    verify(eventObserver2, never()).onNext(any(IncomingEventPB.class));
  }

  @Test
  public void testSendKeypresses() {
    long id = makeDefaultConsole();

    PlugControllerRequestPB pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id)
        .setDelayFrames(2).setRequestedPort1(Port.PORT_1).build();
    server.plugController(pReq, plugControllerObserver);
    pReq = PlugControllerRequestPB.newBuilder().setConsoleId(id).setDelayFrames(2)
        .setRequestedPort1(Port.PORT_2).build();

    verify(plugControllerObserver).onNext(plugControllerCaptor.capture());
    long clientId1 = plugControllerCaptor.getValue().getClientId();

    OutgoingEventPB readyPb = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setClientId(clientId1).setConsoleId(id).build())
        .build();
    @SuppressWarnings("unchecked")
    StreamObserver<IncomingEventPB> eventObserver1 = mock(StreamObserver.class);

    // Send ready only for player 1
    StreamObserver<OutgoingEventPB> outgoingSender1 = server.sendEvent(eventObserver1);
    outgoingSender1.onNext(readyPb);
    @SuppressWarnings("unchecked")
    StreamObserver<IncomingEventPB> eventObserver2 = mock(StreamObserver.class);
    server.sendEvent(eventObserver2);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testShutdownTestServer() {
    ArgumentCaptor<ShutDownServerResponsePB> shutdownCaptor =
        ArgumentCaptor.forClass(ShutDownServerResponsePB.class);

    // The server implementation was not passed.
    ShutDownServerRequestPB req = ShutDownServerRequestPB.newBuilder().build();
    StreamObserver<ShutDownServerResponsePB> shutdownObserver = mock(StreamObserver.class);
    server.shutDownServer(req, shutdownObserver);
    verify(shutdownObserver).onNext(shutdownCaptor.capture());
    assertTrue(!shutdownCaptor.getValue().getServerWillDie());

    // The server implementation was passed
    reset(shutdownObserver);
    server.setServerImpl(new DummyGrpcServer());
    server.shutDownServer(req, shutdownObserver);
    verify(shutdownObserver).onNext(shutdownCaptor.capture());
    assertTrue(shutdownCaptor.getValue().getServerWillDie());
  }

  @Test
  public void testShutdownProdServer() {
    ArgumentCaptor<ShutDownServerResponsePB> shutdownCaptor =
        ArgumentCaptor.forClass(ShutDownServerResponsePB.class);

    server = new Server(false);
    server.setServerImpl(new DummyGrpcServer());
    ShutDownServerRequestPB req = ShutDownServerRequestPB.newBuilder().build();
    @SuppressWarnings("unchecked")
    StreamObserver<ShutDownServerResponsePB> shutdownObserver = mock(StreamObserver.class);
    server.shutDownServer(req, shutdownObserver);
    verify(shutdownObserver).onNext(shutdownCaptor.capture());
    assertTrue(!shutdownCaptor.getValue().getServerWillDie());
  }

}
