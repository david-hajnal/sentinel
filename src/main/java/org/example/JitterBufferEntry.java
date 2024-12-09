package org.example;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class JitterBufferEntry implements Delayed {
  private final byte[] data;
  private final long deliveryTime;

  public JitterBufferEntry(byte[] data, long deliveryTime) {
    this.data = data;
    this.deliveryTime = deliveryTime;
  }

  public byte[] getData() {
    return data;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    return unit.convert(deliveryTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public int compareTo(Delayed o) {
    if (o instanceof JitterBufferEntry) {
      JitterBufferEntry other = (JitterBufferEntry) o;
      return Long.compare(this.deliveryTime, other.deliveryTime);
    }
    return 0;
  }

  @Override
  public String toString() {
    return "JitterBufferEntry{" +
           "deliveryTime=" + deliveryTime +
           ", dataLength=" + (data != null ? data.length : 0) +
           '}';
  }
}
