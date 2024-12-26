package space.hajnal.sentinel.network.model;

import java.nio.ByteBuffer;
import lombok.Getter;

@Getter
public class RTPPacket {

  public static final int BASE_HEADER_SIZE = 12;

  private int version = 2;
  private boolean padding = false;
  private boolean extension = false;
  private int csrcCount = 0;
  private boolean marker;
  private int payloadType;
  private int sequenceNumber;
  private long timestamp;
  private long ssrc;
  private byte[] payload;
  private byte[] header;  // Store the RTP header separately

  // Constructor for sending
  public RTPPacket(int payloadType, int sequenceNumber, long timestamp, long ssrc, byte[] payload,
      boolean marker) {
    this.payloadType = payloadType;
    this.sequenceNumber = sequenceNumber;
    this.timestamp = timestamp;
    this.ssrc = ssrc;
    this.payload = payload;
    this.marker = marker;
    this.header = constructHeader();
  }

  // Deserialize from raw bytes
  public RTPPacket(byte[] rawData) {
    if (rawData.length < BASE_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid RTP packet: insufficient data for header.");
    }

    ByteBuffer buffer = ByteBuffer.wrap(rawData);

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
  }

  public static RTPPacket fromBytes(byte[] packetData) {
    return new RTPPacket(packetData);
  }

  // Build header during construction
  private byte[] constructHeader() {
    ByteBuffer buffer = ByteBuffer.allocate(BASE_HEADER_SIZE);
    int firstByte = (version << 6) | (padding ? 0x20 : 0) | (extension ? 0x10 : 0) | csrcCount;
    int secondByte = (marker ? 0x80 : 0) | (payloadType & 0x7F);

    buffer.put((byte) firstByte);
    buffer.put((byte) secondByte);
    buffer.putShort((short) sequenceNumber);
    buffer.putInt((int) timestamp);
    buffer.putInt((int) ssrc);
    return buffer.array();
  }

  // Serialize to byte array (header + payload)
  public byte[] toBytes() {
    ByteBuffer buffer = ByteBuffer.allocate(BASE_HEADER_SIZE + payload.length);
    buffer.put(header);
    buffer.put(payload);
    return buffer.array();
  }

}
