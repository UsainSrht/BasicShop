package me.usainsrht.basicshop.config;

import java.util.Locale;

public enum FilterMode {
    BLACKLIST,
    WHITELIST;

    public static FilterMode fromString(String str) {
        if (str == null || str.isBlank()) {
            return BLACKLIST;
        }
        try {
            return FilterMode.valueOf(str.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BLACKLIST;
        }
    }
}
