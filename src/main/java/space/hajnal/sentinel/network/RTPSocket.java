package space.hajnal.sentinel.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.example.RTPPacket;
import space.hajnal.sentinel.codec.H264Encoder;

@Slf4j
public class RTPSocket implements AutoCloseable {

  private final ServerOptions serverOptions;
  private final H264Encoder h264Encoder;
  private DatagramSocket socket;

  public RTPSocket(ServerOptions serverOptions, H264Encoder h264Encoder) {
    this.serverOptions = serverOptions;
    this.h264Encoder = h264Encoder;
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
      int sequenceNumber = 0;
      int ssrc = 123456;

      byte[] frameData = h264Encoder.encode(frame);

      int mtu = serverOptions.getMtu();
      for (int offset = 0; offset < frameData.length; offset += mtu) {
        int payloadSize = Math.min(mtu, frameData.length - offset);
        byte[] payload = new byte[payloadSize];
        System.arraycopy(frameData, offset, payload, 0, payloadSize);

        boolean marker = (offset + payloadSize == frameData.length);
        RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload,
            marker);

        socket.send(new DatagramPacket(rtpPacket.toBytes(), rtpPacket.toBytes().length,
            InetAddress.getByName(serverOptions.getServerAddress()),
            serverOptions.getServerPort()));
      }

    } catch (Exception e) {
      log.error("Failed to send frame", e);
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
