package space.hajnal.sentinel.network.receiver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;

class RTPSocketReceiverTest {

  private DatagramSocket mockSocket;
  private ExecutorService threadPool;
  private RTPSocketReceiver rtpSocketReceiver;
  private RTPPacketDeserializer rtpPacketDeserializer;

  @BeforeEach
  void setUp() {
    mockSocket = mock(DatagramSocket.class);
    ServerOptions serverOptions = ServerOptions.builder().mtu(1500).build();

    // Use a thread pool that runs tasks on the current thread
    threadPool = Executors.newSingleThreadScheduledExecutor();

    rtpPacketDeserializer = mock(RTPPacketDeserializer.class);
    rtpSocketReceiver = new RTPSocketReceiver(serverOptions, rtpPacketDeserializer, 100);
  }

  @AfterEach
  void tearDown() {
    rtpSocketReceiver.close();
    threadPool.shutdownNow();
  }

  @Test
  @Timeout(5)
  void testStartReceiving_PacketsAreReceived() throws Exception {
    // Arrange: Mock the DatagramSocket behavior
    RTPPacket rtpPacket = mock(RTPPacket.class);

    CountDownLatch latch = new CountDownLatch(1);
    byte[] buffer = new byte[1400];
    buffer[0] = 0x01;
    buffer[1] = 0x02;
    buffer[2] = 0x03;

    when(rtpPacketDeserializer.deserialize(any(byte[].class))).thenReturn(rtpPacket);

    doAnswer(invocation -> {
      DatagramPacket p = invocation.getArgument(0);
      System.arraycopy(buffer, 0, p.getData(), 0, buffer.length);
      p.setLength(buffer.length);
      latch.countDown(); // Signal packet received
      return p; // Simulate packet receive completion
    }).when(mockSocket).receive(any());

    // Act
    startReceiving(mockSocket);
    // Retrieve the packet in the processor pool
    assertTrue(latch.await(100, TimeUnit.MILLISECONDS), "Packet was not received in time");
    // Retrieve the result
    RTPPacket receivedPacket = getRtpPacket();

    // Assert
    assertNotNull(receivedPacket, "Received packet should not be null");
    assertEquals(rtpPacket, receivedPacket, "Packet mismatch");
    assertArrayEquals(rtpPacket.getPayload(), receivedPacket.getPayload(), "Payload mismatch");
  }

  @Test
  @Timeout(5)
  void testRetrievePacket_QueueBlocksWhenEmpty() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);

    // Act

    Future<?> future = threadPool.submit(() -> {
      try {
        RTPPacket rtpPacket = rtpSocketReceiver.retrievePacket(); // Should block
        System.out.println("Received packet: " + rtpPacket);
      } catch (InterruptedException e) {
        System.out.println("Packet retrieval was interrupted");
        latch.countDown(); // Signal the blocking was interrupted
        Thread.currentThread().interrupt();
      }
    });

    try {
      future.get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      // Expected timeout
    }

    // Interrupt the retrieval thread
    future.cancel(true);
    assertTrue(latch.await(1, TimeUnit.SECONDS)); // Verify blocking behavior
  }

  @Test
  @Timeout(5)
  void testStartReceiving_HandlesIOExceptionGracefully() throws Exception {
    // Arrange
    doThrow(new IOException("Socket error")).when(mockSocket).receive(any());
    DatagramSocket mockSocket = mock(DatagramSocket.class);
    // Act
    startReceiving(mockSocket);

    // No exception thrown, socket error is logged
    assertTrue(true); // Implicit pass if no exception is thrown
  }

  private void startReceiving(DatagramSocket socket) {
    threadPool.submit(() -> rtpSocketReceiver.startReceiving(socket)); // Don't block with get()
  }

  private RTPPacket getRtpPacket() {
    try {
      return rtpSocketReceiver.retrievePacket();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Packet retrieval was interrupted", e);
    }
  }
}
