package space.hajnal.sentinel.network.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;
import space.hajnal.sentinel.network.video.FrameProcessor;

class RTPPacketIntegrationTest {

  private static final int MTU_SIZE = 1400;  // Simulate safe RTP payload size
  private DatagramSocket sender;
  private DatagramSocket receiver;

  @BeforeEach
  void setUp() throws IOException {
    sender = new DatagramSocket();
    receiver = new DatagramSocket(12345);
  }

  @AfterEach
  void tearDown() {
    sender.close();
    receiver.close();
  }

  @Test
  @Timeout(5)
  void testRTPPacketEncodingAndDecoding() throws IOException {
    FrameProcessor frameProcessor = new FrameProcessor();
    RTPPacketSerializer rtpPacketSerializer = new RTPPacketSerializer();
    RTPPacketDeserializer rtpPacketDeserializer = new RTPPacketDeserializer();

    // Step 1: Load the test image
    File imageFile = new File("src/test/resources/test_full_hd.jpg");
    byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
    System.out.println("Original Image Size: " + imageBytes.length);
    // Step 2: Encode to RTP packets
    List<RTPPacket> serialized = rtpPacketSerializer.serialize(imageBytes, MTU_SIZE, 1, 12345);
    Collections.shuffle(serialized);  // Simulate out-of-order packets
    SortedMap<Integer, RTPPacket> deserializedPackets = new TreeMap<>();

    for (RTPPacket packet : serialized) {
      sender.send(
          new DatagramPacket(packet.toBytes(), packet.toBytes().length, InetAddress.getLocalHost(),
              12345));

      byte[] buffer = new byte[MTU_SIZE];
      DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
      receiver.receive(datagramPacket);
      int receivedLength = datagramPacket.getLength();
      byte[] receivedBytes = new byte[receivedLength];
      System.arraycopy(datagramPacket.getData(), 0, receivedBytes, 0, receivedLength);
      deserializedPackets.put(packet.getSequenceNumber(),
          rtpPacketDeserializer.deserialize(receivedBytes));

    }

    // Step 3: Reassemble the image
    byte[] reconstructedImage = frameProcessor.reassembleFrame(deserializedPackets);

    System.out.println("Reconstructed Image Size: " + reconstructedImage.length);

    // Step 4: Validate the reconstructed image against the original
    assertArrayEquals(imageBytes, reconstructedImage,
        "Reconstructed image does not match original.");
  }

  @Test
  void testRTPPacketEncodingAndDecodingSinglePacket() throws IOException {
    // Step 1: Load the test image
    File imageFile = new File("src/test/resources/test_full_hd.jpg");
    byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
    // Step 2: Encode to RTP packets
    RTPPacketSerializer rtpPacketSerializer = new RTPPacketSerializer();
    List<RTPPacket> serialize = rtpPacketSerializer.serialize(imageBytes, MTU_SIZE, 1, 12345);

    RTPPacketDeserializer rtpPacketDeserializer = new RTPPacketDeserializer();
    // Step 3: Reassemble the image
    for (RTPPacket packet : serialize) {
      byte[] packetBytes = packet.toBytes();
      RTPPacket rtpPacket = rtpPacketDeserializer.deserialize(packetBytes);
      assertArrayEquals(packetBytes, rtpPacket.toBytes(),
          "Reconstructed image does not match original. Packet: " + packet.getSequenceNumber());
    }
  }

}
