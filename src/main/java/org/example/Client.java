package org.example;

import javax.swing.*;

public class Client {
  public static void main(String[] args) {
    JFrame frame = new JFrame("RTP Client");
    JButton startButton = new JButton("Start");
    JButton stopButton = new JButton("Stop");

    RTPStreamSender sender = new RTPStreamSender("127.0.0.1", 5004);

    startButton.addActionListener(e -> sender.start());
    stopButton.addActionListener(e -> sender.stop());

    JPanel panel = new JPanel();
    panel.add(startButton);
    panel.add(stopButton);

    frame.add(panel);
    frame.setSize(300, 100);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);
  }
}
