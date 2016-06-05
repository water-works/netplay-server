package netplayServer.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import netplayServer.Client;
import netplayServer.Console;
import netplayServer.Server;
import netplayprotos.NetplayServiceProto.Port;

@RunWith(MockitoJUnitRunner.class)
public class ClientTest {

  @Mock private Server server;
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
    client.addPlayerForPort(Port.PORT_1);
    assertEquals(1, client.getPorts().size());
  }

}
