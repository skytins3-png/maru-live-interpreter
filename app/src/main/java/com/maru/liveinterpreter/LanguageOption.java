package com.maru.liveinterpreter;

import java.util.Locale;

public enum LanguageOption {
    ENGLISH("영어", "en", Locale.US),
    CHINESE("중국어", "zh-CN", Locale.SIMPLIFIED_CHINESE),
    JAPANESE("일본어", "ja", Locale.JAPAN),
    RUSSIAN("러시아어", "ru", new Locale("ru", "RU")),
    BENGALI("방글라데시어", "bn", new Locale("bn", "BD"));

    public final String label;
    public final String code;
    public final Locale voiceLocale;

    LanguageOption(String label, String code, Locale voiceLocale) {
        this.label = label;
        this.code = code;
        this.voiceLocale = voiceLocale;
    }

    public static LanguageOption fromTag(String tag) {
        if (tag == null) return ENGLISH;
        String lower = tag.toLowerCase(Locale.ROOT);
        if (lower.startsWith("zh")) return CHINESE;
        if (lower.startsWith("ja")) return JAPANESE;
        if (lower.startsWith("ru")) return RUSSIAN;
        if (lower.startsWith("bn")) return BENGALI;
        return ENGLISH;
    }

    @Override public String toString() { return label; }
}
