package space.hajnal.sentinel.network.video;

import java.util.SortedMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrameProcessor {

  byte[] reassembleFrame(SortedMap<Integer, byte[]> packets) {
    int totalLength = packets.values().stream().mapToInt(p -> p.length).sum();
    byte[] completeFrame = new byte[totalLength];
    int offset = 0;

    for (byte[] payload : packets.values()) {
      System.arraycopy(payload, 0, completeFrame, offset, payload.length);
      offset += payload.length;
    }
    log.debug("Reassembled frame size: {}", completeFrame.length);
    return completeFrame;
  }

}
