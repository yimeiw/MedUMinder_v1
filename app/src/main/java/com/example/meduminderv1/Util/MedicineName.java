package com.example.meduminderv1.Util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Aturan nama obat yang SAMA di app dan di skrip migrasi (tools/firestore):
 * - key: spasi depan/belakang dibuang, spasi ganda jadi satu, huruf kecil -> untuk membandingkan
 * - docId: SHA-1 dari key -> id obat pribadi, jadi nama yang sama selalu dokumen yang sama (tidak dobel)
 */
public final class MedicineName {
    private MedicineName() {}

    // karakter spasi yang dianggap sama (persis sama dengan WHITESPACE di tools/firestore/medicine-name.js)
    private static final String WHITESPACE = "[ \\t\\n\\u000B\\f\\r\\u00A0]+";

    public static String key(String name) {
        if (name == null) return "";
        return name.replaceAll(WHITESPACE, " ").replaceAll("^ | $", "").toLowerCase(Locale.ROOT);
    }

    public static String docId(String name) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(key(name).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1 selalu ada di Android
        }
    }
}
