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
import java.util.List;
import org.bytedeco.javacv.Frame;
import org.example.RTPPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import space.hajnal.sentinel.codec.H264Encoder;

class RTPSocketSenderTest {

  private ServerOptions serverOptions;
  private H264Encoder h264Encoder;
  private DatagramSocket mockSocket;
  private RTPSocketSender rtpSocketSender;
  private RTPPacketSerializer rtpPacketSerializer;
  private Frame frame;

  @BeforeEach
  void setUp() {
    byte[] emptyFrame = new byte[0];
    int mtu = 1400;

    serverOptions = mock(ServerOptions.class);
    h264Encoder = mock(H264Encoder.class);
    rtpPacketSerializer = mock(RTPPacketSerializer.class);
    frame = mock(Frame.class);

    when(serverOptions.getMtu()).thenReturn(mtu); // Example MTU
    when(serverOptions.getServerAddress()).thenReturn("127.0.0.1");
    when(serverOptions.getServerPort()).thenReturn(5004);
    when(h264Encoder.encode(frame)).thenReturn(emptyFrame); // Empty frame
    when(rtpPacketSerializer.serialize(emptyFrame, mtu, 1, 12345)).thenReturn(List.of());

    mockSocket = mock(DatagramSocket.class);

    // Use a spy to inject the mock socket into the RTPStream class
    rtpSocketSender = spy(new RTPSocketSender(serverOptions, h264Encoder, rtpPacketSerializer));
    doReturn(true).when(mockSocket).isClosed();
  }

  @Test
  void testOpenAndCloseSocket() {
    // Act
    rtpSocketSender.open(mockSocket);

    // Assert
    assertNotNull(rtpSocketSender, "Socket should be opened.");
    verify(rtpSocketSender, times(1)).open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act
    rtpSocketSender.close();

    // Assert
    verify(mockSocket, times(1)).close();
    verify(mockSocket, times(1)).isClosed();
  }

  @Test
  void testSendEncodesFrameAndSendsPackets() throws Exception {
    // Arrange

    long timestamp = 12345L;

    byte[] encodedFrame = new byte[5000]; // Simulate encoded frame data
    for (int i = 0; i < encodedFrame.length; i++) {
      encodedFrame[i] = (byte) (i % 256);
    }

    when(h264Encoder.encode(frame)).thenReturn(encodedFrame);
    when(rtpPacketSerializer.serialize(encodedFrame, 1400, timestamp, 123456)).thenReturn(
        List.of(new RTPPacket(96, 0, timestamp, 12345, new byte[1400], false),
            new RTPPacket(96, 1, timestamp, 12345, new byte[1400], false),
            new RTPPacket(96, 2, timestamp, 12345, new byte[1400], false),
            new RTPPacket(96, 3, timestamp, 12345, new byte[400], true)));

    rtpSocketSender.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act
    rtpSocketSender.send(frame, timestamp);

    // Assert
    ArgumentCaptor<DatagramPacket> packetCaptor = ArgumentCaptor.forClass(DatagramPacket.class);
    verify(mockSocket, atLeastOnce()).send(packetCaptor.capture());
    verify(h264Encoder, times(1)).encode(frame);
    verify(rtpPacketSerializer, times(1)).serialize(encodedFrame, 1400, timestamp, 123456);
    verify(mockSocket).isClosed();
    verify(mockSocket).setSendBufferSize(65536);

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
    // Act & Assert
    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> rtpSocketSender.send(frame, 12345L));
    assertEquals("Socket is not open. Call open() before sending frames.", exception.getMessage());
  }

  @Test
  void testSendHandlesEmptyFrame() throws Exception {
    // Arrange
    when(h264Encoder.encode(frame)).thenReturn(new byte[0]); // Empty frame

    rtpSocketSender.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);

    // Act
    rtpSocketSender.send(frame, 12345L);

    // Assert
    verify(mockSocket, never()).send(any(DatagramPacket.class)); // No packets should be sent
  }

  @Test
  void testSendHandlesExceptionGracefully() throws Exception {
    // Arrange
    when(h264Encoder.encode(frame)).thenThrow(new RuntimeException("Encoding error"));

    rtpSocketSender.open(mockSocket);
    when(mockSocket.isClosed()).thenReturn(false);
    // Act & Assert
    assertDoesNotThrow(() -> rtpSocketSender.send(frame, 12345L),
        "Send method should handle exceptions gracefully.");
  }
}
