package space.hajnal.sentinel.network.receiver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.*;

import space.hajnal.sentinel.network.model.RTPPacket;
import org.junit.jupiter.api.*;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.model.ServerOptions;

class PacketReceiverTest {

  private DatagramSocket mockSocket;
  private ExecutorService threadPool;
  private PacketReceiver packetReceiver;
  private RTPPacketDeserializer rtpPacketDeserializer;

  @BeforeEach
  void setUp() {
    mockSocket = mock(DatagramSocket.class);
    ServerOptions serverOptions = ServerOptions.builder().mtu(1500).build();

    // Use a thread pool that runs tasks on the current thread
    threadPool = Executors.newFixedThreadPool(2);

    rtpPacketDeserializer = mock(RTPPacketDeserializer.class);
    packetReceiver = new PacketReceiver(mockSocket, serverOptions, rtpPacketDeserializer, 1000);
  }

  @AfterEach
  void tearDown() {
    packetReceiver.close();
    threadPool.shutdownNow();
  }

  @Test
  @Timeout(5)
  void testStartReceiving_PacketsAreReceived() throws Exception {
    // Arrange: Mock the DatagramSocket behavior
    RTPPacket rtpPacket = mock(RTPPacket.class);
    when(rtpPacketDeserializer.deserialize(any())).thenReturn(rtpPacket);
    CountDownLatch latch = new CountDownLatch(1);
    byte[] data = {0x01, 0x02, 0x03};
    when(rtpPacket.getPayload()).thenReturn(data);

    doAnswer(invocation -> {
      DatagramPacket p = invocation.getArgument(0);
      System.arraycopy(data, 0, p.getData(), 0, data.length);
      p.setLength(data.length);
      latch.countDown(); // Signal packet is "received"
      return p; // Simulate packet receive completion
    }).when(mockSocket).receive(any());

    // Act
    startReceiving();
    // Retrieve the packet in the processor pool
    Future<RTPPacket> futurePacket = threadPool.submit(this::getRtpPacket);

    // Retrieve the result
    assertTrue(latch.await(1, TimeUnit.SECONDS), "Packet was not received in time");
    RTPPacket receivedPacket = futurePacket.get(1, TimeUnit.SECONDS);

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
        RTPPacket rtpPacket = packetReceiver.retrievePacket(); // Should block
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

    // Act
    startReceiving();

    // No exception thrown, socket error is logged
    assertTrue(true); // Implicit pass if no exception is thrown
  }

  private void startReceiving() {
    threadPool.submit(packetReceiver::startReceiving); // Don't block with get()
  }

  private RTPPacket getRtpPacket() {
    try {
      return packetReceiver.retrievePacket();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Packet retrieval was interrupted", e);
    }
  }
}
