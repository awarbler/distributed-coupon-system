package coupons.api;

import java.util.*;

/** Minimal explicit JSON encoder/parser for the API contract. */
final class Json {
    private Json() {}
    static String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    static String object(Map<String, ?> values) {
        StringJoiner out = new StringJoiner(",", "{", "}");
        values.forEach((key, value) -> out.add(quote(key) + ":" + encode(value)));
        return out.toString();
    }
    static String array(Collection<?> values) {
        StringJoiner out = new StringJoiner(",", "[", "]");
        values.forEach(value -> out.add(encode(value)));
        return out.toString();
    }
    private static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Character) return quote(value.toString());
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Collection<?> c) return array(c);
        return quote(value.toString());
    }
    static Map<String, String> objectFields(String input) {
        input = Objects.requireNonNull(input).trim();
        if (!input.startsWith("{") || !input.endsWith("}")) throw new IllegalArgumentException("JSON object required.");
        Map<String, String> result = new LinkedHashMap<>();
        String content = input.substring(1, input.length() - 1).trim();
        if (content.isEmpty()) return result;
        for (String part : content.split(",")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) throw new IllegalArgumentException("Invalid JSON object.");
            String key = pair[0].trim(), value = pair[1].trim();
            if (!key.startsWith("\"") || !key.endsWith("\"")) throw new IllegalArgumentException("JSON keys must be strings.");
            result.put(key.substring(1, key.length() - 1), value.startsWith("\"") && value.endsWith("\"")
                    ? value.substring(1, value.length() - 1) : ("null".equals(value) ? null : value));
        }
        return result;
    }
}
