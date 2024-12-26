package space.hajnal.sentinel.network.model;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;
import space.hajnal.sentinel.network.video.FrameProcessor;
import space.hajnal.sentinel.stream.RTPStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RTPPacketIntegrationTest {

  private static final int MTU_SIZE = 1400;  // Simulate safe RTP payload size

  @Test
  void testRTPPacketEncodingAndDecoding() throws IOException, InterruptedException {
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

    DatagramSocket sender = new DatagramSocket();
    DatagramSocket receiver = new DatagramSocket(12345);

    for (RTPPacket packet : serialized) {
      sender.send(
          new DatagramPacket(packet.toBytes(), packet.toBytes().length, InetAddress.getLocalHost(),
              12345));

      byte[] buffer = new byte[MTU_SIZE];
      DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
      receiver.receive(datagramPacket);
      RTPPacket deserialize = rtpPacketDeserializer.deserialize(datagramPacket);
      deserializedPackets.put(packet.getSequenceNumber(), deserialize);
    }

    for (RTPPacket original : serialized) {
      RTPPacket received = deserializedPackets.get(original.getSequenceNumber());
      System.out.println("Original size: " + original.getPayload().length + " Deserialized size: "
          + received.getPayload().length);
    }

    // Step 3: Reassemble the image
    byte[] reconstructedImage = frameProcessor.reassembleFrame(deserializedPackets);

    System.out.println("Reconstructed Image Size: " + reconstructedImage.length);

//    Frame reconstructed = RTPStream.fromBytes(reconstructedImage, 1920, 1080);
//    Frame original = RTPStream.fromBytes(imageBytes, 1920, 1080);
//
//    RTPStream.createCanvas("Reconstructed Image").showImage(reconstructed);
//    RTPStream.createCanvas("Original Image").showImage(original);
//    Thread.sleep(12000);

    // Step 4: Validate the reconstructed image against the original
    assertEquals(imageBytes.length, reconstructedImage.length,
        "Reconstructed image size does not match original.");
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

    // Step 3: Reassemble the image
    for (RTPPacket packet : serialize) {
      byte[] packetBytes = packet.toBytes();
      RTPPacket rtpPacket = RTPPacket.fromBytes(packetBytes);
      assertArrayEquals(packetBytes, rtpPacket.toBytes(),
          "Reconstructed image does not match original. Packet: " + packet.getSequenceNumber());
    }
  }


  private List<RTPPacket> fragmentToRTPPackets(byte[] data) {
    List<RTPPacket> packets = new ArrayList<>();
    int offset = 0;
    int sequenceNumber = 0;
    long ssrc = 12345L;
    long timestamp = System.currentTimeMillis();

    while (offset < data.length) {
      int remaining = data.length - offset;
      int payloadSize = Math.min(remaining, MTU_SIZE);  // Fragment payload
      byte[] payload = new byte[payloadSize];

      System.arraycopy(data, offset, payload, 0, payloadSize);

      boolean isLast = (offset + payloadSize) >= data.length;
      RTPPacket packet = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload, isLast);
      packets.add(packet);

      offset += payloadSize;
    }
    System.out.println("Generated " + packets.size() + " RTP packets.");
    return packets;
  }

  private byte[] reassembleImage(List<byte[]> payloads) {
    int totalSize = payloads.stream().mapToInt(p -> p.length).sum();
    byte[] reconstructed = new byte[totalSize];
    int offset = 0;

    for (byte[] payload : payloads) {
      System.arraycopy(payload, 0, reconstructed, offset, payload.length);
      offset += payload.length;
    }
    System.out.println("Reassembled Image Size: " + reconstructed.length);
    return reconstructed;
  }
}
