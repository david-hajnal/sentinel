package space.hajnal.sentinel.network.sender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.codec.H264Encoder;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

@Slf4j
public class RTPSocketSender implements AutoCloseable {

  private final ServerOptions serverOptions;
  private final H264Encoder h264Encoder;
  private final RTPPacketSerializer rtpPacketSerializer;
  private DatagramSocket socket;

  public RTPSocketSender(ServerOptions serverOptions, H264Encoder h264Encoder,
      RTPPacketSerializer rtpPacketSerializer) {
    this.serverOptions = serverOptions;
    this.h264Encoder = h264Encoder;
    this.rtpPacketSerializer = rtpPacketSerializer;
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

  public void send(Frame frame, long timestamp) {
    if (socket == null || socket.isClosed()) {
      throw new IllegalStateException("Socket is not open. Call open() before sending frames.");
    }

    try {
      int ssrc = 123456;
      byte[] frameData = h264Encoder.encode(frame);
      int mtu = serverOptions.getMtu();
      List<RTPPacket> rtpPackets = rtpPacketSerializer.serialize(frameData, mtu, timestamp, ssrc);

      rtpPackets.forEach(this::send);

    } catch (Exception e) {
      log.error("Failed to send frame", e);
    }
  }

  private void send(RTPPacket rtpPacket) {
    try {
      //log.debug("Sending RTP packet with timestamp: {}", rtpPacket.getTimestamp());
      socket.send(new DatagramPacket(rtpPacket.toBytes(), rtpPacket.toBytes().length,
          InetAddress.getByName(serverOptions.getServerAddress()),
          serverOptions.getServerPort()));
    } catch (IOException e) {
      log.error("Failed to send RTP packet", e);
    }
  }

  @Override
  public void close() {
    if (socket != null && !socket.isClosed()) {
      socket.close();
      log.info("RTPStream socket closed");
    }
  }
}
