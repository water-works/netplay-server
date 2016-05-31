package netplayServer.utils;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.grpc.stub.StreamObserver;
import netplayServer.Client;
import netplayServer.ClientHandoffStreamObserver;
import netplayServer.Console;
import netplayprotos.NetplayServiceProto.ClientReadyPB;
import netplayprotos.NetplayServiceProto.IncomingEventPB;
import netplayprotos.NetplayServiceProto.InvalidDataPB;
import netplayprotos.NetplayServiceProto.KeyStatePB;
import netplayprotos.NetplayServiceProto.OutgoingEventPB;
import netplayprotos.NetplayServiceProto.Port;

@RunWith(MockitoJUnitRunner.class)
public class ClientHandoffStreamObserverTest {

  private final long CONSOLE_ID = 100L;
  private final long CLIENT_ID = 200L;

  @Mock
  private Map<Long, Console> consoleMap;
  @Mock
  private StreamObserver<IncomingEventPB> responseObserver;

  private ClientHandoffStreamObserver<IncomingEventPB> handoffObserver;

  private Console mockConsole = mock(Console.class);
  private Client mockClient = mock(Client.class);

  @Before
  public void setUp() {
    handoffObserver = new ClientHandoffStreamObserver<>(responseObserver, consoleMap);

    when(consoleMap.get(any())).thenReturn(null);
    when(consoleMap.get(CONSOLE_ID)).thenReturn(mockConsole);
    when(mockConsole.getClientById(CLIENT_ID)).thenReturn(mockClient);
  }

  @Test
  public void testOnNextNoClient() {
    OutgoingEventPB event = OutgoingEventPB.newBuilder()
        .addKeyPress(
            KeyStatePB.newBuilder().setConsoleId(CONSOLE_ID).setPort(Port.PORT_1).setFrameNumber(1))
        .build();
    handoffObserver.onNext(event);
    verify(responseObserver, never()).onNext(any());
  }

  @Test
  public void testOnNextClientReady() {
    OutgoingEventPB event = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setConsoleId(CONSOLE_ID).setClientId(CLIENT_ID))
        .build();
    handoffObserver.onNext(event);
    verify(mockConsole, times(1)).getClientById(CLIENT_ID);
    verify(mockClient, times(1)).setStreamObserver(responseObserver);
    verify(mockClient, times(1)).setReady();
  }

  @Test
  public void testOnNextClientReadyMultipleCalls() {
    OutgoingEventPB event = OutgoingEventPB.newBuilder()
        .setClientReady(ClientReadyPB.newBuilder().setConsoleId(CONSOLE_ID).setClientId(CLIENT_ID))
        .build();
    handoffObserver.onNext(event);
    verify(mockConsole, times(1)).getClientById(CLIENT_ID);
    verify(mockClient, times(1)).setStreamObserver(responseObserver);
    verify(mockClient, times(1)).setReady();

    reset(mockClient);
    handoffObserver.onNext(event);
    verify(mockClient, never()).setStreamObserver(any());
    verify(mockClient, never()).setReady();
  }

  @Test
  public void testOnNextClientReadyNoSuchConsole() {
    OutgoingEventPB event =
        OutgoingEventPB.newBuilder()
            .setClientReady(
                ClientReadyPB.newBuilder().setConsoleId(CONSOLE_ID + 1).setClientId(CLIENT_ID))
            .build();
    handoffObserver.onNext(event);
    verify(responseObserver, times(1)).onNext(IncomingEventPB.newBuilder()
        .addInvalidData(InvalidDataPB.newBuilder().setStatus(InvalidDataPB.Status.INVALID_CONSOLE))
        .build());
    verify(responseObserver, times(1)).onCompleted();
  }
}
