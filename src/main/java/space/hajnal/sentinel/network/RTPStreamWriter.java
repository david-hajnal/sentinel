package space.hajnal.sentinel.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.camera.SentinelFrameGrabber;
import space.hajnal.sentinel.network.sender.RTPSocketSender;

@Slf4j
public class RTPStreamWriter implements AutoCloseable {

  private final RTPSocketSender rtpSocketSender;
  private final SentinelFrameGrabber sentinelFrameGrabber;
  private final ExecutorService threadPool;

  public RTPStreamWriter(RTPSocketSender rtpSocketSender, SentinelFrameGrabber sentinelFrameGrabber,
      ExecutorService threadPool) {
    this.rtpSocketSender = rtpSocketSender;
    this.sentinelFrameGrabber = sentinelFrameGrabber;
    this.threadPool = threadPool;
  }

  public void start(DatagramSocket socket) {
    rtpSocketSender.open(socket);
    threadPool.submit(() -> {
      try {
        log.info("Starting RTPStreamWriter");
        captureFrames();
      } catch (Exception e) {
        log.error("Error while capturing frames", e);
      }
    });
  }

  private void captureFrames() {
    try {
      sentinelFrameGrabber.capture((frame, ts) -> {
        rtpSocketSender.send(frame, ts);
        return frame;
      });
    } catch (Exception e) {
      log.error("Error while capturing frames", e);
    }
  }

  @Override
  public void close() throws Exception {
    try {
      sentinelFrameGrabber.close();
    } catch (Exception e) {
      log.error("Error while closing SentinelFrameGrabber", e);
    }
    threadPool.shutdown();
  }

}
