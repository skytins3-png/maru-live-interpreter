package com.maru.liveinterpreter;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TranslationClient {
    public interface Callback { void onResult(String translated); void onError(String message); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void translateKorean(String text, LanguageOption target, Callback callback) {
        translate(text, "ko", target.code, callback);
    }

    public void translateToKorean(String text, String sourceCode, Callback callback) {
        translate(text, sourceCode, "ko", callback);
    }

    public void translate(String text, String sourceCode, String targetCode, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String query = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
                String pair = URLEncoder.encode(sourceCode + "|" + targetCode, StandardCharsets.UTF_8.name());
                URL url = new URL("https://api.mymemory.translated.net/get?q=" + query + "&langpair=" + pair);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("번역 서버 응답 " + status);
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject root = new JSONObject(body.toString());
                String translated = root.getJSONObject("responseData").optString("translatedText", "").trim();
                if (translated.isEmpty()) throw new IllegalStateException("번역 결과가 비어 있습니다");
                main.post(() -> callback.onResult(translated));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "인터넷 또는 번역 서버를 확인해 주세요" : e.getMessage();
                main.post(() -> callback.onError(message));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
