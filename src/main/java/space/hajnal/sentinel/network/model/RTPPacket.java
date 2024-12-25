package space.hajnal.sentinel.network.model;

import java.nio.ByteBuffer;

public class RTPPacket {

  private static final int BASE_HEADER_SIZE = 12;

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

  // Constructor for creating an RTP packet for sending
  public RTPPacket(int payloadType, int sequenceNumber, long timestamp, long ssrc, byte[] payload,
      boolean marker) {
    this.payloadType = payloadType;
    this.sequenceNumber = sequenceNumber;
    this.timestamp = timestamp;
    this.ssrc = ssrc;
    this.payload = payload;
    this.marker = marker;
  }

  // Constructor for creating an RTP packet for receiving (from raw bytes)
  public RTPPacket(byte[] rawData) {
    if (rawData.length < BASE_HEADER_SIZE) {
      throw new IllegalArgumentException("Invalid RTP packet: insufficient data for header.");
    }

    ByteBuffer buffer = ByteBuffer.wrap(rawData);

    int firstByte = Byte.toUnsignedInt(buffer.get());
    version = (firstByte >> 6) & 0x03;
    padding = (firstByte & 0x20) != 0;
    extension = (firstByte & 0x10) != 0;
    csrcCount = firstByte & 0x0F;

    int secondByte = Byte.toUnsignedInt(buffer.get());
    marker = (secondByte & 0x80) != 0;
    payloadType = secondByte & 0x7F;

    sequenceNumber = Short.toUnsignedInt(buffer.getShort());
    timestamp = Integer.toUnsignedLong(buffer.getInt());
    ssrc = Integer.toUnsignedLong(buffer.getInt());

    // CSRC handling
    int actualHeaderSize = BASE_HEADER_SIZE + (csrcCount * 4);
    buffer.position(actualHeaderSize);

    // Handle padding
    int paddingBytes = 0;
    if (padding && rawData.length > actualHeaderSize) {
      paddingBytes = Byte.toUnsignedInt(rawData[rawData.length - 1]);
    }

    // Extract payload
    int payloadLength = rawData.length - actualHeaderSize - paddingBytes;
    if (payloadLength < 0) {
      throw new IllegalArgumentException("Invalid RTP packet: negative payload length.");
    }

    payload = new byte[payloadLength];
    buffer.get(payload, 0, payloadLength);
  }

  public static RTPPacket fromBytes(byte[] rawData) {
    return new RTPPacket(rawData);
  }

  // Serialize RTP packet to bytes for sending
  public byte[] toBytes() {
    int actualHeaderSize = BASE_HEADER_SIZE + csrcCount * 4;
    ByteBuffer buffer = ByteBuffer.allocate(actualHeaderSize + payload.length);

    // Build RTP header
    int firstByte = (version << 6) | (padding ? 0x20 : 0) | (extension ? 0x10 : 0) | csrcCount;
    buffer.put((byte) firstByte);

    int secondByte = (marker ? 0x80 : 0) | (payloadType & 0x7F);
    buffer.put((byte) secondByte);

    buffer.putShort((short) sequenceNumber);
    buffer.putInt((int) timestamp);
    buffer.putInt((int) ssrc);

    // Add payload
    buffer.put(payload);

    // Add padding if enabled
    if (padding) {
      buffer.put((byte) (actualHeaderSize + payload.length));
    }

    return buffer.array();
  }

  // Getters for accessing packet data
  public int getPayloadType() {
    return payloadType;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getSsrc() {
    return ssrc;
  }

  public byte[] getPayload() {
    return payload;
  }

  public boolean isMarker() {
    return marker;
  }

  @Override
  public String toString() {
    return "RTPPacket{" +
           "version=" + version +
           ", padding=" + padding +
           ", extension=" + extension +
           ", csrcCount=" + csrcCount +
           ", marker=" + marker +
           ", payloadType=" + payloadType +
           ", sequenceNumber=" + sequenceNumber +
           ", timestamp=" + timestamp +
           ", ssrc=" + ssrc +
           ", payloadLength=" + payload.length +
           '}';
  }
}
