package org.example;

import java.io.ByteArrayOutputStream;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Frame;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class RTPClient {

  public static void main(String[] args) {
    String serverAddress = "127.0.0.1"; // Replace with your RTP server IP
    int serverPort = 5004;             // Replace with your RTP server port
    int webcamIndex = 0;               // Default webcam index for built-in camera

    try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(webcamIndex);
        DatagramSocket socket = new DatagramSocket()) {

      grabber.start();
      System.out.println("Video capture and streaming started...");

      int sequenceNumber = 0;
      int timestampIncrement = 90000 / 30; // Assuming 30 fps
      long timestamp = 0;
      int ssrc = 123456;                  // Arbitrary SSRC value

      while (true) {
        Frame frame = grabber.grab();
        if (frame == null) {
          break;
        }

        byte[] frameData = encodeFrameAsH264(frame); // Placeholder encoding function

        // Fragment and send the frame data using RTP
        int mtu = 1400; // Maximum transmission unit

        for (int offset = 0; offset < frameData.length; offset += mtu) {
          int remaining = frameData.length - offset;
          int payloadSize = Math.min(mtu, remaining);
          byte[] payload = new byte[payloadSize];
          System.arraycopy(frameData, offset, payload, 0, payloadSize);

          boolean marker = (offset + payloadSize == frameData.length); // Marker for last fragment
          RTPPacket rtpPacket = new RTPPacket(96, sequenceNumber, timestamp, ssrc, payload, marker);

          byte[] packetData = rtpPacket.toBytes();
          DatagramPacket packet = new DatagramPacket(packetData, packetData.length,
              InetAddress.getByName(serverAddress), serverPort);
          socket.send(packet);

          sequenceNumber++;
          offset += payloadSize;
        }

        timestamp += timestampIncrement; // Increment timestamp for the next frame
      }

      grabber.stop();
      System.out.println("Video capture and streaming stopped.");

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
