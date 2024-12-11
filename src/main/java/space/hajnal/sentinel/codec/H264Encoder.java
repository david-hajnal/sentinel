package space.hajnal.sentinel.codec;

import java.io.ByteArrayOutputStream;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import space.hajnal.sentinel.camera.SentinelFrameGrabberOptions;

public class H264Encoder {

  private SentinelFrameGrabberOptions options;

  public H264Encoder(SentinelFrameGrabberOptions options) {
    this.options = options;
  }

  public byte[] encode(Frame frame) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream,
        options.getImageWidth(), options.getImageHeight())) {
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

  private void setupH246Settings(FFmpegFrameRecorder recorder) {
    recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264); // H.264 codec
    recorder.setFormat("h264"); // Output format
    recorder.setFrameRate(options.getFrameRate());
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
