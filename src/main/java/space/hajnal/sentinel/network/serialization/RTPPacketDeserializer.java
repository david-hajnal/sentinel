package space.hajnal.sentinel.network.serialization;

import java.net.DatagramPacket;
import space.hajnal.sentinel.network.model.RTPPacket;

public class RTPPacketDeserializer {

  /**
   * Deserializes an RTP packet from a DatagramPacket.
   *
   * @param packet The DatagramPacket to deserialize
   * @return The deserialized RTP packet
   */
  public RTPPacket deserialize(DatagramPacket packet) {
    byte[] packetData = new byte[packet.getLength()];
    System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());

    return RTPPacket.fromBytes(packetData);
  }

}
