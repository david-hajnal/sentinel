package space.hajnal.sentinel.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.codec.H264Encoder;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;

@Slf4j
@Deprecated
public class RTPSocketReceiver implements AutoCloseable {

  private final ServerOptions serverOptions;
  private final H264Encoder h264Encoder;
  private final RTPPacketDeserializer rtpPacketDeserializer;
  private DatagramSocket socket;
  private boolean running = true;
  private final SortedMap<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new TreeMap<>();

  public RTPSocketReceiver(ServerOptions serverOptions, H264Encoder h264Encoder,
      RTPPacketDeserializer rtpPacketDeserializer) {
    this.serverOptions = serverOptions;
    this.h264Encoder = h264Encoder;
    this.rtpPacketDeserializer = rtpPacketDeserializer;
  }

  public void receive() throws IOException {
    byte[] buffer = new byte[serverOptions.getMtu()];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

    while (running) {
      socket.receive(packet);
      byte[] packetData = new byte[packet.getLength()];
      System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
      RTPPacket rtpPacket = RTPPacket.fromBytes(packetData);

      long timestamp = rtpPacket.getTimestamp();
      int sequenceNumber = rtpPacket.getSequenceNumber();

      // Add packet to the timestamp-based buffer
      frameBufferByTimestamp.computeIfAbsent(timestamp, k -> new TreeMap<>())
          .put(sequenceNumber, rtpPacket.getPayload());

      System.out.println(
          "Buffered RTP packet: Timestamp=" + timestamp + ", SequenceNumber=" + sequenceNumber);

      // Trigger reassembly if marker packet is received
      if (rtpPacket.isMarker()) {
        processFrameBuffer(timestamp);
      }
    }
  }

  private void processFrameBuffer(long timestamp) {
    SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.get(timestamp);
    if (packets == null) {
      return;
    }

    // Check if the frame is complete
    int firstSeq = packets.firstKey();
    int lastSeq = packets.lastKey();
    for (int seq = firstSeq; seq <= lastSeq; seq++) {
      if (!packets.containsKey(seq)) {
        System.out.println("Missing packet: SequenceNumber=" + seq + " for Timestamp=" + timestamp);
        processFrameBuffer(timestamp); // Retry processing
        return;
      }
    }

    // Reassemble frame
    byte[] frameData = reassembleFrame(packets);
    if (frameData != null) {
      frameBufferByTimestamp.remove(timestamp); // Remove processed frame
    }
  }

  private byte[] reassembleFrame(SortedMap<Integer, byte[]> packets) {
    int totalLength = packets.values().stream().mapToInt(p -> p.length).sum();
    byte[] completeFrame = new byte[totalLength];
    int offset = 0;

    for (byte[] payload : packets.values()) {
      System.arraycopy(payload, 0, completeFrame, offset, payload.length);
      offset += payload.length;
    }

    System.out.println("Reassembled frame size: " + completeFrame.length);
    return completeFrame;
  }

  public void open(DatagramSocket socket) {
    try {
      if (socket == null) {
        throw new IllegalArgumentException("Socket cannot be null");
      }

      if (this.socket != null && !this.socket.isClosed()) {
        throw new IllegalArgumentException("Socket is must be closed");
      }
      this.socket = socket;
      socket.setSendBufferSize(65536);
      log.info("RTPStream socket opened");
    } catch (Exception e) {
      log.error("Failed to open DatagramSocket", e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close() throws Exception {
    running = false;
    if (socket != null && !socket.isClosed()) {
      socket.close();
      log.info("RTPStream socket closed");
    }

  }
}
