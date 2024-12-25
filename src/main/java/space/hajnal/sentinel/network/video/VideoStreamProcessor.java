package space.hajnal.sentinel.network.video;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.camera.model.SentinelFrame;
import space.hajnal.sentinel.network.model.RTPPacket;

@Slf4j
public class VideoStreamProcessor {

  private final Map<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new ConcurrentHashMap<>();
  private final Map<Long, Long> lastArrivalTimeByTimestamp = new ConcurrentHashMap<>();
  private final List<FrameListener> subscribers = new CopyOnWriteArrayList<>();
  private final FrameProcessor frameProcessor;
  private final ScheduledExecutorService scheduler;
  private final double frameIntervalMillis;  // Interval for frame assembly (e.g., 33.3ms for 30 fps)
  private final AtomicReference<Double> jitter = new AtomicReference<>(0.0);
  private static final double JITTER_ALPHA = 0.125;  // Jitter smoothing factor
  private volatile long lastAssembledTimestamp = -1; // Track last assembled timestamp

  public VideoStreamProcessor(FrameProcessor frameProcessor) {
    this(frameProcessor, 30, Executors.newScheduledThreadPool(2));
  }

  public VideoStreamProcessor(FrameProcessor frameProcessor, int fps,
      ScheduledExecutorService scheduler) {
    this.frameProcessor = frameProcessor;
    this.frameIntervalMillis = 1000.0 / fps;
    this.scheduler = scheduler;

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

    // log.debug("Received packet: Timestamp={} Seq={}", timestamp, sequenceNumber);

    calculateJitter(timestamp, arrivalTime);

    frameBufferByTimestamp
        .computeIfAbsent(timestamp, k -> new TreeMap<>())
        .put(sequenceNumber, rtpPacket.getPayload());
  }

  private void calculateJitter(long timestamp, long arrivalTime) {
    Long previousArrivalTime = lastArrivalTimeByTimestamp.put(timestamp, arrivalTime);
    if (previousArrivalTime != null) {
      long interarrivalDifference = Math.abs(arrivalTime - previousArrivalTime);
      jitter.updateAndGet(j -> {
        double jitterSample = Math.abs(interarrivalDifference - j);
        return j + JITTER_ALPHA * (jitterSample - j);
      });

      //log.debug("Updated jitter: {} ms", jitter);
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

    long jitterCompensation = Math.round(jitter.get());  // Apply jitter adjustment
    long adjustedDelay = (long) (
        Math.max(frameIntervalMillis, frameIntervalMillis + jitterCompensation));

    log.debug("Assembling frame for timestamp: {} (Jitter: {} ms, Delay: {} ms) with {} packets",
        nextTimestamp, jitterCompensation, adjustedDelay,
        frameBufferByTimestamp.get(nextTimestamp).size());

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
    byte[] lastGoodPacket = null;
    boolean frameComplete = true;

    // Reorder and handle missing packets
    for (int seq = firstSeq; seq <= lastSeq; seq++) {
      if (!packets.containsKey(seq)) {
        long missingThreshold = Math.round(jitter.get() * 1.5);  // Allow packet arrival within jitter range
        long timeSinceLastPacket = System.currentTimeMillis() - lastArrivalTimeByTimestamp.getOrDefault(timestamp, 0L);

        // Error concealment: Duplicate last packet if within jitter threshold
        if (timeSinceLastPacket <= missingThreshold && lastGoodPacket != null) {
          log.warn("Packet Seq={} missing. Using previous packet for concealment.", seq);
          packets.put(seq, lastGoodPacket);  // Duplicate last good packet
        } else {
          log.error("Packet Seq={} missing beyond jitter threshold. Skipping frame.", seq);
          frameComplete = false;
          break;
        }
      }
      lastGoodPacket = packets.get(seq);
    }

    log.debug(lastGoodPacket == null ? "Last good packet is null" : "Last good packet is not null");

    // Assemble the frame if complete
    if (frameComplete) {
      byte[] frame = frameProcessor.reassembleFrame(packets);
       frameProcessor.compareSentAndReceivedPackets(packets, timestamp);
      if (frame != null) {
        lastAssembledTimestamp = timestamp;
        notifySubscribers(SentinelFrame.builder().data(frame).timestamp(timestamp).build());
      } else {
        log.error("Failed to assemble frame for timestamp: {}", timestamp);
      }
    } else {
      log.warn("Frame skipped for timestamp: {} due to excessive packet loss.", timestamp);
    }
  }

  public void addSubscriber(FrameListener listener) {
    subscribers.add(listener);
  }

  void notifySubscribers(SentinelFrame frame) {
    for (FrameListener listener : subscribers) {
      listener.onFrameAvailable(frame);
    }
  }

  SortedMap<Integer, byte[]> getFramesByTimestamp(long timestamp) {
    return frameBufferByTimestamp.get(timestamp);
  }

  public interface FrameListener {

    void onFrameAvailable(SentinelFrame frame);
  }
}
