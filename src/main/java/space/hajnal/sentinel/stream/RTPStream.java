package space.hajnal.sentinel.stream;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_PANIC;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
import space.hajnal.sentinel.camera.SentinelFrameGrabberStatic;
import space.hajnal.sentinel.camera.model.SentinelFrame;
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
      .frameRate(1)
      .imageWidth(640)
      .imageHeight(480)
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

      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
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
          GRABBER_OPTIONS.getFrameRate(), scheduler);
//      SentinelFrameGrabber grabber = new SentinelFrameGrabber(GRABBER_OPTIONS,
//          frameGrabberFactory);
      SentinelFrameGrabber grabber = new SentinelFrameGrabberStatic(GRABBER_OPTIONS);
      RTPSocketSender rtpSocketSender = new RTPSocketSender(serverOptions,
          encoder,
          rtpPacketSerializer, frameProcessor);
      DatagramSocket socket = new DatagramSocket(5004);
      RTPStreamWriter rtpStreamWriter = new RTPStreamWriter(rtpSocketSender, grabber,
          executorService);
      PacketReceiver packetReceiver = new PacketReceiver(
          serverOptions,
          rtpPacketDeserializer, 500);
      RTPStreamReader rtpStreamReader = new RTPStreamReader(videoStreamProcessor, packetReceiver,
          executorService);

      CanvasFrame canvas = createCanvas("Receiver");
      ScheduledExecutorService frameDisplayScheduler = Executors.newSingleThreadScheduledExecutor();

      CountDownLatch latch = new CountDownLatch(1);
      canvas.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          try {
            grabber.close();
            rtpStreamWriter.close();
            rtpStreamReader.close();
            scheduler.shutdown();
            frameDisplayScheduler.shutdown();
            Thread.sleep(100);
            latch.countDown();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        }
      });

      ConcurrentLinkedQueue<SentinelFrame> frameQueue = new ConcurrentLinkedQueue<>();
      try {
        executorService.submit(() -> {
          rtpStreamReader.start(socket);
          rtpStreamWriter.start(socket);
        });

        log.info("Waiting for window to close");

        videoStreamProcessor.addSubscriber(f -> {
          frameQueue.add(f);
        });

        frameDisplayScheduler.scheduleAtFixedRate(() -> {
          SentinelFrame frameData = frameQueue.poll();
          if (frameData == null) {
            return;
          }
          display(frameData, canvas, GRABBER_OPTIONS);
        }, 100, 33, TimeUnit.MILLISECONDS);

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
    canvas.setResizable(false);
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

  private static void display(SentinelFrame frame, CanvasFrame canvas,
      SentinelFrameGrabberOptions options) {
    log.debug("Displaying frame {}", frame.getTimestamp());
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(
        new ByteArrayInputStream(frame.getData()))) {
      grabber.setImageWidth(options.getImageWidth());
      grabber.setImageHeight(options.getImageHeight());
      grabber.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24); // Pixel format
      grabber.start();
      Frame frameCV = grabber.grabImage();
      if (frameCV != null) {
        canvas.showImage(frameCV);
      } else {
        log.info("Frame is null");
      }
      grabber.stop();
    } catch (Exception e) {
      log.error("Error while displaying frame", e);
    }
  }
}
