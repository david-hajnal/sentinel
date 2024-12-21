package space.hajnal.sentinel.stream;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_PANIC;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_videoio;
import space.hajnal.sentinel.camera.FrameGrabberFactory;
import space.hajnal.sentinel.camera.SentinelFrameGrabber;
import space.hajnal.sentinel.camera.SentinelFrameGrabberOptions;
import space.hajnal.sentinel.codec.H264Encoder;
import space.hajnal.sentinel.network.video.FrameProcessor;
import space.hajnal.sentinel.network.video.VideoStreamProcessor;
import space.hajnal.sentinel.network.RTPStreamReader;
import space.hajnal.sentinel.network.RTPStreamWriter;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.receiver.PacketReceiver;
import space.hajnal.sentinel.network.sender.RTPSocketSender;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

@Slf4j
public class RTPStream {

  public static final SentinelFrameGrabberOptions GRABBER_OPTIONS = SentinelFrameGrabberOptions.builder()
      .cameraIndex(0)
      .frameRate(30)
      .imageHeight(480)
      .imageWidth(640)
      .build();

  public static void main(String[] args) {
    Loader.load(opencv_core.class);
    Loader.load(opencv_videoio.class);

    RTPStream rtpStream = new RTPStream();
    rtpStream.start();
  }

  @SneakyThrows
  public void start() {
    av_log_set_level(AV_LOG_PANIC);
    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

      FrameGrabberFactory frameGrabberFactory = new FrameGrabberFactory();

      ServerOptions serverOptions = ServerOptions.builder().serverAddress("127.0.0.1")
          .serverPort(5004)
          .mtu(1400)
          .build();

      H264Encoder encoder = new H264Encoder(GRABBER_OPTIONS);
      RTPPacketSerializer rtpPacketSerializer = new RTPPacketSerializer();
      RTPPacketDeserializer rtpPacketDeserializer = new RTPPacketDeserializer();
      FrameProcessor frameProcessor = new FrameProcessor();
      VideoStreamProcessor videoStreamProcessor = new VideoStreamProcessor(frameProcessor,
          GRABBER_OPTIONS.getFrameRate());
      SentinelFrameGrabber grabber = new SentinelFrameGrabber(GRABBER_OPTIONS,
          frameGrabberFactory);
      RTPSocketSender rtpSocketSender = new RTPSocketSender(serverOptions,
          encoder,
          rtpPacketSerializer);
      DatagramSocket socket = new DatagramSocket(5004);
      RTPStreamWriter rtpStreamWriter = new RTPStreamWriter(rtpSocketSender, grabber,
          executorService);
      PacketReceiver packetReceiver = new PacketReceiver(
          serverOptions,
          rtpPacketDeserializer, 50);
      RTPStreamReader rtpStreamReader = new RTPStreamReader(videoStreamProcessor, packetReceiver,
          executorService);

      CanvasFrame canvas = createCanvas("Receiver");

      CountDownLatch latch = new CountDownLatch(1);
      canvas.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          try {
            grabber.close();
            rtpStreamWriter.close();
            rtpStreamReader.close();
            Thread.sleep(100);
            latch.countDown();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        }
      });

      try {
        executorService.submit(() -> {

          rtpStreamReader.start(socket);
          rtpStreamWriter.start(socket);

          videoStreamProcessor.addSubscriber(frame -> display(frame, canvas, GRABBER_OPTIONS));

        });

        log.info("Waiting for window to close");

        latch.await();

      } catch (Exception e) {
        log.error("Error while capturing frames", e);
      }
    }

  }

  public static CanvasFrame createCanvas(String name) {
    CanvasFrame canvas = new CanvasFrame(name);
    canvas.setSize(RTPStream.GRABBER_OPTIONS.getImageWidth(),
        RTPStream.GRABBER_OPTIONS.getImageHeight());
    canvas.setDefaultCloseOperation(EXIT_ON_CLOSE);
    return canvas;
  }

  public static Frame fromBytes(byte[] frameData, int width, int height) {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
      grabber.setImageWidth(width);
      grabber.setImageHeight(height);
      grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24); // Pixel format
      grabber.start();
      Frame frame = grabber.grabImage();
      grabber.stop();
      return frame;
    } catch (Exception e) {
      log.error("Error while displaying frame", e);
      return null;
    }
  }

  private static void display(byte[] frameData, CanvasFrame canvas,
      SentinelFrameGrabberOptions options) {
    log.debug("Displaying frame");
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(frameData))) {
      // grabber.setFrameRate(options.getFrameRate());
      grabber.setImageWidth(options.getImageWidth());
      grabber.setImageHeight(options.getImageHeight());
      grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24); // Pixel format
      grabber.start();
      Frame frame = grabber.grabImage();
      if (frame != null) {
        canvas.showImage(frame);
      } else {
        log.info("Frame is null");
      }
      grabber.stop();
    } catch (Exception e) {
      log.error("Error while displaying frame", e);
    }
  }
}
