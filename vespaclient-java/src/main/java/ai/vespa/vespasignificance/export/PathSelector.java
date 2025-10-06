// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.export;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Pick exactly one path or describe why not.
 *
 * @author johsol
 */
final class PathSelector {

    enum Outcome {

        /**
         * Either preferredName was in the items or it was null and rest of path unambiguous
         */
        CHOSEN,

        /**
         * The preferredName was not in the items
         */
        NOT_FOUND,

        /**
         * PreferredName was null and more than one path
         */
        AMBIGUOUS

    }

    /**
     * Row describing a candidate for display/help.
     */
    record Row(String name, String path) {
    }

    /**
     * Result of a selection attempt.
     */
    record Result<T>(
            Outcome outcome,
            @Nullable T value,
            String message,
            List<Row> options
    ) {
        static <T> Result<T> chosen(T value) {
            return new Result<>(Outcome.CHOSEN, Objects.requireNonNull(value), "", List.of());
        }

        static <T> Result<T> notFound(String msg, List<Row> options) {
            return new Result<>(Outcome.NOT_FOUND, null, msg, options);
        }

        static <T> Result<T> ambiguous(String msg, List<Row> options) {
            return new Result<>(Outcome.AMBIGUOUS, null, msg, options);
        }
    }

    /**
     * Selects one path or returns options.
     */
    static <T> Result<T> selectOne(
            List<T> items,
            @Nullable String preferredName,
            String kind,
            Path container,
            Function<T, String> displayName,
            Function<T, String> displayPath,
            BiPredicate<String, String> nameMatcher
    ) {
        if (items.isEmpty()) {
            return Result.notFound("No " + kind + " directory found in: " + container, List.of());
        }
        if (items.size() == 1 && preferredName == null) {
            return Result.chosen(items.get(0));
        }

        // List candidates and sort by name
        final var candidates = new ArrayList<Map.Entry<T, Row>>(items.size());
        for (T t : items) {
            var name = displayName.apply(t);
            var path = displayPath.apply(t);
            candidates.add(Map.entry(t, new Row(name, path)));
        }
        candidates.sort(Comparator.comparing(e -> e.getValue().name().toLowerCase(Locale.ROOT)));

        if (preferredName != null) {
            for (var e : candidates) {
                if (nameMatcher.test(e.getValue().name(), preferredName)) {
                    return Result.chosen(e.getKey());
                }
            }
            var msg = capitalize(kind) + " '" + preferredName + "' not found under: " + container
                    + System.lineSeparator()
                    + "Use `--" + kind + " <name>` to select one of the options below.";
            return Result.notFound(msg, rows(candidates));
        }

        var msg = "Multiple " + kind + " directories found in: " + container
                + System.lineSeparator()
                + "Use `--" + kind + " <name>` to select one of the options below.";
        return Result.ambiguous(msg, rows(candidates));
    }

    /**
     * String::equals matcher.
     */
    static <T> Result<T> selectOne(
            List<T> items,
            @Nullable String preferredName,
            String kind,
            Path container,
            Function<T, String> displayName,
            Function<T, String> displayPath
    ) {
        return selectOne(items, preferredName, kind, container, displayName, displayPath, String::equals);
    }

    private static <T> List<Row> rows(List<Map.Entry<T, Row>> entries) {
        return entries.stream().map(Map.Entry::getValue).toList();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s :
                s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

}
