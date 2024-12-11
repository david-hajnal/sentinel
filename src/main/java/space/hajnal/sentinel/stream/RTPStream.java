package space.hajnal.sentinel.stream;

import java.net.DatagramSocket;
import java.util.function.Function;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_videoio;
import space.hajnal.sentinel.camera.FrameGrabberFactory;
import space.hajnal.sentinel.camera.SentinelFrameGrabber;
import space.hajnal.sentinel.camera.SentinelFrameGrabberOptions;
import space.hajnal.sentinel.codec.H264Encoder;
import space.hajnal.sentinel.network.RTPSocket;
import space.hajnal.sentinel.network.ServerOptions;

public class RTPStream {

  public static void main(String[] args) {
    Loader.load(opencv_core.class);
    Loader.load(opencv_videoio.class);

    RTPStream rtpStream = new RTPStream();
    rtpStream.start();
  }

  public void start() {

    SentinelFrameGrabberOptions grabberOptions = SentinelFrameGrabberOptions.builder()
        .cameraIndex(0)
        .frameRate(30)
        .imageHeight(720)
        .imageWidth(1280)
        .build();

    FrameGrabberFactory frameGrabberFactory = new FrameGrabberFactory();

    ServerOptions serverOptions = ServerOptions.builder().serverAddress("127.0.0.1")
        .serverPort(5004)
        .mtu(1400)
        .build();

    H264Encoder encoder = new H264Encoder(grabberOptions);

    try (SentinelFrameGrabber grabber = new SentinelFrameGrabber(grabberOptions,
        frameGrabberFactory); RTPSocket rtpSocket = new RTPSocket(serverOptions, encoder)) {
      rtpSocket.open(new DatagramSocket());

      CanvasFrame canvas = new CanvasFrame("RTP Sender Display");
      canvas.setDefaultCloseOperation(CanvasFrame.EXIT_ON_CLOSE);

      grabber.capture((Function<Frame, Void>) frame -> {
        long timestamp = System.currentTimeMillis();
        rtpSocket.send(frame, timestamp);
        canvas.showImage(frame);
        return null;
      });

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
