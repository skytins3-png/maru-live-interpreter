"""Static integrity checks for the WeChat voice relay test feature.

These checks do not substitute for a real device test or translation quality evaluation.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / "app/src/main/java/com/maru/liveinterpreter/WeChatRelayTestActivity.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/maru/liveinterpreter/MainActivity.java").read_text(encoding="utf-8")
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
client = (root / "app/src/main/java/com/maru/liveinterpreter/TranslationClient.java").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")

checks = {
    "Korean voice request": 'recognize("ko-KR", REQUEST_KOREAN)' in src,
    "Chinese voice request": 'recognize("zh-CN", REQUEST_CHINESE)' in src,
    "first English relay hop": 'translator.translate(phrase, from, "en"' in src,
    "second destination hop": 'translator.translate(english, "en", to' in src,
    "reverse English hop": 'translator.translate(finalText, to, "en"' in src,
    "reverse source hop": 'translator.translate(backEnglish, "en", from' in src,
    "speech output": 'tts.speak(text, TextToSpeech.QUEUE_FLUSH' in src,
    "text test": 'private EditText typed;' in src,
    "live call limitations": "does NOT capture audio from WeChat" in src,
    "entry point": "new Intent(this, WeChatRelayTestActivity.class)" in main,
    "activity registration": 'android:name=".WeChatRelayTestActivity"' in manifest,
    "explicit response validation": 'responseStatus != 200' in client,
    "client shutdown": "public void close()" in client,
    "version updated": "0.4.0-wechat-relay-test" in gradle,
    "CI invokes tests": "python3 scripts/check_wechat_relay.py" in workflow,
}
for name, passed in checks.items():
    assert passed, f"FAIL: {name}"
print(f"WECHAT RELAY STATIC CHECK PASS ({len(checks)} checks). Device voice and live calls NOT tested.")
