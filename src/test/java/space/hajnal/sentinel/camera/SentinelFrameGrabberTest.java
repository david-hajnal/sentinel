package space.hajnal.sentinel.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.SneakyThrows;
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
    CountDownLatch latch = new CountDownLatch(1);
    FrameGrabberCallback frameProcessor = mock(FrameGrabberCallback.class);
    when(frameProcessor.onFrameGrabbed(any(Frame.class), anyLong())).thenAnswer(invocation -> {
      latch.countDown();
      return null;
    });
    // Act
    frameGrabberInstance.capture(frameProcessor);
    assertTrue(latch.await(1, java.util.concurrent.TimeUnit.SECONDS),
        "Latch should have counted down after frame processing.");

    // Assert
    verify(frameProcessor, times(1)).onFrameGrabbed(any(Frame.class), anyLong());
  }

  @Test
  void testGrabberIsConfiguredCorrectly() throws Exception {
    // Arrange
    FrameGrabberCallback frameProcessor = mock(FrameGrabberCallback.class);

    // Act
    frameGrabberInstance.capture(frameProcessor);

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


}
