package netplayServer.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.StreamObserver;
import netplayServer.Client;
import netplayServer.Client.ClientStatus;
import netplayServer.Client.Player;
import netplayServer.Console;
import netplayServer.Server;
import netplayprotos.NetplayServiceProto.IncomingEventPB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;
import netplayprotos.NetplayServiceProto.Port;

@RunWith(MockitoJUnitRunner.class)
public class ClientTest {

  @Mock private Server server;
  @Mock private StreamObserver<IncomingEventPB> incomingStream;
  private Client client;
  private Console console;

  @Before
  public void setUp() {
    console = new Console(server);
    client = new Client(console, 0);

  }

  @Test
  public void testAddPlayerForPort() {
    assertEquals(0, client.getPorts().size());
    client.addPlayerForPort(Port.PORT_1);
    assertEquals(1, client.getPorts().size());
	assertFalse(client.addPlayerForPort(Port.PORT_1));
    assertEquals(1, client.getPorts().size());
  }
  
  @Test
  public void testGetPlayer() {
	assertNull(client.getPlayer(Port.PORT_1));
	client.addPlayerForPort(Port.PORT_1);
	Player player = client.getPlayer(Port.PORT_1);
	assertNotNull(player);
	assertEquals(player.getClient(), client);
  }
  
  @Test
  public void testAddMultiplePlayers() {
	assertTrue(client.addPlayerForPort(Port.PORT_1));
	assertTrue(client.addPlayerForPort(Port.PORT_2));
	assertTrue(client.addPlayerForPort(Port.PORT_3));
	assertTrue(client.addPlayerForPort(Port.PORT_4));
  }
  
  @Test
  public void testCreatedStatus() {
	assertEquals(client.getStatus(), ClientStatus.CREATED);
  }
  
  @Test
  public void testReadyStatus() {
	client.setReady();
	assertEquals(client.getStatus(), ClientStatus.READY);
  }
  
  @Test
  public void testGetPorts() {
	client.addPlayerForPort(Port.PORT_1);
	assertEquals(client.getPorts().size(), 1);
	assertTrue(client.getPorts().contains(Port.PORT_1));
	client.addPlayerForPort(Port.PORT_2);
	assertEquals(client.getPorts().size(), 2);
	assertTrue(client.getPorts().contains(Port.PORT_2));
  }
  
  @Test
  public void testSetStreamObserver() {
	// Exception should be thrown if we don't have a stream observer
	try {
	  client.onNext(OutgoingEventPB.getDefaultInstance());
	  fail("Expected IllegalStateException");
	} catch (IllegalStateException expected) {
	  // expected
	}
	client.setStreamObserver(incomingStream);
	client.onNext(OutgoingEventPB.getDefaultInstance());
  }
  
  @Test
  public void testPlayingStatus() {
	client.setStreamObserver(incomingStream);
	client.acceptStartGame();
	assertEquals(client.getStatus(), ClientStatus.PLAYING);
  }
  
  @Test
  public void testDoneStatus() {
	client.setStreamObserver(incomingStream);
	client.acceptStartGame();
	client.onCompleted();
	assertEquals(client.getStatus(), ClientStatus.DONE);
  }
  

}
