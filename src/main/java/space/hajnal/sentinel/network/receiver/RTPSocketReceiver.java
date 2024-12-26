package space.hajnal.sentinel.network.receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.CanvasFrame;
import space.hajnal.sentinel.network.model.DelayedRTPPacket;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.video.FrameProcessor;
import space.hajnal.sentinel.stream.RTPStream;

@Slf4j
public class RTPSocketReceiver implements AutoCloseable {

  private DatagramSocket socket;
  private final DelayQueue<DelayedRTPPacket> packetQueue;
  private volatile boolean running = true;
  private final ServerOptions serverOptions;
  private final RTPPacketDeserializer rtpPacketDeserializer;
  private final long packetTtlMillis;

  public RTPSocketReceiver(ServerOptions serverOptions,
      RTPPacketDeserializer rtpPacketDeserializer, long packetTtlMillis) {
    this.packetQueue = new DelayQueue<>();
    this.serverOptions = serverOptions;
    this.rtpPacketDeserializer = rtpPacketDeserializer;
    this.packetTtlMillis = packetTtlMillis;
  }


  public void startReceiving(DatagramSocket socket) {
    this.socket = socket;
    while (running) {
      try {
        byte[] buffer = new byte[serverOptions.getMtu()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
       // RTPPacket rtpPacket = rtpPacketDeserializer.deserialize(packet); //seems like overkill
        RTPPacket rtpPacket = RTPPacket.fromBytes(buffer);
        //log.info("Received packet with timestamp: {}", rtpPacket.getTimestamp());
        putPacket(rtpPacket);

      } catch (IOException e) {
        if (running) {
          log.error("Error while receiving packets", e);
        }
      }
    }
  }

  void putPacket(RTPPacket rtpPacket) {
    DelayedRTPPacket delayedPacket = new DelayedRTPPacket(rtpPacket, packetTtlMillis);
    packetQueue.put(delayedPacket);
//    log.debug("Packet added to queue. Timestamp: {} Seq: {} / {}", rtpPacket.getTimestamp(),
//        rtpPacket.getSequenceNumber(), packetQueue.size());
  }

  public RTPPacket retrievePacket() throws InterruptedException {
    DelayedRTPPacket delayedPacket = packetQueue.take(); // Blocks until an unexpired packet is available
    return delayedPacket.getPacket();
  }

  @Override
  public void close() {
    running = false;
    if (socket != null && !socket.isClosed()) {
      socket.close();
    }
    log.info("PacketReceiver closed");
  }
}
