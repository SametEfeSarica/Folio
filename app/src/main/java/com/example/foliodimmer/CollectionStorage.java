package com.example.foliodimmer;

import java.nio.charset.Charset;
import java.util.Collection;

/** Small, deterministic helpers for collection names and preference storage keys. */
final class CollectionStorage {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private CollectionStorage() { }

    static String normalizeName(String name) {
        if (name == null) return null;
        String normalized = name.trim();
        return normalized.length() == 0 ? null : normalized;
    }

    static boolean isDuplicateName(Collection<String> existingNames, String candidate,
                                   String editingName) {
        String normalized = normalizeName(candidate);
        if (normalized == null || existingNames == null) return false;
        for (String existing : existingNames) {
            if (existing == null) continue;
            if (editingName != null && existing.equalsIgnoreCase(editingName)) continue;
            if (existing.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /** Hex-encodes UTF-8 bytes so arbitrary identifiers cannot collide like String.hashCode(). */
    static String keyPart(String value) {
        if (value == null) throw new IllegalArgumentException("Preference key value is required");
        byte[] bytes = value.getBytes(UTF_8);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            int unsigned = valueByte & 0xff;
            result.append(hex[unsigned >>> 4]);
            result.append(hex[unsigned & 0x0f]);
        }
        return result.toString();
    }

    static String key(String prefix, String value) {
        return prefix + keyPart(value);
    }
}
