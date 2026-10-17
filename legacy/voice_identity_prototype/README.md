# Archived voice identity prototype

These Kotlin files are preserved for reference but are intentionally outside `app/src/main`, so they are not compiled into the active Android app.

Reason: the current requested feature is conversational speech input/output, which uses Android's installed speech recognition service and TextToSpeech. The prototype is not wired into login, its expected ONNX model asset is absent, and it has no verified liveness or replay resistance. It must not be used to authorize access or sensitive operations. Do not move these files back into the Android source set until the model, licensing/checksum, runtime permission handling, liveness design, and device tests are complete.
