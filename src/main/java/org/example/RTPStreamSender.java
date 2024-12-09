package org.example;

import java.io.ByteArrayOutputStream;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RTPStreamSender {
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

  private void captureAndSend() {
    try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0); DatagramSocket socket = new DatagramSocket()) {
      grabber.start();
      int sequenceNumber = 0;
      long timestamp = 0;
      int ssrc = 123456;
      int frameRate = 30;

      while (running) {
        Frame frame = grabber.grab();
        byte[] frameData = encodeFrameAsH264(frame);

        int mtu = 1400; // Example MTU size
        for (int offset = 0; offset < (null != frameData ? frameData.length : 0); offset += mtu) {
          int payloadSize = Math.min(mtu, frameData.length - offset);
          byte[] payload = new byte[payloadSize];
          System.arraycopy(frameData, offset, payload, 0, payloadSize);
          boolean marker = (offset + payloadSize == frameData.length);
          RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber++, timestamp, ssrc, payload, marker);
          socket.send(new DatagramPacket(rtpPacket.toBytes(), rtpPacket.toBytes().length,
              InetAddress.getByName(serverAddress), serverPort));
        }
        timestamp += (90000 / frameRate);
      }

      grabber.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static byte[] encodeFrameAsH264(Frame frame) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // FFmpegFrameRecorder to encode the frame
    try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream, 1920, 1080)) {
      recorder.setVideoCodec(
          org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264); // Set H.264 codec
      recorder.setFormat("h264"); // Set format (can also use raw H.264 stream if needed)
      recorder.setFrameRate(30);
      recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
      recorder.setVideoBitrate(2000 * 1000); // 2 Mbps
      recorder.setOption("slices", "4"); // Example: Set to 4 slices per frame
      recorder.setVideoOption("profile:v", "main"); // Set H.264 profile to 'main'
      recorder.setOption("b_enable_frame_skip", "15");
      recorder.setOption("tune", "zerolatency"); // Set tuning for zero latency

      recorder.setOption("preset", "ultrafast"); // Ensure SPS/PPS is included
      recorder.setOption("tune", "zerolatency"); // Useful for real-time streaming
      recorder.setOption("x264opts",
          "keyint=30:min-keyint=30:scenecut=0"); // Regular keyframe intervals

      recorder.start(); // Initialize the recorder
      recorder.record(frame); // Encode the frame
      recorder.stop(); // Finish encoding
    } catch (Exception e) {
      e.printStackTrace();
      return new byte[0]; // Return an empty byte array on failure
    }

    return outputStream.toByteArray(); // Return the encoded frame as a byte array

  }
}
