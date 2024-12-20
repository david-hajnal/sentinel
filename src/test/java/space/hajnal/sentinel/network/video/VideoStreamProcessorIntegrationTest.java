package space.hajnal.sentinel.network.video;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import space.hajnal.sentinel.network.model.RTPPacket;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

class VideoStreamProcessorIntegrationTest {

  private VideoStreamProcessor videoStreamProcessor;
  private FrameProcessor frameProcessor;
  private RTPPacketSerializer rtpPacketSerializer;

  @BeforeEach
  void setUp() {
    frameProcessor = new FrameProcessor();
    videoStreamProcessor = new VideoStreamProcessor(frameProcessor);
    rtpPacketSerializer = new RTPPacketSerializer();
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
    List<RTPPacket> serialize = rtpPacketSerializer.serialize(imageBytes, 1400, 1234L, 123);
    SortedMap<Integer, RTPPacket> packets = new TreeMap<>();
    for (RTPPacket rtpPacket : serialize) {
      packets.put(rtpPacket.getSequenceNumber(), rtpPacket);
    }

    // Add a subscriber to capture the final frame
    final byte[][] receivedFrame = {null}; // Use an array to capture the frame in the test
    videoStreamProcessor.addSubscriber(frame -> receivedFrame[0] = frame);

    // Act: Process the packets
    packets.values().forEach(videoStreamProcessor::processPacket);

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