# Owner speaker model asset

The Android build fetches the ECAPA-TDNN ONNX speaker-embedding model into generated Android assets and bundles it into the APK. The binary is deliberately not stored as a normal Git blob because it is about 83 MB.

- Asset path inside the APK: `owner_voice/ecapa_tdnn.onnx`
- Source: `astrolabos/ecapa-tdnn` ONNX export of SpeechBrain `spkrec-ecapa-voxceleb`
- Licence declared by the model card: Apache-2.0
- Expected size: 83,404,680 bytes
- Expected SHA-256: `9acb086d5d9efca07266930ce03772f13cad5fd5a415bcf014ee4d82bed26311`
- Download URL: https://huggingface.co/astrolabos/ecapa-tdnn/resolve/main/ecapa_tdnn.onnx

The Gradle task refuses to package the model if its size or checksum differs. It requires network access at build time. This adds the model binary and ONNX Runtime dependency to the build, but does **not** by itself complete speaker verification: audio capture, the model's expected feature extraction/preprocessing, enrollment storage, calibrated matching thresholds, anti-replay/liveness checks, and device tests still must be implemented before it can authorize sensitive operations. Do not treat phrase transcription as speaker verification.
