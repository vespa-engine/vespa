// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance.export;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * Select a single item from a list.
 * <p>
 * If there exists only one item in the list, select it.
 * <p>
 * If there are multiple, then user must have specified which to choose, else tell the user how.
 * <p>
 * Used by {@link IndexLocator} to select directories to resolve path to index.
 *
 * @author johsol
 */
final class Selector {

    /**
     * Result of selection or an error that was printed and we should exit.
     */
    record Selection<T>(@Nullable T value, boolean shouldExit) {
        static <T> Selection<T> chosen(T v) {
            return new Selection<>(v, false);
        }

        static <T> Selection<T> exit() {
            return new Selection<>(null, true);
        }

        T getValue() {
            return Objects.requireNonNull(value);
        }
    }

    static <T> Selection<T> selectOneOrExplain(
            List<T> items,
            @Nullable String wantedName,
            String what,
            Path container,
            Function<T, String> displayName,
            Function<T, String> displayPath) {

        if (items.isEmpty()) {
            System.out.println("Error: No " + what + " directory found in: " + container);
            return Selection.exit();
        }
        if (items.size() == 1) {
            return Selection.chosen(items.get(0));
        }

        if (wantedName != null) {
            for (T t : items) {
                if (displayName.apply(t).equals(wantedName)) {
                    return Selection.chosen(t);
                }
            }
            TablePrinter.printTable(
                    "Error: " + capitalize(what) + " '" + wantedName + "' not found under: " + container,
                    List.of(what, "path"),
                    items.stream()
                            .sorted(Comparator.comparing(a -> displayName.apply(a).toLowerCase()))
                            .map(t -> List.of(displayName.apply(t), displayPath.apply(t)))
                            .toList()
            );
            System.out.println();
            System.out.println("Use `--" + what + " <name>` to select one of the above.");
            return Selection.exit();
        } else {
            TablePrinter.printTable(
                    "Error: Multiple " + what + " directories found in: " + container,
                    List.of(what, "path"),
                    items.stream()
                            .sorted(Comparator.comparing(a -> displayName.apply(a).toLowerCase()))
                            .map(t -> List.of(displayName.apply(t), displayPath.apply(t)))
                            .toList()
            );
            System.out.println();
            System.out.println("Use `--" + what + " <name>` to select one of the above.");
            return Selection.exit();
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Selector() {
    }
}
