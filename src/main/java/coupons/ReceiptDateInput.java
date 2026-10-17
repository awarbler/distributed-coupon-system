package coupons;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Console date interpretations; two-digit years mean 2000 through 2099. */
final class ReceiptDateInput {
    private ReceiptDateInput() { }

    /**
     * Uses month/day/year and year/month/day numeric conventions, not silent day/month swaps.
     * Month-first input takes precedence; year-first input starts with four digits or a value above 12.
     * Calendar validity is checked strictly, including leap days.
     */
    static List<LocalDate> candidates(String input) {
        String value = input.trim();
        List<LocalDate> result = new ArrayList<>();
        String[] parts = value.split("[/.-]", -1);
        if (parts.length == 3 && java.util.Arrays.stream(parts).allMatch(p -> p.matches("[0-9]{1,4}"))) {
            int first = Integer.parseInt(parts[0]);
            int middle = Integer.parseInt(parts[1]);
            int last = Integer.parseInt(parts[2]);
            if (first >= 1 && first <= 12 && (parts[2].length() == 4 || parts[2].length() == 2)) {
                add(result, parts[2].length() == 2 ? 2000 + last : last, first, middle);
            }
            if (parts[0].length() == 4 || parts[0].length() == 2 && first > 12) {
                add(result, parts[0].length() == 2 ? 2000 + first : first, middle, last);
            }
        }
        for (String pattern : List.of("MMMM d, uuuu", "MMM d, uuuu", "MMMM d uuuu", "MMM d uuuu", "d MMMM uuuu", "d MMM uuuu")) {
            var format = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                    .toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
            try {
                LocalDate date = LocalDate.parse(value, format);
                if (!result.contains(date)) result.add(date);
            } catch (DateTimeException ignored) { }
        }
        return List.copyOf(result);
    }

    private static void add(List<LocalDate> dates, int year, int month, int day) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            if (!dates.contains(date)) dates.add(date);
        } catch (DateTimeException ignored) { }
    }
}
