package com.yahoo.collections;

import com.google.common.collect.Comparators;

import java.util.Optional;
import java.util.function.BinaryOperator;

/**
 * @author jonmv
 */
public class Optionals {

    private Optionals() { }

    /** Returns the first non-empty optional, or empty if all are empty. */
    @SafeVarargs
    public static <T> Optional<T> firstNonEmpty(Optional<T>... optionals) {
        for (Optional<T> optional : optionals)
            if (optional.isPresent())
                return optional;
        return Optional.empty();
    }

    /** Returns the non-empty optional with the lowest value, or empty if all are empty. */
    @SafeVarargs
    public static <T extends Comparable<T>> Optional<T> min(Optional<T>... optionals) {
        Optional<T> best = Optional.empty();
        for (Optional<T> optional : optionals)
            if (best.isEmpty() || optional.isPresent() && optional.get().compareTo(best.get()) < 0)
                best = optional;
        return best;
    }

    /** Returns the non-empty optional with the highest value, or empty if all are empty. */
    @SafeVarargs
    public static <T extends Comparable<T>> Optional<T> max(Optional<T>... optionals) {
        Optional<T> best = Optional.empty();
        for (Optional<T> optional : optionals)
            if (best.isEmpty() || optional.isPresent() && optional.get().compareTo(best.get()) > 0)
                best = optional;
        return best;
    }

    /** Returns whether either optional is empty, or both are present and equal. */
    public static <T> boolean equalIfBothPresent(Optional<T> first, Optional<T> second) {
        return first.isEmpty() || second.isEmpty() || first.equals(second);
    }

    /** Returns whether the optional is empty, or present and equal to the given value. */
    public static <T> boolean emptyOrEqual(Optional<T> optional, T value) {
        return optional.isEmpty() || optional.equals(Optional.of(value));
    }

}
