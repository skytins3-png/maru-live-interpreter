from pathlib import Path

root = Path(__file__).resolve().parents[1]
required = [
    "settings.gradle", "build.gradle", "app/build.gradle",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/maru/liveinterpreter/MainActivity.java",
    "app/src/main/java/com/maru/liveinterpreter/InterpreterEngine.java",
    "app/src/main/java/com/maru/liveinterpreter/TranslationClient.java",
    "app/src/main/java/com/maru/liveinterpreter/FloatingInterpreterService.java",
    ".github/workflows/build-apk.yml",
]
for relative in required:
    path = root / relative
    assert path.is_file() and path.stat().st_size > 0, f"missing: {relative}"

manifest = (root / required[3]).read_text(encoding="utf-8")
for permission in ("RECORD_AUDIO", "INTERNET", "SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE"):
    assert permission in manifest, f"missing permission: {permission}"

engine = (root / required[5]).read_text(encoding="utf-8")
assert '"ko-KR"' in engine
assert "setSpeechRate(0.92f)" in engine
assert "setPitch(0.86f)" in engine

activity = (root / required[4]).read_text(encoding="utf-8")
assert "android.graphics.Typeface.BOLD" in activity
assert "noinspection UnspecifiedRegisterReceiverFlag" in activity
assert "setTypeface(null, 1)" not in activity

workflow = (root / required[8]).read_text(encoding="utf-8")
assert "V0.2.0" in workflow
assert "lintDebug assembleDebug" in workflow

assert "listenRemoteAuto" in engine
assert "EXTRA_ENABLE_LANGUAGE_DETECTION" in engine
assert "EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES" in engine
assert "EXTRA_ENABLE_LANGUAGE_SWITCH" in engine
assert "EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES" in engine
assert "onLanguageDetection" in engine
assert "translateToKorean" in (root / "app/src/main/java/com/maru/liveinterpreter/TranslationClient.java").read_text(encoding="utf-8")

languages = (root / "app/src/main/java/com/maru/liveinterpreter/LanguageOption.java").read_text(encoding="utf-8")
for code in ('"en"', '"zh-CN"', '"ja"', '"ru"', '"bn"'):
    assert code in languages, f"missing language: {code}"

print(f"PROJECT CHECK PASS: {len(required)} required files, 4 permissions, 5 target languages, 2 lint fixes, bidirectional auto-detection")
