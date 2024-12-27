package space.hajnal.sentinel.network.video;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.DatagramPacket;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

class VideoStreamProcessorIntegrationTest {

  private VideoStreamProcessor videoStreamProcessor;
  private FrameProcessor frameProcessor;
  private RTPPacketSerializer rtpPacketSerializer;
  private ScheduledExecutorService scheduler;

  @BeforeEach
  void setUp() {
    frameProcessor = new FrameProcessor();
    videoStreamProcessor = new VideoStreamProcessor(frameProcessor, 1,
        Executors.newSingleThreadScheduledExecutor());
    rtpPacketSerializer = new RTPPacketSerializer();
    scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  @AfterEach
  void tearDown() {
    scheduler.shutdown();
  }

  @Test
  void testProcessPacket_FullImageReassembled() throws Exception {
    // Arrange: Load an image from resources
    File imageFile = new File("src/test/resources/test_full_hd.jpg");
    BufferedImage originalImage = ImageIO.read(imageFile);

    // Check color format
    System.out.println("Color Model: " + originalImage.getColorModel());
    System.out.println("Width: " + originalImage.getWidth());
    System.out.println("Height: " + originalImage.getHeight());
    System.out.println("Number of Components: " + originalImage.getColorModel().getNumComponents());
    System.out.println(
        "Number of Color Components: " + originalImage.getColorModel().getNumColorComponents());

    // Convert image to byte array
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(originalImage, "jpg", baos);
    byte[] imageBytes = baos.toByteArray();
    System.out.println("Original frame size: " + imageBytes.length);

    // Create RTP packets
    List<RTPPacket> rtpPacketList = rtpPacketSerializer.serialize(imageBytes, 1400, 1234L, 123);
    Collections.shuffle(rtpPacketList); // Randomize packet order
    SortedMap<Integer, RTPPacket> packets = new TreeMap<>();
    RTPPacketDeserializer rtpPacketDeserializer = new RTPPacketDeserializer();

    for (RTPPacket rtpPacket : rtpPacketList) {
      DatagramPacket datagramPacket = new DatagramPacket(rtpPacket.toBytes(),
          rtpPacket.toBytes().length);
      RTPPacket deserialized = rtpPacketDeserializer.deserialize(datagramPacket);
      System.out.println("Packet: " + deserialized.getSequenceNumber());
      packets.put(rtpPacket.getSequenceNumber(), deserialized);
    }

    // Add a subscriber to capture the final frame
    CountDownLatch latch = new CountDownLatch(1);
    final byte[][] receivedFrame = {null}; // Use an array to capture the frame in the test
    videoStreamProcessor.addSubscriber(frame -> {
      receivedFrame[0] = frame.getData();
      latch.countDown();
    });

    // Act: Process the packets
    packets.keySet().forEach(seq -> videoStreamProcessor.processPacket(packets.get(seq)));

    assertTrue(latch.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS),
        "Timeout waiting for frame assembly");
    // Assert: Ensure the reassembled frame matches the original image data
    assertNotNull(receivedFrame[0], "Frame should not be null");
    assertEquals(imageBytes.length, receivedFrame[0].length, "Frame size mismatch");
    assertArrayEquals(imageBytes, receivedFrame[0],
        "Reassembled frame does not match original image");

    // Verify that the image can be reloaded
    BufferedImage reassembledImage = ImageIO.read(new ByteArrayInputStream(receivedFrame[0]));
    assertEquals(originalImage.getWidth(), reassembledImage.getWidth(), "Image width mismatch");
    assertEquals(originalImage.getHeight(), reassembledImage.getHeight(), "Image height mismatch");
  }

}