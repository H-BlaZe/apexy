# Third-party notices

Apexy is licensed under the [GPL-3.0](LICENSE). It bundles or depends on the following, each under its own license.

## Bundled models

| File | Source | License |
|---|---|---|
| `app/src/main/assets/yamnet.tflite` | [YAMNet](https://www.kaggle.com/models/google/yamnet) audio classifier, Google | Apache 2.0 |
| `app/src/main/assets/image_embedder.tflite` | [MediaPipe Image Embedder](https://ai.google.dev/edge/mediapipe/solutions/vision/image_embedder) (MobileNet V3), Google | Apache 2.0 |

## Libraries

| Library | License |
|---|---|
| AndroidX (Compose, Activity, Core, Lifecycle, DataStore, Room, CameraX) | Apache 2.0 |
| Kotlin and kotlinx.coroutines | Apache 2.0 |
| MediaPipe Tasks (vision, audio) | Apache 2.0 |
| Google ML Kit Barcode Scanning | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) |

ML Kit is distributed as a closed-source binary by Google. The Apexy build removes its network permissions, so it runs fully on-device.

All alarm tones, sleep sounds and UI sounds are synthesised at runtime by Apexy's own code. No audio files from other apps are included.
