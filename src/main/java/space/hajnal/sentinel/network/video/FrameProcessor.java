package space.hajnal.sentinel.network.video;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import space.hajnal.sentinel.network.model.RTPPacket;

@Slf4j
public class FrameProcessor {

  private volatile List<RTPPacket> sentPackets = new ArrayList<>();

  public synchronized void addSentPacket(RTPPacket rtpPacket) {
    sentPackets.add(rtpPacket);
  }

  public void compareSentAndReceivedPackets(SortedMap<Integer, byte[]> packets, long timestamp) {
    if (sentPackets.isEmpty()) {
      log.info("No packets to compare");
      return;
    }

    int missingPackets = 0;
    sentPackets.stream()
        .filter(p -> p.getTimestamp() == timestamp && packets.containsKey(p.getSequenceNumber()))
        .forEach(p -> {
          byte[] sentPayload = p.getPayload();
          byte[] receivedPayload = packets.get(p.getSequenceNumber());
          if (sentPayload.length != receivedPayload.length) {
            log.warn("Payload length mismatch for seq: {} (sent: {}, received: {})",
                p.getSequenceNumber(),
                sentPayload.length, receivedPayload.length);
          }

          for (int i = receivedPayload.length; i < sentPayload.length; i++) {
            System.out.printf("Byte %d missing. Value: %02X%n", i, sentPayload[i]);
          }
        });

    log.info("Missing packets: {}", missingPackets);
  }

  public byte[] reassembleFrame(SortedMap<Integer, byte[]> packets) {
    int totalLength = packets.values().stream().mapToInt(p -> p.length).sum();
    byte[] completeFrame = new byte[totalLength];
    AtomicInteger offset = new AtomicInteger(0);

    packets.keySet().forEach(seq -> {
      byte[] payload = packets.get(seq);
      System.arraycopy(payload, 0, completeFrame, offset.get(), payload.length);
      offset.addAndGet(payload.length);
    });

    //log.debug("Reassembled frame size: {}", completeFrame.length);
    return completeFrame;
  }

}
