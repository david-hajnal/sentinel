package space.hajnal.sentinel.stream;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.VideoInputFrameGrabber;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_videoio;
import org.bytedeco.opencv.opencv_core.Mat;
import space.hajnal.sentinel.camera.FrameGrabberFactory;
import space.hajnal.sentinel.camera.SentinelFrameGrabber;
import space.hajnal.sentinel.camera.SentinelFrameGrabberOptions;
import space.hajnal.sentinel.codec.H264Encoder;
import space.hajnal.sentinel.network.FrameProcessor;
import space.hajnal.sentinel.network.RTPStreamReader;
import space.hajnal.sentinel.network.RTPStreamWriter;
import space.hajnal.sentinel.network.model.ServerOptions;
import space.hajnal.sentinel.network.receiver.PacketReceiver;
import space.hajnal.sentinel.network.sender.RTPSocketSender;
import space.hajnal.sentinel.network.serialization.RTPPacketDeserializer;
import space.hajnal.sentinel.network.serialization.RTPPacketSerializer;

@Slf4j
public class RTPStream {

  public static void main(String[] args) {
    Loader.load(opencv_core.class);
    Loader.load(opencv_videoio.class);

    RTPStream rtpStream = new RTPStream();
    rtpStream.start();
  }

  @SneakyThrows
  public void start() {

    Arrays.stream(OpenCVFrameGrabber.getDeviceDescriptions()).forEach(log::info);

    try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

      SentinelFrameGrabberOptions grabberOptions = SentinelFrameGrabberOptions.builder()
          .cameraIndex(0)
          .frameRate(30)
          .imageHeight(480)
          .imageWidth(640)
          .build();

      FrameGrabberFactory frameGrabberFactory = new FrameGrabberFactory();

      ServerOptions serverOptions = ServerOptions.builder().serverAddress("127.0.0.1")
          .serverPort(5004)
          .mtu(1400)
          .build();

      H264Encoder encoder = new H264Encoder(grabberOptions);

      RTPPacketSerializer rtpPacketSerializer = new RTPPacketSerializer();
      RTPPacketDeserializer rtpPacketDeserializer = new RTPPacketDeserializer();

      FrameProcessor frameProcessor = new FrameProcessor();
      CountDownLatch latch = new CountDownLatch(1);
      try (SentinelFrameGrabber grabber = new SentinelFrameGrabber(grabberOptions,
          frameGrabberFactory); RTPSocketSender rtpSocketSender = new RTPSocketSender(serverOptions,
          encoder,
          rtpPacketSerializer)) {
        DatagramSocket socket = new DatagramSocket(5004);
        rtpSocketSender.open(socket);

        RTPStreamWriter rtpStreamWriter = new RTPStreamWriter(rtpSocketSender, grabber,
            executorService);

        RTPStreamReader rtpStreamReader;
        try (PacketReceiver packetReceiver = new PacketReceiver(socket,
            serverOptions,
            rtpPacketDeserializer, 5000)) {

          rtpStreamReader = new RTPStreamReader(frameProcessor, packetReceiver,
              executorService);
        }

        CanvasFrame canvas = new CanvasFrame("RTP Sender Display");
        canvas.setDefaultCloseOperation(EXIT_ON_CLOSE);

        canvas.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            latch.countDown();
          }
        });

        executorService.submit(() -> {
          try (ToMat converter = new ToMat()) {

            rtpStreamReader.start();
            rtpStreamWriter.start();

            frameProcessor.addSubscriber(frame -> display(frame, converter, canvas));

          }
        });

        latch.await();

      } catch (Exception e) {
        log.error("Error while capturing frames", e);
        latch.countDown();
      }
    }

  }

  private static void display(byte[] frame, ToMat converter, CanvasFrame canvas) {
    Mat decodedFrame = decodeFrame(frame, 640, 480);
    if (decodedFrame != null) {
      Frame displayFrame = converter.convert(decodedFrame);
      canvas.showImage(displayFrame);
    }
  }

  private static Mat decodeFrame(byte[] frameData, int width, int height) {
    try {
      try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(
          new ByteArrayInputStream(frameData))) {
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
}
