package com.jacky8399.portablebeacons.i18n;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public record Context(Locale locale, Map<String, Placeholder> placeholders) {
    public Context {
        placeholders = Map.copyOf(placeholders);
    }

    public Context(Map<String, Placeholder> placeholders) {
        this(Locale.US, placeholders);
    }

    @Nullable
    public Placeholder getPlaceholder(String key) {
        return placeholders.get(key);
    }

    public Context withLocale(Locale locale) {
        return new Context(locale, placeholders);
    }
}
