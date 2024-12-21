package space.hajnal.sentinel.network.video;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;

@Slf4j
public class VideoStreamProcessor {

  private final Map<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new ConcurrentHashMap<>();
  private final Map<Long, Long> lastArrivalTimeByTimestamp = new ConcurrentHashMap<>();
  private final List<FrameListener> subscribers = new CopyOnWriteArrayList<>();
  private final FrameProcessor frameProcessor;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final double frameIntervalMillis;  // Interval for frame assembly (e.g., 33.3ms for 30 fps)
  private volatile double jitter = 0;
  private static final double JITTER_ALPHA = 0.125;  // Jitter smoothing factor
  private volatile long lastAssembledTimestamp = -1; // Track last assembled timestamp

  public VideoStreamProcessor(FrameProcessor frameProcessor) {
    this(frameProcessor, 30); // Default 30 FPS
  }

  public VideoStreamProcessor(FrameProcessor frameProcessor, int fps) {
    this.frameProcessor = frameProcessor;
    this.frameIntervalMillis = 1000.0 / fps;

    // Initialize lastAssembledTimestamp to the earliest frame (if available)
    initializeFrameAssembly();

    // Start frame assembly scheduler at fixed rate
    scheduler.scheduleAtFixedRate(this::assembleNextFrame, 0, (long) frameIntervalMillis,
        TimeUnit.MILLISECONDS);
  }

  private void initializeFrameAssembly() {
    if (!frameBufferByTimestamp.isEmpty()) {
      // Set to the earliest frame in the buffer, but don't trigger immediate assembly
      lastAssembledTimestamp = frameBufferByTimestamp.keySet().stream()
          .min(Long::compare)
          .orElse(-1L);

      log.info("Starting frame assembly from timestamp: {}", lastAssembledTimestamp);
    } else {
      // If buffer is empty, wait for incoming packets
      log.info("No initial frames available. Waiting for incoming packets.");
      lastAssembledTimestamp = -1L;
    }
  }

  public void processPacket(RTPPacket rtpPacket) {
    long timestamp = rtpPacket.getTimestamp();
    int sequenceNumber = rtpPacket.getSequenceNumber();
    long arrivalTime = System.currentTimeMillis();

    log.debug("Received packet: Timestamp={} Seq={}", timestamp, sequenceNumber);

    calculateJitter(timestamp, arrivalTime);

    frameBufferByTimestamp
        .computeIfAbsent(timestamp, k -> new TreeMap<>())
        .put(sequenceNumber, rtpPacket.getPayload());
  }

  private void calculateJitter(long timestamp, long arrivalTime) {
    Long previousArrivalTime = lastArrivalTimeByTimestamp.put(timestamp, arrivalTime);
    if (previousArrivalTime != null) {
      long interarrivalDifference = Math.abs(arrivalTime - previousArrivalTime);
      double jitterSample = Math.abs(interarrivalDifference - jitter);
      jitter += JITTER_ALPHA * (jitterSample - jitter);

      log.debug("Updated jitter: {} ms (Sample: {} ms)", jitter, jitterSample);
    }
  }

  private void assembleNextFrame() {
    if (frameBufferByTimestamp.isEmpty()) {
      log.debug("No frames available for assembly.");
      return;
    }

    long nextTimestamp = frameBufferByTimestamp.keySet().stream()
        .filter(ts -> ts > lastAssembledTimestamp)
        .findFirst()
        .orElse(-1L);

    if (nextTimestamp == -1) {
      log.debug("No new frames to assemble.");
      return;
    }

    long jitterCompensation = Math.round(jitter);  // Apply jitter adjustment
    long adjustedDelay = (long) (
        Math.max(frameIntervalMillis, frameIntervalMillis + jitterCompensation));

    log.debug("Assembling frame for timestamp: {} (Jitter: {} ms, Delay: {} ms)",
        nextTimestamp, jitterCompensation, adjustedDelay);

    // Schedule the frame assembly with adjusted delay
    scheduler.schedule(() -> assembleFrame(nextTimestamp), adjustedDelay, TimeUnit.MILLISECONDS);
  }

  private void assembleFrame(long timestamp) {
    SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.remove(timestamp);
    if (packets == null) {
      log.error("No packets found for timestamp: {}", timestamp);
      return;
    }

    int firstSeq = packets.firstKey();
    int lastSeq = packets.lastKey();

    for (int seq = firstSeq; seq <= lastSeq; seq++) {
      if (!packets.containsKey(seq)) {
        long missingThreshold = Math.round(jitter * 1.5);  // Allow late arrival within 1.5x jitter
        log.warn("Packet Seq={} missing. Jitter threshold={}ms", seq, missingThreshold);
      }
    }

    byte[] frame = frameProcessor.reassembleFrame(packets);
    if (frame != null) {
      lastAssembledTimestamp = timestamp;
      notifySubscribers(frame);
    }
  }


  public void addSubscriber(FrameListener listener) {
    subscribers.add(listener);
  }

  private void notifySubscribers(byte[] frame) {
    for (FrameListener listener : subscribers) {
      listener.onFrameAvailable(frame);
    }
  }

  public interface FrameListener {

    void onFrameAvailable(byte[] frame);
  }
}
