package space.hajnal.sentinel.camera.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SentinelFrame {

  private final byte[] data;
  private final long timestamp;
}
