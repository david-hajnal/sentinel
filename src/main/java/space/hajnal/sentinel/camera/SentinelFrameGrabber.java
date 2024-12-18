package space.hajnal.sentinel.camera;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

@Slf4j
public class SentinelFrameGrabber implements AutoCloseable {

  private final SentinelFrameGrabberOptions options;
  private boolean running = false;
  private final ScheduledExecutorService scheduler;
  private final FrameGrabberFactory frameGrabberFactory;

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

  public void capture(FrameGrabberCallback fn) {

    if (fn == null) {
      throw new IllegalArgumentException("Function cannot be null");
    }

    try (FrameGrabber grabber = frameGrabberFactory.create(options.getCameraIndex())) {

      running = true;
      grabber.setImageWidth(options.getImageWidth());
      grabber.setImageHeight(options.getImageHeight());
      grabber.setFrameRate(options.getFrameRate());
      grabber.start();

      long frameDurationMillis = 1000 / options.getFrameRate();

      scheduler.scheduleAtFixedRate(() -> {
        if (!running) {
          return;
        }

        try {
          Frame frame = grabber.grab();
          if (frame == null) {
            log.info("No more frames to grab.");
          } else {
            fn.onFrameGrabbed(frame);
          }
        } catch (Exception e) {
          log.error("Error while capturing frames", e);
        }
      }, 100, frameDurationMillis, TimeUnit.MILLISECONDS);

    } catch (Exception e) {
      log.error("Error initializing frame grabber", e);
      running = false;
      scheduler.shutdown();
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
