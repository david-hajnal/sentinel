package space.hajnal.sentinel.network.receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.example.RTPPacket;
import space.hajnal.sentinel.network.RTPPacketDeserializer;
import space.hajnal.sentinel.network.ServerOptions;

@Slf4j
public class PacketReceiver implements AutoCloseable {

  private final DatagramSocket socket;
  private final BlockingQueue<RTPPacket> packetQueue;
  private volatile boolean running = true;
  private final ServerOptions serverOptions;
  private final RTPPacketDeserializer rtpPacketDeserializer;

  public PacketReceiver(DatagramSocket socket, int queueCapacity, ServerOptions serverOptions,
      RTPPacketDeserializer rtpPacketDeserializer) {
    this.socket = socket;
    this.packetQueue = new LinkedBlockingQueue<>(queueCapacity);
    this.serverOptions = serverOptions;
    this.rtpPacketDeserializer = rtpPacketDeserializer;
  }

  /**
   * Starts receiving packets from the network and puts them into the packet queue.
   * Blocks until the receiver is closed.
   */
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
    packetQueue.put(rtpPacket);
  }

  /**
   * Retrieves the next packet from the queue.
   * Blocks until a packet is available.
   *
   * @return The next packet
   * @throws InterruptedException If the thread is interrupted while waiting
   */
  public RTPPacket retrievePacket() throws InterruptedException {
    return packetQueue.take();
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
