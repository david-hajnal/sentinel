package space.hajnal.sentinel.network;

import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import space.hajnal.sentinel.network.model.RTPPacket;

import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrameProcessorTest {

  private FrameProcessor frameProcessor;

  @BeforeEach
  void setUp() {
    frameProcessor = new FrameProcessor();
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
    frameProcessor.processPacket(rtpPacket);

    // Assert
    SortedMap<Integer, byte[]> buffer = frameProcessor.getFramesByTimestamp(1000L);
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

    FrameProcessor.FrameListener mockListener = mock(FrameProcessor.FrameListener.class);
    frameProcessor.addSubscriber(mockListener);

    // Act
    frameProcessor.processPacket(packet1);
    frameProcessor.processPacket(packet2);

    // Assert
    verify(mockListener, times(1)).onFrameAvailable(new byte[]{0x01, 0x02, 0x03, 0x04});
    assertNull(frameProcessor.getFramesByTimestamp(1000L),
        "Frame buffer for timestamp 1000 should be removed");
  }

  @Test
  void testAddSubscriber_NotifiesSubscriber() {
    // Arrange
    FrameProcessor.FrameListener mockListener = mock(FrameProcessor.FrameListener.class);
    frameProcessor.addSubscriber(mockListener);

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
    frameProcessor.processPacket(packet1);
    frameProcessor.processPacket(packet2);

    // Assert
    verify(mockListener, times(1)).onFrameAvailable(new byte[]{0x05, 0x06, 0x07, 0x08});
  }

  @Test
  void testReassembleFrame_CorrectlyReassemblesFrame() {
    // Arrange
    SortedMap<Integer, byte[]> packets = new TreeMap<>();
    packets.put(1, new byte[]{0x01, 0x02});
    packets.put(2, new byte[]{0x03, 0x04});
    packets.put(3, new byte[]{0x05, 0x06});

    // Act
    byte[] frame = frameProcessor.reassembleFrame(packets);

    // Assert
    assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}, frame,
        "Frame assembly mismatch");
  }

  @Test
  void testNotifySubscribers_NotifiesAllSubscribers() {
    // Arrange
    FrameProcessor.FrameListener listener1 = mock(FrameProcessor.FrameListener.class);
    FrameProcessor.FrameListener listener2 = mock(FrameProcessor.FrameListener.class);

    frameProcessor.addSubscriber(listener1);
    frameProcessor.addSubscriber(listener2);

    byte[] frame = new byte[]{0x01, 0x02, 0x03};

    // Act
    frameProcessor.notifySubscribers(frame);

    // Assert
    verify(listener1, times(1)).onFrameAvailable(frame);
    verify(listener2, times(1)).onFrameAvailable(frame);
  }

}
