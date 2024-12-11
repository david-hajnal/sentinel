package space.hajnal.sentinel.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import org.bytedeco.javacv.Frame;
import org.example.RTPPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.hajnal.sentinel.codec.H264Encoder;

class RTPSocketTest {

  private ServerOptions serverOptions;
  private H264Encoder h264Encoder;
  private DatagramSocket mockSocket;
  private RTPSocket rtpSocket;

  @BeforeEach
  void setUp() throws Exception {
    serverOptions = mock(ServerOptions.class);
    h264Encoder = mock(H264Encoder.class);

    when(serverOptions.getMtu()).thenReturn(1400); // Example MTU
    when(serverOptions.getServerAddress()).thenReturn("127.0.0.1");
    when(serverOptions.getServerPort()).thenReturn(5004);

    mockSocket = mock(DatagramSocket.class);

    // Use a spy to inject the mock socket into the RTPStream class
    rtpSocket = spy(new RTPSocket(serverOptions, h264Encoder));
    doReturn(true).when(mockSocket).isClosed();
  }

  @Test
  void testOpenAndCloseSocket() throws Exception {
    // Act
    rtpSocket.open(mockSocket);

    // Assert
    assertNotNull(rtpSocket, "Socket should be opened.");
    verify(rtpSocket, times(1)).open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act
    rtpSocket.close();

    // Assert
    verify(mockSocket, times(1)).close();
    verify(mockSocket, times(1)).isClosed();
  }

  @Test
  void testSendEncodesFrameAndSendsPackets() throws Exception {
    // Arrange
    Frame frame = mock(Frame.class);
    long timestamp = 12345L;

    byte[] encodedFrame = new byte[5000]; // Simulate encoded frame data
    for (int i = 0; i < encodedFrame.length; i++) {
      encodedFrame[i] = (byte) (i % 256);
    }

    when(h264Encoder.encode(frame)).thenReturn(encodedFrame);

    rtpSocket.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act
    rtpSocket.send(frame, timestamp);

    // Assert
    ArgumentCaptor<DatagramPacket> packetCaptor = ArgumentCaptor.forClass(DatagramPacket.class);
    verify(mockSocket, atLeastOnce()).send(packetCaptor.capture());

    // Check the captured packets
    for (DatagramPacket packet : packetCaptor.getAllValues()) {
      byte[] data = packet.getData();
      RTPPacket rtpPacket = RTPPacket.fromBytes(data);

      assertEquals(96, rtpPacket.getPayloadType(), "Payload type should be 96 (dynamic H.264).");
      assertEquals(timestamp, rtpPacket.getTimestamp(),
          "Timestamp should match the provided value.");
      assertNotNull(rtpPacket.getPayload(), "Payload should not be null.");
    }
  }

  @Test
  void testSendWithoutOpenThrowsException() {
    // Arrange
    Frame frame = mock(Frame.class);

    // Act & Assert
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> rtpSocket.send(frame, 12345L));
    assertEquals("Socket is not open. Call open() before sending frames.", exception.getMessage());
  }

  @Test
  void testSendHandlesEmptyFrame() throws Exception {
    // Arrange
    Frame frame = mock(Frame.class);
    when(h264Encoder.encode(frame)).thenReturn(new byte[0]); // Empty frame

    rtpSocket.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);

    // Act
    rtpSocket.send(frame, 12345L);

    // Assert
    verify(mockSocket, never()).send(any(DatagramPacket.class)); // No packets should be sent
  }

  @Test
  void testSendHandlesExceptionGracefully() throws Exception {
    // Arrange
    Frame frame = mock(Frame.class);
    when(h264Encoder.encode(frame)).thenThrow(new RuntimeException("Encoding error"));

    rtpSocket.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act & Assert
    assertDoesNotThrow(() -> rtpSocket.send(frame, 12345L),
        "Send method should handle exceptions gracefully.");
  }
}
