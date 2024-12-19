package org.example;

import java.io.ByteArrayOutputStream;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import space.hajnal.sentinel.network.model.RTPPacket;

@Deprecated
public class RTPStreamSender {

  public static final int FRAME_RATE = 30;
  public static final int IMAGE_WIDTH = 640;
  public static final int IMAGE_HEIGHT = 480;

  private final String serverAddress;
  private final int serverPort;
  private boolean running;

  public RTPStreamSender(String serverAddress, int serverPort) {
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }

  public void start() {
    running = true;
    new Thread(this::captureAndSend).start();
  }

  public void stop() {
    running = false;
  }

  public void sendTestImage(String imagePath) {
    new Thread(() -> {
      try (DatagramSocket socket = new DatagramSocket()) {
        socket.setSendBufferSize(65536);
        // Load the image
        ToMat converter = new ToMat();
        Mat matImage = opencv_imgcodecs.imread(imagePath);
        if (matImage.empty()) {
          System.err.println("Failed to load image from: " + imagePath);
          return;
        }
        Frame frame = converter.convert(matImage);

        // Encode the image as a frame
        byte[] frameData = encodeFrameAsH264(frame);

        // Send the frame data as RTP packets
        int sequenceNumber = 0;
        long timestamp = 0;
        int ssrc = 123456;
        int mtu = 1400;

        for (int offset = 0; offset < frameData.length; offset += mtu) {
          int payloadSize = Math.min(mtu, frameData.length - offset);
          byte[] payload = new byte[payloadSize];
          System.arraycopy(frameData, offset, payload, 0, payloadSize);

          boolean marker = (offset + payloadSize == frameData.length);
          RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload, marker);
          System.out.println("Sending RTP packet: " + rtpPacket);
          socket.send(new DatagramPacket(rtpPacket.toBytes(), rtpPacket.toBytes().length,
              InetAddress.getByName(serverAddress), serverPort));
        }
        System.out.println("Test image sent.");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  }


  private void captureAndSend() {
    try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);
        DatagramSocket socket = new DatagramSocket()) {

      grabber.setImageWidth(IMAGE_WIDTH);
      grabber.setImageHeight(IMAGE_HEIGHT);
      grabber.setFrameRate(FRAME_RATE);
      grabber.start();

      CanvasFrame canvas = new CanvasFrame("RTP Sender Display");
      canvas.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);

      int sequenceNumber = 0;
      long timestamp = 0;
      int ssrc = 123456;

      long frameDurationMillis = 1000 / FRAME_RATE; // Time per frame (e.g., 33ms for 30 FPS)
      long nextFrameTime = System.currentTimeMillis();

      while (running && canvas.isVisible()) {
        long now = System.currentTimeMillis();

        // Wait if we're ahead of schedule
        if (now < nextFrameTime) {
          Thread.sleep(nextFrameTime - now);
        }
        nextFrameTime += frameDurationMillis;


        Frame frame = grabber.grab();
        if (frame == null) {
          break;
        }

        // Display the captured frame on the sender's UI
        canvas.showImage(frame);

        byte[] frameData = encodeFrameAsH264(frame);

        int mtu = 1400; // Example MTU size
        for (int offset = 0; offset < frameData.length; offset += mtu) {
          int payloadSize = Math.min(mtu, frameData.length - offset);
          byte[] payload = new byte[payloadSize];
          System.arraycopy(frameData, offset, payload, 0, payloadSize);

          boolean marker = (offset + payloadSize == frameData.length);
          RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload, marker);

          socket.send(new DatagramPacket(rtpPacket.toBytes(), rtpPacket.toBytes().length,
              InetAddress.getByName(serverAddress), serverPort));
        }
        timestamp += (90000 / FRAME_RATE); // Increment timestamp based on frame rate
      }

      grabber.stop();
      canvas.dispose();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static byte[] encodeFrameAsH264(Frame frame) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream, IMAGE_WIDTH, IMAGE_HEIGHT)) {
      setupH246Settings(recorder);
      recorder.start();
      recorder.record(frame);
      recorder.stop();
    } catch (Exception e) {
      e.printStackTrace();
      return new byte[0];
    }

    return outputStream.toByteArray();
  }

  private static void setupH246Settings(FFmpegFrameRecorder recorder) {
    recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264); // H.264 codec
    recorder.setFormat("h264"); // Output format
    recorder.setFrameRate(FRAME_RATE);
    recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P); // Pixel format
    recorder.setVideoBitrate(1200 * 1000); // Bitrate
    recorder.setOption("slices", "4");
    recorder.setVideoOption("profile:v", "baseline");
    recorder.setOption("b_enable_frame_skip", "1");
    recorder.setOption("i_rc_mode", "VBR");

    recorder.setOption("preset", "ultrafast"); // SPS/PPS for low latency
    recorder.setOption("tune", "zerolatency"); // Real-time optimization
    recorder.setOption("x264opts", "keyint=30:min-keyint=30:scenecut=0"); // Keyframe interval
  }
}
