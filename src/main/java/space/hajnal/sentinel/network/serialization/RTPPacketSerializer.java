package space.hajnal.sentinel.network.serialization;

import java.util.ArrayList;
import java.util.List;
import space.hajnal.sentinel.network.model.RTPPacket;

public class RTPPacketSerializer {

  /**
   * Serialize a frame into RTP packets.
   *
   * @param frameData The frame data to serialize.
   * @param mtu The maximum transmission unit.
   * @param timestamp The timestamp of the frame.
   * @param ssrc The synchronization source identifier.
   * @return A list of RTP packets.
   */
  public List<RTPPacket> serialize(byte[] frameData, int mtu, long timestamp, int ssrc) {

    List<RTPPacket> data = new ArrayList<>();
    int sequenceNumber = 0;

    for (int offset = 0; offset < frameData.length; offset += mtu) {
      int payloadSize = Math.min(mtu, frameData.length - offset);
      byte[] payload = new byte[payloadSize];
      System.arraycopy(frameData, offset, payload, 0, payloadSize);

      boolean marker = (offset + payloadSize == frameData.length);
      RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload,
          marker);

      data.add(rtpPacket);
    }

    return data;
  }

}
