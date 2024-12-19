package space.hajnal.sentinel.camera;

import org.bytedeco.javacv.Frame;

public interface FrameGrabberCallback {

  Frame onFrameGrabbed(Frame frame);
}
