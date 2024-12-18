package space.hajnal.sentinel.network;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.example.RTPPacket;

@Slf4j
public class FrameProcessor {

  private final Map<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new TreeMap<>();
  private final List<FrameListener> subscribers = new CopyOnWriteArrayList<>();

  public void processPacket(RTPPacket rtpPacket) {
    long timestamp = rtpPacket.getTimestamp();
    int sequenceNumber = rtpPacket.getSequenceNumber();

    frameBufferByTimestamp
        .computeIfAbsent(timestamp, k -> new TreeMap<>())
        .put(sequenceNumber, rtpPacket.getPayload());

    if (rtpPacket.isMarker()) {
      SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.remove(timestamp);
      byte[] frame = reassembleFrame(packets);
      notifySubscribers(frame);
    }
  }

  private byte[] reassembleFrame(SortedMap<Integer, byte[]> packets) {
    int totalLength = packets.values().stream().mapToInt(p -> p.length).sum();
    byte[] completeFrame = new byte[totalLength];
    int offset = 0;

    for (byte[] payload : packets.values()) {
      System.arraycopy(payload, 0, completeFrame, offset, payload.length);
      offset += payload.length;
    }
    log.info("Reassembled frame size: {}", completeFrame.length);
    return completeFrame;
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
