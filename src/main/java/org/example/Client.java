package org.example;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Client {
  public static void main(String[] args) {
    JFrame frame = new JFrame("RTP Client");
    JButton startButton = new JButton("Start");
    JButton stopButton = new JButton("Stop");
    JButton testButton = new JButton("Test");

    RTPStreamSender sender = new RTPStreamSender("127.0.0.1", 5004);

    startButton.addActionListener(e -> sender.start());
    stopButton.addActionListener(e -> sender.stop());

    testButton.addActionListener(e -> {
      // Path to the test image
      String testImagePath = "/Users/david.hajnal/documents/test.jpg"; // Replace with the actual path
      sender.sendTestImage(testImagePath);
    });

    JPanel panel = new JPanel();
    panel.add(startButton);
    panel.add(stopButton);
    panel.add(testButton);

    frame.add(panel);
    frame.setSize(400, 100);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);
  }
}
