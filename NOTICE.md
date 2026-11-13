# Third-party notices

Apexy is licensed under the [GPL-3.0](LICENSE). It bundles or depends on the following, each under its own license.

## Bundled models

| File | Source | License |
|---|---|---|
| `app/src/full/assets/yamnet.tflite` | [YAMNet](https://www.kaggle.com/models/google/yamnet) audio classifier, Google | Apache 2.0 |
| `app/src/full/assets/image_embedder.tflite` | [MediaPipe Image Embedder](https://ai.google.dev/edge/mediapipe/solutions/vision/image_embedder) (MobileNet V3), Google | Apache 2.0 |

## Libraries

| Library | License |
|---|---|
| AndroidX (Compose, Activity, Core, Lifecycle, DataStore, Room, CameraX) | Apache 2.0 |
| Kotlin and kotlinx.coroutines | Apache 2.0 |
| ZXing (QR and barcode scanning) | Apache 2.0 |
| MediaPipe Tasks (vision, audio), `full` flavor only | Apache 2.0 |

MediaPipe is distributed as prebuilt binaries by Google and pulls in Google's data-transport libraries. The Apexy build removes its network permissions, so it runs fully on-device. The `foss` flavor (F-Droid) includes neither MediaPipe nor the two models.

All alarm tones, sleep sounds and UI sounds are synthesised at runtime by Apexy's own code. No audio files from other apps are included.
