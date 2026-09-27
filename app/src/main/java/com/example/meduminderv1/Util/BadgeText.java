package com.example.meduminderv1.Util;

public final class BadgeText {
    private BadgeText() {}

    public static String of(int count) {
        if (count >= 100) return "99+";
        if (count > 50) return "50+";
        return String.valueOf(count);
    }
}
