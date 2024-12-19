package space.hajnal.sentinel.network.model;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * A wrapper class for RTP packets that can be dropped after a certain delay.
 */
public class DelayedRTPPacket implements Delayed {

  @Getter
  private final RTPPacket packet;
  private final long expirationTime;

  public DelayedRTPPacket(RTPPacket packet, long ttlMillis) {
    this.packet = packet;
    this.expirationTime = System.currentTimeMillis() + ttlMillis;
  }

  @Override
  public long getDelay(TimeUnit unit) {
    long delay = expirationTime - System.currentTimeMillis();
    return unit.convert(delay, TimeUnit.MILLISECONDS);
  }

  @Override
  public int compareTo(Delayed other) {
    if (other instanceof DelayedRTPPacket) {
      return Long.compare(this.expirationTime, ((DelayedRTPPacket) other).expirationTime);
    }
    return 0;
  }
}
