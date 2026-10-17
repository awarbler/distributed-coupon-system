package coupons;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Normalizes console time for display while the original receipt text stays recorded. */
final class ReceiptTimeInput {
    private static final Pattern TWELVE = Pattern.compile("(\\d{1,2})(?::?(\\d{2}))?\\s*([AP])\\.?M\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWENTY_FOUR = Pattern.compile("(\\d{1,2}):?(\\d{2})");
    private ReceiptTimeInput() { }

    static Optional<LocalTime> parse(String raw) {
        if (raw == null) return Optional.empty();
        String text = raw.trim();
        var twelve = TWELVE.matcher(text);
        try {
            if (twelve.matches()) {
                int hour = Integer.parseInt(twelve.group(1));
                if (hour < 1 || hour > 12) return Optional.empty();
                int minute = twelve.group(2) == null ? 0 : Integer.parseInt(twelve.group(2));
                return Optional.of(LocalTime.of(hour % 12 + (twelve.group(3).equalsIgnoreCase("P") ? 12 : 0), minute));
            }
            var full = TWENTY_FOUR.matcher(text);
            if (full.matches()) return Optional.of(LocalTime.of(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2))));
        } catch (DateTimeException | NumberFormatException ignored) { }
        return Optional.empty();
    }

    static String display(String raw) {
        return parse(raw).map(time -> time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)))
                .orElse(raw == null ? "unknown" : raw);
    }
}
