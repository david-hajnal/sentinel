package space.hajnal.sentinel.network;

import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.receiver.PacketReceiver;

@Slf4j
public class RTPStreamReader implements AutoCloseable {

  private final FrameProcessor frameProcessor;
  private final PacketReceiver packetReceiver;
  private final ExecutorService threadPool;

  public RTPStreamReader(FrameProcessor frameProcessor, PacketReceiver packetReceiver,
      ExecutorService threadPool) {
    this.frameProcessor = frameProcessor;
    this.packetReceiver = packetReceiver;
    this.threadPool = threadPool;
  }

  public void start() {
    startReceiving();
    startProcessing();
  }

  private void startProcessing() {
    threadPool.submit(() -> {
      try {
        RTPPacket rtpPacket = packetReceiver.retrievePacket();
        frameProcessor.processPacket(rtpPacket);
      } catch (Exception e) {
        log.error("Error while processing packets", e);
      }
    });
  }

  private void startReceiving() {
    threadPool.submit(() -> {
      try {
        packetReceiver.startReceiving();
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
