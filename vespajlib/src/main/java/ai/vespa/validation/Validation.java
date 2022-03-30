// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import com.yahoo.yolean.Exceptions;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Every {@link String} is a security risk!
 * This class has utility methods for validating strings, which are often user input.
 *
 * @author jonmv
 */
public class Validation {

    private Validation() { }

    /** Parses then given string, and then validates that each of the requirements are true for the parsed value. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> T validate(String value, Function<String, T> parser, String description, Predicate<? super T>... requirements) {
        T parsed;
        try {
            parsed = parser.apply(requireNonNull(value, description + " cannot be null"));
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("failed parsing " + description +
                                               " '" + value + "': " + Exceptions.toMessageString(e));
        }
        return validate(parsed, description, requirements);
    }

    /** Validates that each of the requirements are true for the given argument. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> T validate(T value, String description, Predicate<? super T>... requirements) {
        for (Predicate<? super T> requirement : requirements)
            if ( ! requirement.test(value))
                throw new IllegalArgumentException(description + " " + requirement + ", but got: '" + value + "'");

        return value;
    }

    /** Requires arguments to match the given pattern. */
    public static Predicate<String> requireMatch(Pattern pattern) {
        return require(s -> pattern.matcher(s).matches(), "must match '" + pattern + "'");
    }

    /** Requires arguments to be non-blank. */
    public static Predicate<String> requireNonBlank() {
        return require(s -> ! s.isBlank(), "cannot be blank");
    }

    /** Requires arguments to be at least the lower bound. */
    public static <T extends Comparable<? super T>> Predicate<T> requireAtLeast(T lower) {
        requireNonNull(lower, "lower bound cannot be null");
        return require(c -> lower.compareTo(c) <= 0, "must be at least '" + lower + "'");
    }

    /** Requires arguments to be at most the upper bound. */
    public static <T extends Comparable<? super T>> Predicate<T> requireAtMost(T upper) {
        requireNonNull(upper, "upper bound cannot be null");
        return require(c -> upper.compareTo(c) >= 0, "must be at most '" + upper + "'");
    }

    /** Requires arguments to be at least the lower bound, and at most the upper bound. */
    public static <T extends Comparable<? super T>> Predicate<T> requireInRange(T lower, T upper) {
        requireNonNull(lower, "lower bound cannot be null");
        requireNonNull(upper, "upper bound cannot be null");
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower bound cannot be greater than upper bound, " +
                                                                           "but got '" + lower + "' > '" + upper + "'");
        return require(c -> lower.compareTo(c) <= 0 && upper.compareTo(c) >= 0,
                       "must be at least '" + lower + "' and at most '" + upper + "'");
    }

    /** Wraps a predicate with a message describing it. */
    public static <T> Predicate<T> require(Predicate<T> predicate, String message) {
        return new Predicate<T>() {
            @Override public boolean test(T t) { return predicate.test(t); }
            @Override public String toString() { return message; }
        };
    }

}