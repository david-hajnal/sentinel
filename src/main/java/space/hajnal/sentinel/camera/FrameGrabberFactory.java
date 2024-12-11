package space.hajnal.sentinel.camera;

import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;

public class FrameGrabberFactory {

  public FrameGrabber create(int cameraIndex) {
    return new OpenCVFrameGrabber(cameraIndex);
  }
}
