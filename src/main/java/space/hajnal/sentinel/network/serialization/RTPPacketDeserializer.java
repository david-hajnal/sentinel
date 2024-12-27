package space.hajnal.sentinel.network.serialization;

import static space.hajnal.sentinel.network.model.RTPPacket.BASE_HEADER_SIZE;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import space.hajnal.sentinel.network.model.RTPPacket;

public class RTPPacketDeserializer {

  /**
   * Deserializes an RTP packet from a DatagramPacket.
   *
   * @param packet The DatagramPacket to deserialize
   * @return The deserialized RTP packet
   */
  @Deprecated
  public RTPPacket deserialize(DatagramPacket packet) {
    return this.deserialize(packet.getData());
  }

  /**
   * Deserializes an RTP packet from a byte array.
   *
   * @param The raw data to deserialize
   * @return The deserialized RTP packet
   */
  public RTPPacket deserialize(byte[] rawData) {
    if (rawData.length < BASE_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid RTP packet: insufficient data for header.");
    }
    ByteBuffer buffer = ByteBuffer.wrap(rawData);

    int version;
    boolean padding;
    boolean extension;
    int csrcCount = 0;
    boolean marker;
    int payloadType;
    int sequenceNumber;
    long timestamp;
    long ssrc;
    byte[] payload;
    byte[] header;  // Store the RTP header separately

    // Extract header bytes
    header = new byte[BASE_HEADER_SIZE];
    buffer.get(header);  // Read the first 12 bytes (header)

    int firstByte = Byte.toUnsignedInt(header[0]);
    version = (firstByte >> 6) & 0x03;
    padding = (firstByte & 0x20) != 0;
    extension = (firstByte & 0x10) != 0;
    csrcCount = firstByte & 0x0F;

    int secondByte = Byte.toUnsignedInt(header[1]);
    marker = (secondByte & 0x80) != 0;
    payloadType = secondByte & 0x7F;

    sequenceNumber = Short.toUnsignedInt(ByteBuffer.wrap(header, 2, 2).getShort());
    timestamp = Integer.toUnsignedLong(ByteBuffer.wrap(header, 4, 4).getInt());
    ssrc = Integer.toUnsignedLong(ByteBuffer.wrap(header, 8, 4).getInt());

    // Extract payload
    int payloadLength = rawData.length - BASE_HEADER_SIZE;
    payload = new byte[payloadLength];
    buffer.get(payload);

    return new RTPPacket(version, padding, extension, csrcCount, marker, payloadType,
        sequenceNumber,
        timestamp, ssrc, payload, header);
  }

}

