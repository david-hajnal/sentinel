package space.hajnal.sentinel.network;

import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.receiver.RTPSocketReceiver;
import space.hajnal.sentinel.network.video.VideoStreamProcessor;

@Slf4j
public class RTPStreamReader implements AutoCloseable {

  private final VideoStreamProcessor videoStreamProcessor;
  private final RTPSocketReceiver RTPSocketReceiver;
  private final ExecutorService threadPool;

  public RTPStreamReader(VideoStreamProcessor videoStreamProcessor, RTPSocketReceiver rtpSocketReceiver,
      ExecutorService threadPool) {
    this.videoStreamProcessor = videoStreamProcessor;
    this.RTPSocketReceiver = rtpSocketReceiver;
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
          RTPPacket rtpPacket = RTPSocketReceiver.retrievePacket(); // Block until a packet is available
          //log.debug("Processing packet with timestamp: {} Seq: {}", rtpPacket.getTimestamp(), rtpPacket.getSequenceNumber());
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
        RTPSocketReceiver.startReceiving(socket);
      } catch (Exception e) {
        log.error("Error while receiving packets", e);
      }
    });
  }

  @Override
  public void close() {
    RTPSocketReceiver.close();
    threadPool.shutdown();
  }
}
