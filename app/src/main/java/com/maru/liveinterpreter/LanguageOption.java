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

    @Override public String toString() { return label; }
}
