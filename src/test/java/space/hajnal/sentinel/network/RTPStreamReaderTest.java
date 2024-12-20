package space.hajnal.sentinel.network;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.receiver.PacketReceiver;
import space.hajnal.sentinel.network.video.VideoStreamProcessor;

class RTPStreamReaderTest {

  private VideoStreamProcessor mockVideoStreamProcessor;
  private PacketReceiver mockPacketReceiver;
  private ExecutorService threadPool;
  private RTPStreamReader rtpStreamReader;

  @BeforeEach
  void setUp() {
    mockVideoStreamProcessor = mock(VideoStreamProcessor.class);
    mockPacketReceiver = mock(PacketReceiver.class);
    threadPool = Executors.newFixedThreadPool(2);
    rtpStreamReader = new RTPStreamReader(mockVideoStreamProcessor, mockPacketReceiver, threadPool);
  }

  @AfterEach
  void tearDown() {
    rtpStreamReader.close();
    if (!threadPool.isShutdown()) {
      threadPool.shutdownNow();
    }
  }

  @Test
  void testStart_InvokesPacketReceiverAndFrameProcessor() throws Exception {
    // Arrange: Mock PacketReceiver to return a valid RTPPacket
    RTPPacket mockPacket = mock(RTPPacket.class);
    DatagramSocket mockSocket = mock(DatagramSocket.class);
    when(mockPacketReceiver.retrievePacket()).thenReturn(mockPacket);

    // Act
    rtpStreamReader.start(mockSocket);

    // Allow some time for threads to execute
    threadPool.shutdown();
    assertTrue(threadPool.awaitTermination(1, TimeUnit.SECONDS), "Thread pool did not terminate");

    // Assert: Verify interactions with PacketReceiver and FrameProcessor
    verify(mockPacketReceiver, times(1)).startReceiving(mockSocket);
    verify(mockPacketReceiver, times(1)).retrievePacket();
    verify(mockVideoStreamProcessor, times(1)).processPacket(mockPacket);
  }

  @Test
  void testStart_HandlePacketReceiverException() throws Exception {
    // Arrange: Mock PacketReceiver to throw an exception
    DatagramSocket mockSocket = mock(DatagramSocket.class);
    doThrow(new RuntimeException("Receiver exception")).when(mockPacketReceiver).startReceiving(mockSocket);
    when(mockPacketReceiver.retrievePacket()).thenReturn(mock(RTPPacket.class));

    // Act
    rtpStreamReader.start(mockSocket);

    // Allow some time for threads to execute
    threadPool.shutdown();
    assertTrue(threadPool.awaitTermination(1, TimeUnit.SECONDS), "Thread pool did not terminate");

    // Assert: Ensure exception in PacketReceiver does not crash the thread
    verify(mockPacketReceiver, times(1)).startReceiving(mockSocket);
  }

  @Test
  void testStart_HandleFrameProcessorException() throws Exception {
    // Arrange: Mock PacketReceiver to return a valid RTPPacket
    DatagramSocket mockSocket = mock(DatagramSocket.class);
    RTPPacket mockPacket = mock(RTPPacket.class);
    when(mockPacketReceiver.retrievePacket()).thenReturn(mockPacket);

    // Mock FrameProcessor to throw an exception
    doThrow(new RuntimeException("FrameProcessor exception")).when(mockVideoStreamProcessor)
        .processPacket(mockPacket);

    // Act
    rtpStreamReader.start(mockSocket);

    // Allow some time for threads to execute
    threadPool.shutdown();
    assertTrue(threadPool.awaitTermination(1, TimeUnit.SECONDS), "Thread pool did not terminate");

    // Assert: Ensure exception in FrameProcessor does not crash the thread
    verify(mockPacketReceiver, times(1)).retrievePacket();
    verify(mockVideoStreamProcessor, times(1)).processPacket(mockPacket);
  }

}
