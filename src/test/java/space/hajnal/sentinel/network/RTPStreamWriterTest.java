package space.hajnal.sentinel.network;

import java.util.concurrent.CountDownLatch;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.hajnal.sentinel.camera.FrameGrabberCallback;
import space.hajnal.sentinel.camera.SentinelFrameGrabber;
import space.hajnal.sentinel.network.sender.RTPSocketSender;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RTPStreamWriterTest {

  private RTPSocketSender mockSender;
  private SentinelFrameGrabber mockFrameGrabber;
  private ExecutorService threadPool;
  private RTPStreamWriter rtpStreamWriter;

  @BeforeEach
  void setUp() {
    mockSender = mock(RTPSocketSender.class);
    mockFrameGrabber = mock(SentinelFrameGrabber.class);
    threadPool = Executors.newSingleThreadScheduledExecutor();
    rtpStreamWriter = new RTPStreamWriter(mockSender, mockFrameGrabber, threadPool);
  }

  @AfterEach
  void tearDown() throws Exception {
    rtpStreamWriter.close();
    threadPool.shutdown();
  }

  @Test
  void testStart_CapturesAndSendsFrames() throws InterruptedException {
    // Arrange: Simulate a frame capture callback
    Frame frame = mock(Frame.class);
    CountDownLatch latch = new CountDownLatch(1);
    doAnswer(_ -> {
      mockSender.send(frame, 1L);
      latch.countDown();
      return frame;
    }).when(mockFrameGrabber).capture(any());

    // Act
    rtpStreamWriter.start();
    assertTrue(latch.await(1, TimeUnit.SECONDS), "Frame capture callback was not invoked");

    // Assert: Verify that frames are sent
    verify(mockSender, atLeast(1)).send(eq(frame), anyLong());
    verify(mockFrameGrabber, atLeast(1)).capture(any());
  }

  @Test
  void testStart_HandleFrameGrabberException() throws Exception {
    // Arrange: Simulate an exception during frame grabbing
    doThrow(new RuntimeException("Frame grabber exception")).when(mockFrameGrabber).capture(any());

    // Act
    rtpStreamWriter.start();

    // Allow some time for the thread to execute
    threadPool.shutdown();
    assertTrue(threadPool.awaitTermination(1, TimeUnit.SECONDS), "Thread pool did not terminate");

    // Assert: Verify that an exception is logged and no frames are sent
    verify(mockFrameGrabber, times(1)).capture(any());
    verifyNoInteractions(mockSender);
  }

  @Test
  void testClose_ClosesResources() throws Exception {
    // Act
    rtpStreamWriter.close();

    // Assert: Ensure both the frame grabber and thread pool are closed
    verify(mockFrameGrabber, times(1)).close();
    assertTrue(threadPool.isShutdown(), "Thread pool should be shut down");
  }

  @Test
  void testClose_HandleFrameGrabberCloseException() throws Exception {
    // Arrange: Simulate an exception during frame grabber close
    doThrow(new RuntimeException("Close exception")).when(mockFrameGrabber).close();

    // Act
    rtpStreamWriter.close();

    // Assert: Ensure the thread pool is still shut down despite the exception
    verify(mockFrameGrabber, times(1)).close();
    assertTrue(threadPool.isShutdown(), "Thread pool should be shut down");
  }
}
