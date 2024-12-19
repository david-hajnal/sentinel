package space.hajnal.sentinel.network.receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.DelayQueue;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.DelayedRTPPacket;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;

@Slf4j
public class PacketReceiver implements AutoCloseable {

  private final DatagramSocket socket;
  private final DelayQueue<DelayedRTPPacket> packetQueue;
  private volatile boolean running = true;
  private final ServerOptions serverOptions;
  private final RTPPacketDeserializer rtpPacketDeserializer;
  private final long packetTtlMillis;

  public PacketReceiver(DatagramSocket socket, ServerOptions serverOptions,
      RTPPacketDeserializer rtpPacketDeserializer, long packetTtlMillis) {
    this.socket = socket;
    this.packetQueue = new DelayQueue<>();
    this.serverOptions = serverOptions;
    this.rtpPacketDeserializer = rtpPacketDeserializer;
    this.packetTtlMillis = packetTtlMillis;
  }

  public void startReceiving() {
    while (running) {
      try {
        byte[] buffer = new byte[serverOptions.getMtu()];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        RTPPacket rtpPacket = rtpPacketDeserializer.deserialize(packet);
        putPacket(rtpPacket);
      } catch (IOException e) {
        if (running) {
          log.error("Error while receiving packets", e);
        }
      } catch (InterruptedException e) {
        log.error("Thread interrupted while receiving packets", e);
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  void putPacket(RTPPacket rtpPacket) throws InterruptedException {
    DelayedRTPPacket delayedPacket = new DelayedRTPPacket(rtpPacket, packetTtlMillis);
    packetQueue.put(delayedPacket);
  }

  public RTPPacket retrievePacket() throws InterruptedException {
    DelayedRTPPacket delayedPacket = packetQueue.take(); // Blocks until an unexpired packet is available
    return delayedPacket.getPacket();
  }

  @Override
  public void close() {
    running = false;
    if (!socket.isClosed()) {
      socket.close();
    }
    log.info("PacketReceiver closed");
  }
}
