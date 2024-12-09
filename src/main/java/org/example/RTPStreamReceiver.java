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

public class RTPStreamReceiver {
  private static final int BUFFER_SIZE = 1400; // MTU size
  private static final int JITTER_BUFFER_DELAY_MS = 5;

  private final int port;
  private final SortedMap<Integer, byte[]> frameBuffer = new TreeMap<>();
  private final DelayQueue<JitterBufferEntry> jitterBuffer = new DelayQueue<>();
  private boolean running;

  public RTPStreamReceiver(int port) {
    this.port = port;
  }

  public void start() {
    running = true;
    new Thread(this::processJitterBuffer).start();
    new Thread(this::receivePackets).start();
  }

  public void stop() {
    running = false;
  }

  private void receivePackets() {
    try (DatagramSocket socket = new DatagramSocket(port)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

      while (running) {
        socket.receive(packet);
        byte[] packetData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
        long currentTimestamp = System.currentTimeMillis();
        jitterBuffer.offer(new JitterBufferEntry(packetData, currentTimestamp + JITTER_BUFFER_DELAY_MS));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processJitterBuffer() {
    CanvasFrame canvas = new CanvasFrame("RTP Video Stream");
    OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    final int[] lastProcessedSequenceNumber = {-1};
    while (running) {
      try {
        JitterBufferEntry entry = jitterBuffer.take();
        RTPPacket rtpPacket = RTPPacket.fromBytes(entry.getData());
        int sequenceNumber = rtpPacket.getSequenceNumber();
        boolean isLastFragment = rtpPacket.isMarker();
        byte[] payload = rtpPacket.getPayload();

        frameBuffer.put(sequenceNumber, payload);

        if (isLastFragment) {
          byte[] completeFrame = reassembleFrame(frameBuffer, lastProcessedSequenceNumber[0] + 1, sequenceNumber,
              1400);
          if (completeFrame != null) {
            lastProcessedSequenceNumber[0] = sequenceNumber;
            Mat decodedFrame = decodeFrame(completeFrame, 1920, 1080); // Example resolution
            if (decodedFrame != null) {
              Frame displayFrame = converter.convert(decodedFrame);
              canvas.showImage(displayFrame);
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static byte[] reassembleFrame(SortedMap<Integer, byte[]> buffer, int start, int end, int frameSize) {
    int totalLength = 0;

    for (int seq = start; seq <= end; seq++) {
      totalLength += buffer.containsKey(seq) ? buffer.get(seq).length : frameSize;
    }

    byte[] completeFrame = new byte[totalLength];
    int offset = 0;

    for (int seq = start; seq <= end; seq++) {
      if (buffer.containsKey(seq)) {
        byte[] fragment = buffer.get(seq);
        System.arraycopy(fragment, 0, completeFrame, offset, fragment.length);
        offset += fragment.length;
      } else {
        byte[] blackImage = createBlackImagePlaceholder(frameSize);
        System.arraycopy(blackImage, 0, completeFrame, offset, blackImage.length);
        offset += blackImage.length;
      }
    }

    return completeFrame;
  }

  private static byte[] createBlackImagePlaceholder(int frameSize) {
    byte[] blackImage = new byte[frameSize];
    for (int i = 0; i < frameSize; i++) {
      blackImage[i] = 0;
    }
    return blackImage;
  }

  private Mat decodeFrame(byte[] frameData, int width, int height) {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
      grabber.start();
      Frame frame = grabber.grabImage();
      grabber.stop();
      return new OpenCVFrameConverter.ToMat().convert(frame);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
