package space.hajnal.sentinel.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SentinelFrameGrabberTest {

  private SentinelFrameGrabberOptions options;
  private FrameGrabberFactory frameGrabberFactory;
  private FrameGrabber frameGrabber;
  private ScheduledExecutorService scheduler;
  private SentinelFrameGrabber frameGrabberInstance;

  @BeforeEach
  void setUp() throws Exception {
    options = mock(SentinelFrameGrabberOptions.class);
    frameGrabberFactory = mock(FrameGrabberFactory.class);
    frameGrabber = mock(FrameGrabber.class);

    // Mock options
    when(options.getImageWidth()).thenReturn(640);
    when(options.getImageHeight()).thenReturn(480);
    when(options.getFrameRate()).thenReturn(30);
    when(options.getCameraIndex()).thenReturn(0);

    // Mock FrameGrabberFactory
    when(frameGrabberFactory.create(anyInt())).thenReturn(frameGrabber);

    // Mock FrameGrabber
    when(frameGrabber.grab()).thenReturn(new Frame(), null); // Return one frame, then null

    scheduler = Executors.newSingleThreadScheduledExecutor();

    frameGrabberInstance = new SentinelFrameGrabber(scheduler, options, frameGrabberFactory);
  }

  @AfterEach
  void tearDown() throws Exception {
    frameGrabberInstance.close();
    scheduler.shutdownNow();
  }

  @Test
  void testCaptureFunctionIsCalledWithFrame() throws Exception {
    // Arrange
    Function<Frame, Void> frameProcessor = mock(Function.class);

    // Act
    frameGrabberInstance.capture(frameProcessor);

    // Wait for the scheduled tasks to run
    Thread.sleep(200); // Allow time for the scheduler to execute

    // Assert
    verify(frameProcessor, times(1)).apply(any(Frame.class)); // Ensure the frame processor was called once
  }

  @Test
  void testSchedulerStopsAfterNullFrame() throws Exception {
    // Arrange
    Function<Frame, Void> frameProcessor = mock(Function.class);

    // Act
    frameGrabberInstance.capture(frameProcessor);

    // Wait for the scheduled tasks to run
    Thread.sleep(500); // Allow time for the scheduler to execute

    // Assert
    assertTrue(scheduler.isShutdown(), "Scheduler should have been shut down after grabbing null frame.");
  }

  @Test
  void testGrabberIsConfiguredCorrectly() throws Exception {
    // Arrange
    Function<Frame, Void> frameProcessor = mock(Function.class);

    // Act
    frameGrabberInstance.capture(frameProcessor);

    // Wait for the scheduled tasks to run
    Thread.sleep(200); // Allow time for the scheduler to execute

    // Assert
    verify(frameGrabber).setImageWidth(640);
    verify(frameGrabber).setImageHeight(480);
    verify(frameGrabber).setFrameRate(30);
    verify(frameGrabber).start();
  }

  @Test
  void testCloseStopsRunningAndScheduler() throws Exception {
    // Act
    frameGrabberInstance.close();

    // Assert
    assertFalse(frameGrabberInstance.isRunning(), "Running should be set to false after closing.");
    assertTrue(scheduler.isShutdown(), "Scheduler should be shut down after closing.");
  }

  @Test
  void testErrorDuringFrameProcessingDoesNotCrashScheduler() throws Exception {
    // Arrange
    Function<Frame, Void> frameProcessor = mock(Function.class);
    doThrow(new RuntimeException("Simulated error")).when(frameProcessor).apply(any(Frame.class));

    // Act
    frameGrabberInstance.capture(frameProcessor);

    // Wait for the scheduled tasks to run
    Thread.sleep(200); // Allow time for the scheduler to execute

    // Assert
    assertTrue(scheduler.isShutdown(), "Scheduler should have shut down after processing error.");
  }

}
