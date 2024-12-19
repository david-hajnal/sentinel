package space.hajnal.sentinel.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import space.hajnal.sentinel.network.model.RTPPacket;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;

class RTPPacketDeserializerTest {

  @Test
  void testDeserialize_ValidRTPPacket() {
    // Arrange: Create a valid RTPPacket and serialize it to bytes
    int payloadType = 96;
    int sequenceNumber = 12345;
    long timestamp = 67890L;
    long ssrc = 123456789L;
    byte[] payload = new byte[]{0x01, 0x02, 0x03, 0x04};
    RTPPacket originalPacket = new RTPPacket(payloadType, sequenceNumber, timestamp, ssrc, payload,
        true);
    byte[] packetBytes = originalPacket.toBytes();

    // Wrap the bytes in a DatagramPacket
    DatagramPacket datagramPacket = new DatagramPacket(packetBytes, packetBytes.length);

    RTPPacketDeserializer deserializer = new RTPPacketDeserializer();

    // Act: Deserialize the DatagramPacket into an RTPPacket
    RTPPacket deserializedPacket = deserializer.deserialize(datagramPacket);

    // Assert: Validate the deserialized RTPPacket matches the original
    assertNotNull(deserializedPacket, "Deserialized packet should not be null");
    assertEquals(originalPacket.getPayloadType(), deserializedPacket.getPayloadType(),
        "Payload type mismatch");
    assertEquals(originalPacket.getSequenceNumber(), deserializedPacket.getSequenceNumber(),
        "Sequence number mismatch");
    assertEquals(originalPacket.getTimestamp(), deserializedPacket.getTimestamp(),
        "Timestamp mismatch");
    assertEquals(originalPacket.getSsrc(), deserializedPacket.getSsrc(), "SSRC mismatch");
    assertArrayEquals(originalPacket.getPayload(), deserializedPacket.getPayload(),
        "Payload mismatch");
    assertEquals(originalPacket.isMarker(), deserializedPacket.isMarker(), "Marker bit mismatch");
  }

  @Test
  void testDeserialize_InvalidRTPPacket_ThrowsException() {
    // Arrange: Create invalid packet data (less than RTP header size)
    byte[] invalidData = new byte[]{0x01, 0x02, 0x03}; // Insufficient data for RTP header
    DatagramPacket datagramPacket = new DatagramPacket(invalidData, invalidData.length);

    RTPPacketDeserializer deserializer = new RTPPacketDeserializer();

    // Act & Assert: Expect an IllegalArgumentException due to invalid RTP packet
    assertThrows(IllegalArgumentException.class, () -> deserializer.deserialize(datagramPacket),
        "Expected exception for invalid RTP packet data");
  }

  @Test
  void testDeserialize_EmptyDatagramPacket_ThrowsException() {
    // Arrange: Create an empty DatagramPacket
    byte[] emptyData = new byte[0];
    DatagramPacket datagramPacket = new DatagramPacket(emptyData, emptyData.length);

    RTPPacketDeserializer deserializer = new RTPPacketDeserializer();

    // Act & Assert: Expect an IllegalArgumentException for empty packet
    assertThrows(IllegalArgumentException.class, () -> deserializer.deserialize(datagramPacket),
        "Expected exception for empty DatagramPacket");
  }

  @Test
  void testDeserialize_PartialPayload_DeserializesCorrectly() {
    // Arrange: Create a valid RTPPacket with partial payload
    int payloadType = 96;
    int sequenceNumber = 54321;
    long timestamp = 12345L;
    long ssrc = 987654321L;
    byte[] payload = new byte[]{0x05, 0x06, 0x07};
    RTPPacket originalPacket = new RTPPacket(payloadType, sequenceNumber, timestamp, ssrc, payload,
        false);
    byte[] packetBytes = originalPacket.toBytes();

    // Truncate the payload in the DatagramPacket
    DatagramPacket datagramPacket = new DatagramPacket(packetBytes, packetBytes.length - 5);

    RTPPacketDeserializer deserializer = new RTPPacketDeserializer();

    // Act & Assert: Validate deserialization with truncated payload
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> deserializer.deserialize(datagramPacket));
    System.out.println(exception.getMessage());
    assertTrue(exception.getMessage().contains("Invalid RTP packet: insufficient data for header."),
        "Expected error message for negative payload length");
  }
}
