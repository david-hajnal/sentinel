package space.hajnal.sentinel.camera;


import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class SentinelFrameGrabberOptions {

  int imageWidth;
  int imageHeight;
  int frameRate;
  int cameraIndex;
}