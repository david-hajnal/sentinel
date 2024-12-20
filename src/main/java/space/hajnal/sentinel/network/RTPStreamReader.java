package space.hajnal.sentinel.network;

import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.receiver.PacketReceiver;
import space.hajnal.sentinel.network.video.VideoStreamProcessor;

@Slf4j
public class RTPStreamReader implements AutoCloseable {

  private final VideoStreamProcessor videoStreamProcessor;
  private final PacketReceiver packetReceiver;
  private final ExecutorService threadPool;

  public RTPStreamReader(VideoStreamProcessor videoStreamProcessor, PacketReceiver packetReceiver,
      ExecutorService threadPool) {
    this.videoStreamProcessor = videoStreamProcessor;
    this.packetReceiver = packetReceiver;
    this.threadPool = threadPool;
  }

  public void start(DatagramSocket socket) {
    startReceiving(socket);
    startProcessing();
  }

  private void startProcessing() {
    log.info("Start processing packets");
    threadPool.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          RTPPacket rtpPacket = packetReceiver.retrievePacket(); // Block until a packet is available
          log.debug("Processing packet with timestamp: {}", rtpPacket.getTimestamp());
          videoStreamProcessor.processPacket(rtpPacket);
        } catch (InterruptedException e) {
          log.info("Processing interrupted, shutting down");
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          log.error("Error while processing packets", e);
        }
      }
    });
  }


  private void startReceiving(DatagramSocket socket) {
    log.info("Starting RTPStreamReader");
    threadPool.submit(() -> {
      try {
        packetReceiver.startReceiving(socket);
      } catch (Exception e) {
        log.error("Error while receiving packets", e);
      }
    });
  }

  @Override
  public void close() {
    packetReceiver.close();
    threadPool.shutdown();
  }
}
