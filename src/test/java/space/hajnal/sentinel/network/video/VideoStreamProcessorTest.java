package space.hajnal.sentinel.network.video;

import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.camera.model.SentinelFrame;
import space.hajnal.sentinel.network.model.RTPPacket;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoStreamProcessorTest {

  private VideoStreamProcessor videoStreamProcessor;

  @BeforeEach
  void setUp() {
    FrameProcessor mock = mock(FrameProcessor.class);
    videoStreamProcessor = new VideoStreamProcessor(mock);
  }

  @Test
  void testProcessPacket_AddsPacketToBuffer() {
    // Arrange
    RTPPacket rtpPacket = mock(RTPPacket.class);
    when(rtpPacket.getTimestamp()).thenReturn(1000L);
    when(rtpPacket.getSequenceNumber()).thenReturn(1);
    when(rtpPacket.getPayload()).thenReturn(new byte[]{0x01, 0x02});
    when(rtpPacket.isMarker()).thenReturn(false);

    // Act
    videoStreamProcessor.processPacket(rtpPacket);

    // Assert
    SortedMap<Integer, byte[]> buffer = videoStreamProcessor.getFramesByTimestamp(1000L);
    assertNotNull(buffer, "Frame buffer for timestamp 1000 should exist");
    assertArrayEquals(new byte[]{0x01, 0x02}, buffer.get(1), "Payload mismatch");
  }

  @Test
  void testProcessPacket_ReassemblesFrameOnMarker() {
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

    VideoStreamProcessor.FrameListener mockListener = mock(VideoStreamProcessor.FrameListener.class);
    videoStreamProcessor.addSubscriber(mockListener);

    // Act
    videoStreamProcessor.processPacket(packet1);
    videoStreamProcessor.processPacket(packet2);

    // Assert
    byte[] bytes = {0x01, 0x02, 0x03, 0x04};
    verify(mockListener, times(1)).onFrameAvailable(SentinelFrame.builder().data(bytes).build());
    assertNull(videoStreamProcessor.getFramesByTimestamp(1000L),
        "Frame buffer for timestamp 1000 should be removed");
  }

  @Test
  void testAddSubscriber_NotifiesSubscriber() {
    // Arrange
    VideoStreamProcessor.FrameListener mockListener = mock(VideoStreamProcessor.FrameListener.class);
    videoStreamProcessor.addSubscriber(mockListener);

    RTPPacket packet1 = mock(RTPPacket.class);
    RTPPacket packet2 = mock(RTPPacket.class);

    when(packet1.getTimestamp()).thenReturn(2000L);
    when(packet1.getSequenceNumber()).thenReturn(1);
    when(packet1.getPayload()).thenReturn(new byte[]{0x05, 0x06});
    when(packet1.isMarker()).thenReturn(false);

    when(packet2.getTimestamp()).thenReturn(2000L);
    when(packet2.getSequenceNumber()).thenReturn(2);
    when(packet2.getPayload()).thenReturn(new byte[]{0x07, 0x08});
    when(packet2.isMarker()).thenReturn(true);

    // Act
    videoStreamProcessor.processPacket(packet1);
    videoStreamProcessor.processPacket(packet2);

    // Assert
    byte[] bytes = {0x05, 0x06, 0x07, 0x08};
    verify(mockListener, times(1)).onFrameAvailable(SentinelFrame.builder().data(bytes).build());
  }

  @Test
  void testNotifySubscribers_NotifiesAllSubscribers() {
    // Arrange
    VideoStreamProcessor.FrameListener listener1 = mock(VideoStreamProcessor.FrameListener.class);
    VideoStreamProcessor.FrameListener listener2 = mock(VideoStreamProcessor.FrameListener.class);

    videoStreamProcessor.addSubscriber(listener1);
    videoStreamProcessor.addSubscriber(listener2);

    byte[] frame = new byte[]{0x01, 0x02, 0x03};

    // Act
    SentinelFrame sentinelFrame = SentinelFrame.builder().data(frame).build();
    videoStreamProcessor.notifySubscribers(sentinelFrame);

    // Assert
    verify(listener1, times(1)).onFrameAvailable(sentinelFrame);
    verify(listener2, times(1)).onFrameAvailable(sentinelFrame);
  }

}
