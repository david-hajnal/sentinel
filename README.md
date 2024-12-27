# RTP Protocol for Java

Send and receive RTP packets in Java.
Currently focusing on video streaming only.

* Grab image with JavaCV
* Send image with RTP
* Receive image with RTP
* Display image with JavaCV
----
* Todo auth
* Todo control

# Setup 

## JavaCV

for mac os arm64 libOrbbecSDK needs to be linked manually
https://github.com/orbbec/OrbbecSDK

```bash
ln -s /path/to/libOrbbecSDK.1.9.dylib /Users/david.hajnal/.javacpp/cache/opencv-4.10.0-1.5.11-macosx-arm64.jar/org/bytedeco/opencv/macosx-arm64/libOrbbecSDK.1.9.dylib
```