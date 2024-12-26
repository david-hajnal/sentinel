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

  public void compareSentAndReceivedPackets(SortedMap<Integer, RTPPacket> packets, long timestamp) {
    if (sentPackets.isEmpty()) {
      log.info("No packets to compare");
      return;
    }

    int missingPackets = 0;
    sentPackets.stream()
        .filter(p -> p.getTimestamp() == timestamp && packets.containsKey(p.getSequenceNumber()))
        .forEach(p -> {
          byte[] sentPayload = p.getPayload();
          byte[] receivedPayload = packets.get(p.getSequenceNumber()).getPayload();
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

  public byte[] reassembleFrame(SortedMap<Integer, RTPPacket> packets) {
    if (packets == null || packets.isEmpty()) {
      return new byte[0];
    }

    int totalLength = packets.values().stream()
        .mapToInt(packet -> packet.getPayload().length)
        .sum();

    byte[] completeFrame = new byte[totalLength];
    AtomicInteger offset = new AtomicInteger(0);

    packets.keySet()
        .stream()
        .sorted() //
        .forEach(seq -> {
          RTPPacket packet = packets.get(seq);
          byte[] fullPacket = packet.getPayload();
          System.arraycopy(fullPacket, 0, completeFrame, offset.get(), fullPacket.length);
          offset.addAndGet(fullPacket.length);
        });

    return completeFrame;
  }

}
