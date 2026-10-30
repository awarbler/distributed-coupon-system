package coupons.api;

import java.math.BigDecimal;
import java.util.*;

/** Strict JSON codec for the explicit HTTP DTOs; never deserializes Java objects. */
final class Json {
    private Json() {}
    static String quote(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    static String object(Map<String, ?> values) { return encode(values); }
    static String array(Collection<?> values) { return encode(values); }
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof coupons.promotions.CouponApplied coupon) {
            Map<String,Object> fields=new LinkedHashMap<>();
            fields.put("applicationId",coupon.getApplicationId());fields.put("printedLabel",coupon.getPrintedLabel());
            fields.put("appliedAmount",coupon.getAppliedAmount());fields.put("lineItemIds",coupon.getLineItemIds());
            fields.put("qualifyingQuantities",coupon.getQualifyingQuantities());fields.put("requiredQuantity",coupon.getRequiredQuantity());fields.put("wholeReceipt",coupon.isWholeReceipt());
            return encode(fields);
        }
        if (value instanceof Map<?,?> map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            map.forEach((k,v) -> out.add(quote(k.toString()) + ":" + encode(v))); return out.toString();
        }
        if (value instanceof Collection<?> list) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            list.forEach(v -> out.add(encode(v))); return out.toString();
        }
        if (value.getClass().isRecord()) {
            Map<String,Object> fields = new LinkedHashMap<>();
            try {
                for (var component : value.getClass().getRecordComponents())
                    fields.put(component.getName(), component.getAccessor().invoke(value));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot encode response.", e); }
            return encode(fields);
        }
        return quote(value.toString());
    }
    static Map<String,Object> parseObject(String input) {
        Parser parser = new Parser(Objects.requireNonNull(input));
        Object value = parser.value(0); parser.space();
        if (parser.at != input.length() || !(value instanceof Map)) throw invalid();
        @SuppressWarnings("unchecked") Map<String,Object> fields = (Map<String,Object>) value;
        return fields;
    }
    static String required(Map<String,Object> f,String key) { String s=optional(f,key); if(s==null||s.isBlank())throw new IllegalArgumentException(key+" is required.");return s; }
    static String optional(Map<String,Object> f,String key) { Object v=f.get(key); return v==null?null:asText(v); }
    static String asText(Object v) { if (!(v instanceof String s)) throw new IllegalArgumentException("Expected a JSON string."); return s; }
    static long number(Object v) { if (!(v instanceof BigDecimal n)) throw new IllegalArgumentException("Expected an integer."); try{return n.longValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException("Expected an integer.");} }
    static void known(Map<String,Object> f,String... names) { if(!Set.of(names).containsAll(f.keySet())) throw new IllegalArgumentException("Unknown request field."); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid JSON object."); }
    private static final class Parser {
        final String text; int at;
        Parser(String text) { this.text = text; }
        void space() { while (at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
        boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        Object value(int depth) {
            if (depth > 32) throw invalid(); space(); if (at == text.length()) throw invalid();
            char c = text.charAt(at);
            if (c == '"') return string();
            if (take('{')) {
                Map<String,Object> result = new LinkedHashMap<>(); if (take('}')) return result;
                do { space(); String key = string(); if (!take(':') || result.containsKey(key)) throw invalid(); result.put(key,value(depth+1)); } while (take(','));
                if (!take('}')) throw invalid(); return result;
            }
            if (take('[')) {
                List<Object> result = new ArrayList<>(); if (take(']')) return result;
                do { result.add(value(depth+1)); } while (take(',')); if (!take(']')) throw invalid(); return result;
            }
            for (String literal : List.of("null", "true", "false")) {
                if (text.startsWith(literal, at)) { at += literal.length(); return switch(literal) { case "true" -> true; case "false" -> false; default -> null; }; }
            }
            int start = at;
            while (at < text.length() && "0123456789.eE+-".indexOf(text.charAt(at)) >= 0) at++;
            String n = text.substring(start, at);
            if (!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw invalid();
            return new BigDecimal(n);
        }
        String string() {
            if (at >= text.length() || text.charAt(at++) != '"') throw invalid();
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++); if (c == '"') return out.toString();
                if (c < 32) throw invalid();
                if (c == '\\') {
                    if (at == text.length()) throw invalid(); char e = text.charAt(at++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e);
                        case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                        case 'u' -> { if (at+4 > text.length()) throw invalid(); try { out.append((char)Integer.parseInt(text.substring(at,at+4),16)); } catch (NumberFormatException x) { throw invalid(); } at+=4; }
                        default -> throw invalid();
                    }
                } else out.append(c);
            }
            throw invalid();
        }
    }
}
