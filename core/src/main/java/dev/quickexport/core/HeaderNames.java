package dev.quickexport.core;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Produces a header row in which every name is non-blank and unique. */
final class HeaderNames {

    private HeaderNames() {
    }

    /**
     * Blank names become {@code COLUMN_<n>}; later duplicates (case-insensitive) get a
     * {@code _2}, {@code _3}, ... suffix that does not collide with any other column.
     */
    static String[] unique(ExportModel model) {
        int n = model.getColumnCount();
        String[] raw = new String[n];
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String name = model.getColumnName(i);
            raw[i] = (name == null || name.isBlank()) ? "COLUMN_" + (i + 1) : name;
            taken.add(raw[i].toLowerCase(Locale.ROOT));
        }
        String[] result = new String[n];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String candidate = raw[i];
            if (seen.contains(candidate.toLowerCase(Locale.ROOT))) {
                int suffix = 2;
                do {
                    candidate = raw[i] + "_" + suffix++;
                } while (seen.contains(candidate.toLowerCase(Locale.ROOT))
                        || taken.contains(candidate.toLowerCase(Locale.ROOT)));
            }
            seen.add(candidate.toLowerCase(Locale.ROOT));
            result[i] = candidate;
        }
        return result;
    }
}
