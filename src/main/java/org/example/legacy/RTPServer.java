package org.example.legacy;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.example.RTPPacket;

public class RTPServer {

  private static final int PORT = 5004; // RTP port
  private static final int BUFFER_SIZE = 1400; // MTU size for RTP
  private static final int JITTER_BUFFER_DELAY_MS = 5; // Jitter buffer delay

  private static final SortedMap<Integer, byte[]> frameBuffer = new TreeMap<>();
  private static final DelayQueue<JitterBufferEntry> jitterBuffer = new DelayQueue<>();

  public static void main(String[] args) throws Exception {
    DatagramSocket socket = new DatagramSocket(PORT);

    CanvasFrame canvas = new CanvasFrame("RTP Video Stream");
    OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    byte[] buffer = new byte[BUFFER_SIZE];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

    final int[] lastProcessedSequenceNumber = {-1};
    int frameSize = 1400;
    int frameWidth = 1920; // Full HD width
    int frameHeight = 1080; // Full HD height

    Thread jitterProcessingThread = new Thread(() -> {
      while (true) {
        try {
          // Process packets from the jitter buffer
          JitterBufferEntry entry = jitterBuffer.take();
          RTPPacket rtpPacket = RTPPacket.fromBytes(entry.getData());

          int sequenceNumber = rtpPacket.getSequenceNumber();
          boolean isLastFragment = rtpPacket.isMarker();
          byte[] payload = rtpPacket.getPayload();

          frameBuffer.put(sequenceNumber, payload);

          if (isLastFragment) {
            // Reassemble complete frame
            byte[] completeFrame = reassembleFrame(frameBuffer, lastProcessedSequenceNumber[0] + 1,
                sequenceNumber, frameSize);

            if (completeFrame != null) {
              lastProcessedSequenceNumber[0] = sequenceNumber;

              // Decode frame using OpenCV
              Mat decodedFrame = decodeFrame(completeFrame, frameWidth, frameHeight);
              if (decodedFrame != null) {
                Frame displayFrame = converter.convert(decodedFrame);
                canvas.showImage(displayFrame);
              }
            }

            // Clean up buffer
            frameBuffer.headMap(lastProcessedSequenceNumber[0] + 1).clear();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    jitterProcessingThread.start();

    while (true) {
      socket.receive(packet);
      byte[] packetData = Arrays.copyOf(packet.getData(), packet.getLength());
      long currentTimestamp = System.currentTimeMillis();
      jitterBuffer.offer(new JitterBufferEntry(packetData, currentTimestamp + JITTER_BUFFER_DELAY_MS));
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

  private static Mat decodeFrame(byte[] frameData, int width, int height) {
    try {
      try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
        grabber.start();
        Frame frame = grabber.grabImage();
        grabber.stop();

        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
        return converter.convert(frame);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static class JitterBufferEntry implements Delayed {
    private final byte[] data;
    private final long deliveryTime;

    public JitterBufferEntry(byte[] data, long deliveryTime) {
      this.data = data;
      this.deliveryTime = deliveryTime;
    }

    public byte[] getData() {
      return data;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(deliveryTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return Long.compare(this.deliveryTime, ((JitterBufferEntry) o).deliveryTime);
    }
  }
}
