package coupons.api;

import java.util.*;

/** Explicit, dependency-free JSON support for the small API contract. */
final class Json {
    private Json() {}
    static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\');
            if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else out.append(c);
        }
        return out.append('"').toString();
    }
    static String object(Map<String, ?> values) {
        StringJoiner out = new StringJoiner(",", "{", "}");
        values.forEach((key, value) -> out.add(quote(key) + ":" + value(value)));
        return out.toString();
    }
    static String array(Collection<?> values) {
        StringJoiner out = new StringJoiner(",", "[", "]");
        values.forEach(value -> out.add(value(value)));
        return out.toString();
    }
    private static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Character) return quote(value.toString());
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Collection<?> collection) return array(collection);
        return quote(value.toString());
    }
    static Map<String, String> parseObject(String text) {
        text = Objects.requireNonNull(text).trim();
        if (!text.startsWith("{") || !text.endsWith("}")) throw new IllegalArgumentException("JSON object required.");
        Map<String, String> result = new LinkedHashMap<>();
        int position = 1;
        while (true) {
            position = skip(text, position);
            if (position >= text.length() - 1) return result;
            Parsed key = string(text, position);
            position = skip(text, key.end());
            if (position >= text.length() || text.charAt(position++) != ':') throw new IllegalArgumentException("Invalid JSON.");
            position = skip(text, position);
            String value;
            if (text.charAt(position) == '"') {
                Parsed parsed = string(text, position);
                value = parsed.value();
                position = parsed.end();
            } else {
                int start = position;
                int depth = 0;
                while (position < text.length()) {
                    char c = text.charAt(position);
                    if (c == '{' || c == '[') depth++;
                    if (c == '}' || c == ']') depth--;
                    if (depth == 0 && (c == ',' || c == '}')) break;
                    position++;
                }
                value = text.substring(start, position).trim();
                if ("null".equals(value)) value = null;
            }
            result.put(key.value(), value);
            position = skip(text, position);
            if (position < text.length() && text.charAt(position) == ',') { position++; continue; }
            if (position < text.length() && text.charAt(position) == '}') return result;
            throw new IllegalArgumentException("Invalid JSON.");
        }
    }
    private static int skip(String text, int position) {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        return position;
    }
    private record Parsed(String value, int end) {}
    private static Parsed string(String text, int position) {
        if (text.charAt(position++) != '"') throw new IllegalArgumentException("JSON string required.");
        StringBuilder value = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return new Parsed(value.toString(), position);
            if (c == '\\' && position < text.length()) {
                char escaped = text.charAt(position++);
                value.append(escaped == 'n' ? '\n' : escaped == 'r' ? '\r' : escaped == 't' ? '\t' : escaped);
            } else value.append(c);
        }
        throw new IllegalArgumentException("Unterminated JSON string.");
    }
}
