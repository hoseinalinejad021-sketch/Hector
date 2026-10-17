# Owner speaker model asset

The preferred model asset is the dynamically quantized INT8 ECAPA-TDNN ONNX model at `app/src/main/assets/owner_voice/ecapa_tdnn_int8.onnx`. The original upstream model is about 83 MB; INT8 quantization is intended to reduce repository and APK size.

- Source: `astrolabos/ecapa-tdnn`, an ONNX export of SpeechBrain `spkrec-ecapa-voxceleb`
- Licence declared by the model card: Apache-2.0
- Original model size: 83,404,680 bytes
- Original model SHA-256: `9acb086d5d9efca07266930ce03772f13cad5fd5a415bcf014ee4d82bed26311`
- Original source: https://huggingface.co/astrolabos/ecapa-tdnn/resolve/main/ecapa_tdnn.onnx

## Generate and commit the binary

The workflow `.github/workflows/quantize-owner-voice-model.yml` downloads and verifies the upstream file, dynamically quantizes its weights to INT8, checks that ONNX Runtime can load the result, and commits the resulting binary to this branch. Run it from GitHub Actions using **Run workflow** on `feature/owner-face-voice-matching`. It requires repository Actions permissions that allow the workflow's `GITHUB_TOKEN` to write contents.

Until that workflow completes successfully, the quantized binary is not yet present in Git. Gradle prefers the committed INT8 asset when present; otherwise it falls back to downloading the checksum-verified original model at build time.

Quantization and successful ONNX loading do **not** by themselves complete speaker verification. Audio capture, model-specific input feature extraction/preprocessing, enrollment storage, calibrated matching thresholds, anti-replay/liveness checks, and real-device tests must still be implemented before speaker identity can authorize sensitive operations. Do not treat phrase transcription as speaker verification.
