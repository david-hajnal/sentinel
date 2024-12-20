package space.hajnal.sentinel.network.video;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;

@Slf4j
public class VideoStreamProcessor {

  private final Map<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new TreeMap<>();
  private final List<FrameListener> subscribers = new CopyOnWriteArrayList<>();
  private final FrameProcessor frameProcessor;

  public VideoStreamProcessor(FrameProcessor frameProcessor) {
    this.frameProcessor = frameProcessor;
  }

  public void processPacket(RTPPacket rtpPacket) {
    long timestamp = rtpPacket.getTimestamp();
    int sequenceNumber = rtpPacket.getSequenceNumber();

    frameBufferByTimestamp
        .computeIfAbsent(timestamp, k -> new TreeMap<>())
        .put(sequenceNumber, rtpPacket.getPayload());

    log.info("frameBufferByTimestamp size: {}", frameBufferByTimestamp.size());

    if (rtpPacket.isMarker()) {
      SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.remove(timestamp);
      byte[] frame = frameProcessor.reassembleFrame(packets);
      notifySubscribers(frame);
    }
  }

  SortedMap<Integer, byte[]> getFramesByTimestamp(long timestamp) {
    return frameBufferByTimestamp.get(timestamp);
  }

  public void addSubscriber(FrameListener listener) {
    subscribers.add(listener);
  }

  void notifySubscribers(byte[] frame) {
    for (FrameListener listener : subscribers) {
      listener.onFrameAvailable(frame);
    }
  }

  public interface FrameListener {

    void onFrameAvailable(byte[] frame);
  }
}
