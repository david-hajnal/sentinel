package org.example;

import javax.swing.*;
import java.awt.*;

public class Server {
  public static void main(String[] args) {
    JFrame frame = new JFrame("RTP Server");
    JButton startButton = new JButton("Start");
    JButton stopButton = new JButton("Stop");

    RTPStreamReceiver receiver = new RTPStreamReceiver(5004);

    startButton.addActionListener(e -> receiver.start());
    stopButton.addActionListener(e -> receiver.stop());

    JPanel panel = new JPanel();
    panel.add(startButton);
    panel.add(stopButton);

    frame.add(panel, BorderLayout.CENTER);
    frame.setSize(300, 100);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);
  }
}
