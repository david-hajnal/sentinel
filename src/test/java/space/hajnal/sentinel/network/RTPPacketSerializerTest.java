package space.hajnal.sentinel.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

class RTPPacketSerializerTest {

  private RTPPacketSerializer serializer;

  @BeforeEach
  void setUp() {
    serializer = new RTPPacketSerializer();
  }

  @Test
  void testSerializeSinglePacketFrame() {
    // Arrange
    byte[] frameData = new byte[500]; // Frame smaller than MTU
    int mtu = 1400; // MTU size
    long timestamp = 12345L;
    int ssrc = 67890;

    // Act
    List<RTPPacket> packets = serializer.serialize(frameData, mtu, timestamp, ssrc);

    // Assert
    assertEquals(1, packets.size(),
        "A single packet should be generated for frame smaller than MTU.");
    RTPPacket packet = packets.getFirst();
    assertEquals(96, packet.getPayloadType());
    assertEquals(0, packet.getSequenceNumber());
    assertEquals(timestamp, packet.getTimestamp());
    assertEquals(ssrc, packet.getSsrc());
    assertTrue(packet.isMarker(), "Marker bit should be set for the last packet of a frame.");
    assertArrayEquals(frameData, packet.getPayload());
  }

  @Test
  void testSerializeMultiPacketFrame() {
    // Arrange
    byte[] frameData = new byte[3000]; // Frame larger than MTU
    int mtu = 1400; // MTU size
    long timestamp = 12345L;
    int ssrc = 67890;

    // Act
    List<RTPPacket> packets = serializer.serialize(frameData, mtu, timestamp, ssrc);

    // Assert
    assertEquals(3, packets.size(), "Frame should be split into multiple packets.");
    assertEquals(1388, packets.get(0).getPayload().length,
        "First packet payload size should match MTU.");
    assertEquals(1388, packets.get(1).getPayload().length,
        "Second packet payload size should match MTU.");
    assertEquals(224, packets.get(2).getPayload().length,
        "Last packet payload size should contain remaining data.");

    // Verify sequence numbers
    for (int i = 0; i < packets.size(); i++) {
      assertEquals(i, packets.get(i).getSequenceNumber(),
          "Sequence number should match the packet order.");
    }

    // Verify marker bit
    assertFalse(packets.get(0).isMarker(), "Marker bit should not be set for non-final packets.");
    assertFalse(packets.get(1).isMarker(), "Marker bit should not be set for non-final packets.");
    assertTrue(packets.get(2).isMarker(),
        "Marker bit should be set for the last packet of a frame.");
  }

  @Test
  void testSerializeEmptyFrame() {
    // Arrange
    byte[] frameData = new byte[0]; // Empty frame
    int mtu = 1400;
    long timestamp = 12345L;
    int ssrc = 67890;

    // Act
    List<RTPPacket> packets = serializer.serialize(frameData, mtu, timestamp, ssrc);

    // Assert
    assertTrue(packets.isEmpty(), "No packets should be generated for an empty frame.");
  }

  @Test
  void testSerializeFrameSmallerThanMTU() {
    // Arrange
    byte[] frameData = new byte[100]; // Frame smaller than MTU
    int mtu = 1400;
    long timestamp = 12345L;
    int ssrc = 67890;

    // Act
    List<RTPPacket> packets = serializer.serialize(frameData, mtu, timestamp, ssrc);

    // Assert
    assertEquals(1, packets.size(),
        "A single packet should be generated for frame smaller than MTU.");
    RTPPacket packet = packets.get(0);
    assertEquals(frameData.length, packet.getPayload().length,
        "Payload size should match frame data size.");
    assertArrayEquals(frameData, packet.getPayload(), "Payload data should match frame data.");
  }

  @Test
  void testSerializeLargeFrameExactMTUMultiple() {
    // Arrange
    byte[] frameData = new byte[2800]; // Frame size is an exact multiple of MTU
    int mtu = 1400;
    long timestamp = 12345L;
    int ssrc = 67890;

    // Act
    List<RTPPacket> packets = serializer.serialize(frameData, mtu, timestamp, ssrc);

    // Assert
    assertEquals(3, packets.size(), "Frame should be split into exactly two packets.");
    assertEquals(1388, packets.get(0).getPayload().length,
        "First packet payload size should match MTU minus header.");
    assertEquals(1388, packets.get(1).getPayload().length,
        "Second packet payload size should match MTU minus header.");
    assertEquals(24, packets.get(2).getPayload().length,
        "Second packet payload size should match MTU minus header.");
    assertFalse(packets.get(0).isMarker(),
        "Marker bit should not be set for the first packet of a frame.");
    assertFalse(packets.get(1).isMarker(),
        "Marker bit should not be set for the second packet of a frame.");
    assertTrue(packets.get(2).isMarker(),
        "Marker bit should be set for the last packet of a frame.");
  }
}
