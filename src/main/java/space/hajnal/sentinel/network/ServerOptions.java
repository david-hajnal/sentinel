package space.hajnal.sentinel.network;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ServerOptions {

  private final String serverAddress;
  private final int serverPort;
  private final int mtu;
}
