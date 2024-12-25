package space.hajnal.sentinel.camera;

import static space.hajnal.sentinel.stream.RTPStream.createCanvas;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;

@Slf4j
public class SentinelFrameGrabberStatic extends SentinelFrameGrabber implements AutoCloseable {

  public static final String SRC_TEST_RESOURCES_TEST_FULL_HD_JPG = "src/test/resources/cat.jpg";
  private final SentinelFrameGrabberOptions options;
  private boolean running = false;
  private final ScheduledExecutorService scheduler;
  private Frame testFrame;  // Store the test frame to send repeatedly

  public SentinelFrameGrabberStatic(SentinelFrameGrabberOptions options) {
    super(options, new FrameGrabberFactory());
    this.options = options;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    loadTestImage(SRC_TEST_RESOURCES_TEST_FULL_HD_JPG);  // Load test image at startup
  }

  public SentinelFrameGrabberStatic(ScheduledExecutorService scheduler,
      SentinelFrameGrabberOptions options) {
    super(scheduler, options, new FrameGrabberFactory());
    this.options = options;
    this.scheduler = scheduler;
    loadTestImage(SRC_TEST_RESOURCES_TEST_FULL_HD_JPG);
  }

  // Load the test image and convert it to a Frame
  private void loadTestImage(String imagePath) {
    Mat image = opencv_imgcodecs.imread(imagePath);  // Load the image using OpenCV
    if (image.empty()) {
      log.error("Failed to load test image from {}", imagePath);
      throw new RuntimeException("Test image could not be loaded.");
    }
    OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    testFrame = converter.convert(image);  // Convert the Mat to a JavaCV Frame
    log.info("Test image loaded successfully.");
  }

  @SneakyThrows
  public void capture(FrameGrabberCallback fn) {

    if (fn == null) {
      throw new IllegalArgumentException("Function cannot be null");
    }

    try {
      running = true;
      long frameDurationMillis = 1000 / options.getFrameRate();
      AtomicLong timestamp = new AtomicLong(0);

      CanvasFrame canvas = createCanvas("Test Image Source");

      scheduler.scheduleAtFixedRate(() -> {
        if (!running) {
          return;
        }

        try {
          if (testFrame == null) {
            log.error("Test frame is not available.");
            return;
          }
          canvas.showImage(testFrame);  // Show the test image on CanvasFrame
          fn.onFrameGrabbed(testFrame, timestamp.addAndGet(90000 / options.getFrameRate()));  // Simulate RTP timestamp
        } catch (Exception e) {
          log.error("Error while sending test frame", e);
        }
      }, 100, frameDurationMillis, TimeUnit.MILLISECONDS);

    } catch (Exception e) {
      log.error("Error during test frame broadcasting", e);
      this.close();
    }
  }

  @Override
  public void close() throws Exception {
    running = false;
    scheduler.shutdown();
  }

  boolean isRunning() {
    return running;
  }
}
