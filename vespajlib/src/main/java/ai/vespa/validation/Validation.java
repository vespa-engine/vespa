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
    public static <T> T parse(String value, Function<String, T> parser, String description) {
        try {
            return parser.apply(requireNonNull(value, description + " cannot be null"));
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("failed parsing " + description +
                                               " '" + value + "': " + Exceptions.toMessageString(e));
        }
    }

    /** Requires arguments to match the given pattern. */
    public static String requireMatch(String value, String description, Pattern pattern) {
        return require(pattern.matcher(value).matches(), value, description, "must match '" + pattern + "'");
    }

    /** Requires arguments to be non-blank. */
    public static String requireNonBlank(String value, String description) {
        return require( ! value.isBlank(), value, description, "cannot be blank");
    }

    /** Requires arguments to be at least the lower bound. */
    public static <T extends Comparable<? super T>> T requireAtLeast(T value, String description, T lower) {
        return require(lower.compareTo(value) <= 0, value, description, "must be at least '" + lower + "'");
    }

    /** Requires arguments to be at most the upper bound. */
    public static <T extends Comparable<? super T>> T requireAtMost(T value, String description, T upper) {
        return require(upper.compareTo(value) >= 0, value, description, "must be at most '" + upper + "'");
    }

    /** Requires arguments to be at least the lower bound, and at most the upper bound. */
    public static <T extends Comparable<? super T>> T requireInRange(T value, String description, T lower, T upper) {
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower bound cannot be greater than upper bound, " +
                                                                           "but got '" + lower + "' > '" + upper + "'");
        return require(lower.compareTo(value) <= 0 && upper.compareTo(value) >= 0, value, description,
                       "must be at least '" + lower + "' and at most '" + upper + "'");
    }

    /** Wraps a predicate with a message describing it. */
    public static <T> T require(boolean condition, T value, String description, String message) {
        if (condition) return value;
        throw new IllegalArgumentException(description + " " + message + ", but got: '" + value + "'");
    }

}