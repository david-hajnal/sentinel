package org.example;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.DelayQueue;
import space.hajnal.sentinel.network.model.RTPPacket;

@Deprecated
public class RTPStreamReceiver {

  private static final int BUFFER_SIZE = 1400; // MTU size
  private static final int JITTER_BUFFER_DELAY_MS = 00;
  public static final int WIDTH = 640;
  public static final int HEIGHT = 480;
  public static final int FRAME_RATE = 1;

  private final int port;
  private final SortedMap<Integer, byte[]> frameBuffer = new TreeMap<>();
  private final DelayQueue<JitterBufferEntry> jitterBuffer = new DelayQueue<>();
  private final CanvasFrame canvas;
  private boolean running;
  private final SortedMap<Long, SortedMap<Integer, byte[]>> frameBufferByTimestamp = new TreeMap<>();
  private final long maxWaitTimeMillis = 100; // Max wait time for missing packets

  public RTPStreamReceiver(int port) {
    this.port = port;
    canvas = new CanvasFrame("RTP Playback");
    canvas.setVisible(false);
  }

  public void start() {
    running = true;
    canvas.setVisible(true);
    // new Thread(this::processJitterBuffer).start();
    new Thread(this::receivePackets).start();
  }

  public void stop() {
    running = false;
    canvas.setVisible(false);
  }

  private void receivePackets() {
    try (DatagramSocket socket = new DatagramSocket(port)) {
      socket.setReceiveBufferSize(65536);
      byte[] buffer = new byte[BUFFER_SIZE];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

      while (running) {
        socket.receive(packet);
        byte[] packetData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
        RTPPacket rtpPacket = RTPPacket.fromBytes(packetData);

        long timestamp = rtpPacket.getTimestamp();
        int sequenceNumber = rtpPacket.getSequenceNumber();

        // Add packet to the timestamp-based buffer
        frameBufferByTimestamp.computeIfAbsent(timestamp, k -> new TreeMap<>())
            .put(sequenceNumber, rtpPacket.getPayload());

        System.out.println(
            "Buffered RTP packet: Timestamp=" + timestamp + ", SequenceNumber=" + sequenceNumber);

        // Trigger reassembly if marker packet is received
        if (rtpPacket.isMarker()) {
          processFrameBuffer(timestamp);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processFrameBuffer(long timestamp) {
    SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.get(timestamp);
    if (packets == null) {
      return;
    }

    // Check if the frame is complete
    int firstSeq = packets.firstKey();
    int lastSeq = packets.lastKey();
    for (int seq = firstSeq; seq <= lastSeq; seq++) {
      if (!packets.containsKey(seq)) {
        System.out.println("Missing packet: SequenceNumber=" + seq + " for Timestamp=" + timestamp);
        // Wait for a short period to see if missing packets arrive
        waitForMissingPackets(timestamp);
        return;
      }
    }

    // Reassemble frame
    byte[] frameData = reassembleFrame(packets);
    if (frameData != null) {
      decodeAndDisplayFrame(frameData);
      frameBufferByTimestamp.remove(timestamp); // Remove processed frame
    }
  }

  private void waitForMissingPackets(long timestamp) {
    long startTime = System.currentTimeMillis();
    while ((System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
      SortedMap<Integer, byte[]> packets = frameBufferByTimestamp.get(timestamp);
      int firstSeq = packets.firstKey();
      int lastSeq = packets.lastKey();
      boolean allPacketsReceived = true;

      for (int seq = firstSeq; seq <= lastSeq; seq++) {
        if (!packets.containsKey(seq)) {
          allPacketsReceived = false;
          break;
        }
      }

      if (allPacketsReceived) {
        processFrameBuffer(timestamp);
        return;
      }

      try {
        Thread.sleep(1); // Allow time for packets to arrive
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    System.out.println("Frame incomplete. Dropping Timestamp=" + timestamp);
    frameBufferByTimestamp.remove(timestamp);
  }

  private void decodeAndDisplayFrame(byte[] frameData) {

    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
      grabber.setFrameRate(FRAME_RATE);
      grabber.setImageWidth(WIDTH);
      grabber.setImageHeight(HEIGHT);
      grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P); // Pixel format
      grabber.start();
      Frame frame = grabber.grabImage();
      if (frame != null) {
        canvas.showImage(frame);
      }else {
        System.out.println("Frame is null");
      }
      grabber.stop();
    } catch (Exception e) {
      e.printStackTrace();
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

    System.out.println("Reassembled frame size: " + completeFrame.length);
    return completeFrame;
  }

  private static byte[] generatePlaceholderData(int size) {
    // Create a placeholder NAL unit for padding (e.g., Filler Data NAL Unit)
    byte[] placeholder = new byte[size];
    placeholder[0] = 0x00; // Start code prefix (NAL unit start)
    placeholder[1] = 0x00;
    placeholder[2] = 0x01;
    placeholder[3] = 0x0C; // Filler Data NAL Unit type (12 for H.264)
    // Fill the rest with zeros or neutral padding bytes
    for (int i = 4; i < size; i++) {
      placeholder[i] = (byte) 0xFF; // Padding data
    }
    return placeholder;
  }


  private Mat decodeFrame(byte[] frameData, int width, int height) {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
      grabber.start();
      grabber.setImageWidth(width);
      grabber.setImageHeight(height);
      grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
      grabber.setFrameRate(FRAME_RATE);
      Frame frame = grabber.grabImage();
      grabber.stop();
      return new OpenCVFrameConverter.ToMat().convert(frame);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
