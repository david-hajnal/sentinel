package space.hajnal.sentinel.network.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrameProcessorTest {

  private FrameProcessor frameProcessor;

  @BeforeEach
  void setUp() {
    frameProcessor = new FrameProcessor();
  }

  @Test
  void testReassembleFrame_SimplePackets() {
    // Arrange: Create a map of packets with small payloads
    SortedMap<Integer, byte[]> packets = new TreeMap<>();
    packets.put(1, new byte[]{0x01, 0x02, 0x03});
    packets.put(2, new byte[]{0x04, 0x05});
    packets.put(3, new byte[]{0x06, 0x07, 0x08});

    // Act
    byte[] reassembledFrame = frameProcessor.reassembleFrame(packets);

    // Assert
    assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, reassembledFrame);
  }

  @Test
  void testReassembleFrame_EmptyPackets() {
    // Arrange: Create an empty map of packets
    SortedMap<Integer, byte[]> packets = new TreeMap<>();

    // Act
    byte[] reassembledFrame = frameProcessor.reassembleFrame(packets);

    // Assert
    assertArrayEquals(new byte[]{}, reassembledFrame, "Reassembled frame should be empty");
  }

  @Test
  void testReassembleFrame_SinglePacket() {
    // Arrange: Create a map with a single packet
    SortedMap<Integer, byte[]> packets = new TreeMap<>();
    packets.put(1, new byte[]{0x01, 0x02, 0x03});

    // Act
    byte[] reassembledFrame = frameProcessor.reassembleFrame(packets);

    // Assert
    assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, reassembledFrame);
  }

  @Test
  void testReassembleFrame_FullHDImage() {
    // Arrange: Simulate a Full HD (1920x1080) image split into multiple packets
    int width = 1920;
    int height = 1080;
    int bytesPerPixel = 3; // Assuming RGB format
    int totalBytes = width * height * bytesPerPixel;

    // Create packets of size 1024 bytes each
    int packetSize = 1024;
    int numPackets = (int) Math.ceil((double) totalBytes / packetSize);

    SortedMap<Integer, byte[]> packets = new TreeMap<>();
    for (int i = 0; i < numPackets; i++) {
      int remainingBytes = totalBytes - i * packetSize;
      int currentPacketSize = Math.min(packetSize, remainingBytes);
      byte[] packet = new byte[currentPacketSize];
      for (int j = 0; j < currentPacketSize; j++) {
        packet[j] = (byte) ((i + j) % 256); // Simulate image data
      }
      packets.put(i, packet);
    }

    // Act
    byte[] reassembledFrame = frameProcessor.reassembleFrame(packets);

    // Assert
    assertEquals(totalBytes, reassembledFrame.length, "Reassembled frame size should match Full HD image size");
  }
}
