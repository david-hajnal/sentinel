package space.hajnal.sentinel.network.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.model.RTPPacket;

class VideoStreamProcessorTest {

  private VideoStreamProcessor videoStreamProcessor;
  private FrameProcessor frameProcessor;

  @BeforeEach
  void setUp() {
    frameProcessor = mock(FrameProcessor.class);
    videoStreamProcessor = new VideoStreamProcessor(frameProcessor, 1,
        Executors.newSingleThreadScheduledExecutor());
  }

  @Test
  void testProcessPacket_ReassemblesFrameOnMarker() throws InterruptedException {
    // Arrange
    RTPPacket packet1 = mock(RTPPacket.class);
    RTPPacket packet2 = mock(RTPPacket.class);

    when(packet1.getTimestamp()).thenReturn(1000L);
    when(packet1.getSequenceNumber()).thenReturn(1);
    when(packet1.getPayload()).thenReturn(new byte[]{0x01, 0x02});
    when(packet1.isMarker()).thenReturn(false);

    when(packet2.getTimestamp()).thenReturn(1000L);
    when(packet2.getSequenceNumber()).thenReturn(2);
    when(packet2.getPayload()).thenReturn(new byte[]{0x03, 0x04});
    when(packet2.isMarker()).thenReturn(true);
    byte[] bytes = {0x01, 0x02, 0x03, 0x04};

    when(frameProcessor.reassembleFrame(any())).thenReturn(bytes);

    CountDownLatch latch = new CountDownLatch(1);
    VideoStreamProcessor.FrameListener mockListener = frame -> {
      assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, frame.getData(), "Payload mismatch");
      latch.countDown();
    };

    videoStreamProcessor.addSubscriber(mockListener);

    // Act
    videoStreamProcessor.processPacket(packet1);
    videoStreamProcessor.processPacket(packet2);

    // Assert
    assertTrue(latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS),
        "Frame was not reassembled"); // need larger timeout for jitter
    assertNull(videoStreamProcessor.getFramesByTimestamp(1000L),
        "Frame buffer for timestamp 1000 should be removed");
  }

}
