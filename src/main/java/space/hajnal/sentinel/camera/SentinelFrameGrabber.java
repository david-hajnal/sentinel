package space.hajnal.sentinel.camera;

import static space.hajnal.sentinel.stream.RTPStream.createCanvas;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

@Slf4j
public class SentinelFrameGrabber implements AutoCloseable {

  private final SentinelFrameGrabberOptions options;
  private boolean running = false;
  private final ScheduledExecutorService scheduler;
  private final FrameGrabberFactory frameGrabberFactory;

  private FrameGrabber grabber;

  public SentinelFrameGrabber(SentinelFrameGrabberOptions options,
      FrameGrabberFactory frameGrabberFactory) {
    this.options = options;
    this.frameGrabberFactory = frameGrabberFactory;
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  public SentinelFrameGrabber(ScheduledExecutorService scheduler,
      SentinelFrameGrabberOptions options,
      FrameGrabberFactory frameGrabberFactory) {
    this.options = options;
    this.scheduler = scheduler;
    this.frameGrabberFactory = frameGrabberFactory;
  }

  public void capture(FrameGrabberCallback fn) throws Exception {

    if (fn == null) {
      throw new IllegalArgumentException("Function cannot be null");
    }

    try {

      grabber = frameGrabberFactory.create(options.getCameraIndex());

      running = true;
      grabber.setImageWidth(options.getImageWidth());
      grabber.setImageHeight(options.getImageHeight());
      grabber.setFrameRate(options.getFrameRate());
      grabber.start();
      log.info("Frame grabber started");
      long frameDurationMillis = 1000 / options.getFrameRate();
      AtomicLong timestamp = new AtomicLong(0);

      CanvasFrame canvas = createCanvas("Source");

      scheduler.scheduleAtFixedRate(() -> {
        if (!running) {
          return;
        }

        try {
          Frame frame = grabber.grab();
          if (frame == null) {
            log.info("No more frames to grab.");
          } else {
            canvas.showImage(frame);
            fn.onFrameGrabbed(frame, timestamp.addAndGet(90000 / options.getFrameRate()));
          }
        } catch (Exception e) {
          log.error("Error while capturing frames", e);
        }
      }, 200, frameDurationMillis, TimeUnit.MILLISECONDS);

    } catch (
        Exception e) {
      log.error("Error initializing frame grabber", e);
      this.close();
    }
  }

  @Override
  public void close() throws Exception {
    running = false;
    if (grabber != null) {
      grabber.stop();
      grabber.close();
    }
    scheduler.shutdown();
  }

  boolean isRunning() {
    return running;
  }
}
