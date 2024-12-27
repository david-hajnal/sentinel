package space.hajnal.sentinel.network.video;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.camera.model.SentinelFrame;
import space.hajnal.sentinel.network.model.RTPPacket;

@Slf4j
public class VideoStreamProcessor {

  private static final double JITTER_ALPHA = 0.125;  // Jitter smoothing factor
  private final Map<Long, SortedMap<Integer, RTPPacket>> frameBufferByTimestamp = new ConcurrentHashMap<>();
  private final Map<Long, Long> lastArrivalTimeByTimestamp = new ConcurrentHashMap<>();
  private final List<FrameListener> subscribers = new CopyOnWriteArrayList<>();
  private final FrameProcessor frameProcessor;
  private final ScheduledExecutorService scheduler;
  private final double frameIntervalMillis;  // Interval for frame assembly (e.g., 33.3ms for 30 fps)
  private final AtomicReference<Double> jitter = new AtomicReference<>(0.0);
  private final AtomicLong lastAssembledTimestamp = new AtomicLong(
      -1); // Track last assembled timestamp

  public VideoStreamProcessor(FrameProcessor frameProcessor, int fps,
      ScheduledExecutorService scheduler) {
    this.frameProcessor = frameProcessor;
    this.frameIntervalMillis = 1000.0 / fps;
    this.scheduler = scheduler;

    initializeFrameAssembly();

    // Start with dynamic scheduling (instead of fixed rate)
    scheduler.schedule(this::assembleNextFrame, 0, TimeUnit.MILLISECONDS);
  }

  // Dynamic scheduling for better jitter handling
  private void assembleNextFrame() {
    if (frameBufferByTimestamp.isEmpty()) {
      log.debug("No frames available. Forcing frame forward.");
      assembleFrame(lastAssembledTimestamp.get());
    }

    long nextTimestamp = frameBufferByTimestamp.keySet().stream()
        .filter(ts -> ts >= lastAssembledTimestamp.get())
        .findFirst()
        .orElse(lastAssembledTimestamp.get());

    long jitterCompensation = Math.round(jitter.get());
    long adjustedDelay = (long) Math.max(frameIntervalMillis,
        frameIntervalMillis + jitterCompensation);

    log.debug("Scheduling frame assembly for timestamp: {} with adjusted delay: {} ms",
        nextTimestamp, adjustedDelay);

    // Dynamically reschedule after frame assembly
    scheduler.schedule(() -> {
      assembleFrame(nextTimestamp);
      assembleNextFrame();  // Recurse for the next frame
    }, adjustedDelay, TimeUnit.MILLISECONDS);
  }

  // Enforce timeout to avoid buffer starvation
  private void assembleFrame(long timestamp) {
    SortedMap<Integer, RTPPacket> packets = frameBufferByTimestamp.remove(timestamp);
    if (packets == null || packets.isEmpty()) {
      log.warn("Frame timeout or no packets for timestamp: {}. Skipping.", timestamp);
      long next = frameBufferByTimestamp.keySet().stream()
          .findFirst()
          .orElse(timestamp + 1);  // Increment to avoid reassembling the same frame
      lastAssembledTimestamp.set(next);
      return;
    }

    int firstSeq = packets.firstKey();
    int lastSeq = packets.lastKey();
    RTPPacket lastGoodPacket = null;
    boolean frameComplete = true;

    for (int seq = firstSeq; seq <= lastSeq; seq++) {
      if (!packets.containsKey(seq)) {
        long missingThreshold = Math.round(jitter.get() * 1.5);
        long timeSinceLastPacket = System.currentTimeMillis() -
                                   lastArrivalTimeByTimestamp.getOrDefault(timestamp, 0L);

        if (timeSinceLastPacket <= missingThreshold && lastGoodPacket != null) {
          log.warn("Packet Seq={} missing. Filling with last good packet.", seq);
          packets.put(seq, lastGoodPacket);
        } else {
          log.warn("Packet Seq={} missing beyond threshold. Skipping frame.", seq);
          frameComplete = false;
          break;
        }
      }
      lastGoodPacket = packets.get(seq);
    }

    if (frameComplete) {
      byte[] frame = frameProcessor.reassembleFrame(packets);
      if (frame != null) {
        lastAssembledTimestamp.set(timestamp);
        notifySubscribers(SentinelFrame.builder().data(frame).timestamp(timestamp).build());
      } else {
        log.error("Failed to assemble frame for timestamp: {}", timestamp);
      }
    } else {
      log.warn("Frame skipped for timestamp: {} due to packet loss.", timestamp);
    }
  }

  private void initializeFrameAssembly() {
    if (!frameBufferByTimestamp.isEmpty()) {
      // Set to the earliest frame in the buffer, but don't trigger immediate assembly
      long ts = frameBufferByTimestamp.keySet().stream()
          .min(Long::compare)
          .orElse(-1L);

      lastAssembledTimestamp.set(ts);

      log.info("Starting frame assembly from timestamp: {}", lastAssembledTimestamp);
    } else {
      // If buffer is empty, wait for incoming packets
      log.info("No initial frames available. Waiting for incoming packets.");
      lastAssembledTimestamp.set(-1L);
    }
  }

  public void processPacket(RTPPacket rtpPacket) {
    long timestamp = rtpPacket.getTimestamp();
    int sequenceNumber = rtpPacket.getSequenceNumber();
    long arrivalTime = System.currentTimeMillis();

    log.debug("Received packet: Timestamp={} Seq={}", timestamp, sequenceNumber);

    scheduler.schedule(() -> calculateJitter(timestamp, arrivalTime), 0, TimeUnit.MILLISECONDS);

    frameBufferByTimestamp
        .computeIfAbsent(timestamp, k -> new TreeMap<>())
        .put(sequenceNumber, rtpPacket);
  }

  private void calculateJitter(long timestamp, long arrivalTime) {
    Long previousArrivalTime = lastArrivalTimeByTimestamp.put(timestamp, arrivalTime);

    if (previousArrivalTime != null && lastAssembledTimestamp.get() != -1) {
      long interarrivalDifference = Math.abs(arrivalTime - previousArrivalTime);

      jitter.updateAndGet(j -> {
        double jitterSample = Math.abs(interarrivalDifference - frameIntervalMillis);
        return j + JITTER_ALPHA * (jitterSample - j);
      });

      //log.debug("Updated jitter: {} ms", jitter.get());
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

  SortedMap<Integer, RTPPacket> getFramesByTimestamp(long timestamp) {
    return frameBufferByTimestamp.get(timestamp);
  }

  public interface FrameListener {

    void onFrameAvailable(SentinelFrame frame);

  }
}
