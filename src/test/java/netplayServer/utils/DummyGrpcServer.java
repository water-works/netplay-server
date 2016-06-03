package netplayServer.utils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.NotImplementedException;

import io.grpc.Server;

/**
 * A partial mocking of the GRPC server class for use in tests.
 * @author alexgolec
 */
public class DummyGrpcServer extends Server {

  public boolean didShutDown = false;
  
  @Override
  public Server start() throws IOException {
    throw new NotImplementedException();
  }

  @Override
  public Server shutdown() {
    didShutDown = true;
    return this;
  }

  @Override
  public Server shutdownNow() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isShutdown() {
    throw new NotImplementedException();
  }

  @Override
  public boolean isTerminated() {
    throw new NotImplementedException();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    throw new NotImplementedException();
  }

  @Override
  public void awaitTermination() throws InterruptedException {
    throw new NotImplementedException();
  }

}
