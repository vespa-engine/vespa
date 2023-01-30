package com.yahoo.config.provision;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * An integer range.
 *
 * @author bratseth
 */
public class IntRange {

    private static final IntRange empty = new IntRange(OptionalInt.empty(), OptionalInt.empty());

    private final OptionalInt from, to;

    public IntRange(OptionalInt from, OptionalInt to) {
        if (from.isPresent() && to.isPresent() && from.getAsInt() > to.getAsInt())
            throw new IllegalArgumentException("from " + from.getAsInt() + " is greater than to " + to.getAsInt());
        this.from = from;
        this.to = to;
    }

    /** Returns the minimum value which is in this range, or empty if it is open downwards. */
    public OptionalInt from() { return from; }

    /** Returns the maximum value which is in this range, or empty if it is open upwards. */
    public OptionalInt to() { return to; }

    /** Returns true if both from and to is open (not present). */
    public boolean isEmpty() {
        return from.isEmpty() && to.isEmpty();
    }

    /** Returns whether the given value is in this range. */
    public boolean includes(int value) {
        if (from.isPresent() && value < from.getAsInt()) return false;
        if (to.isPresent() && value > to.getAsInt()) return false;
        return true;
    }

    /** Returns the given value adjusted minimally to fit within this range. */
    public int fit(int value) {
        if (from.isPresent() && value < from.getAsInt()) return from.getAsInt();
        if (to.isPresent() && value > to.getAsInt()) return to.getAsInt();
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof IntRange other)) return false;
        if ( ! this.from.equals(other.from)) return false;
        if ( ! this.to.equals(other.to)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "[]";
        if (from.equals(to)) return String.valueOf(from.getAsInt());
        return "[" + (from.isPresent() ? from.getAsInt() : "") + ", " + (to.isPresent() ? to.getAsInt() : "") + "]";
    }

    public static IntRange empty() { return empty; }

    public static IntRange from(int from) {
        return new IntRange(OptionalInt.of(from), OptionalInt.empty());
    }

    public static IntRange to(int to) {
        return new IntRange(OptionalInt.empty(), OptionalInt.of(to));
    }

    public static IntRange of(int fromTo) {
        return new IntRange(OptionalInt.of(fromTo), OptionalInt.of(fromTo));
    }

    public static IntRange of(int from, int to) {
        return new IntRange(OptionalInt.of(from), OptionalInt.of(to));
    }

    /** Returns this with a 'from' limit which is at most the given value */
    public IntRange fromAtMost(int minLimit) {
        if (from.isEmpty()) return this;
        if (from.getAsInt() <= minLimit) return this;
        return new IntRange(OptionalInt.of(minLimit), to);
    }

    /** Returns this with a 'to' limit which is at least the given value */
    public IntRange toAtLeast(int maxLimit) {
        if (to.isEmpty()) return this;
        if (to.getAsInt() >= maxLimit) return this;
        return new IntRange(from, OptionalInt.of(maxLimit));
    }

    /** Parses a value ("value"), value range ("[min-value?, max-value?]"), or empty. */
    public static IntRange from(String s) {
        try {
            s = s.trim();
            if (s.startsWith("[") && s.endsWith("]")) {
                String innards = s.substring(1, s.length() - 1).trim();
                if (innards.isEmpty()) return empty();
                String[] numbers = (" " + innards + " ").split(","); // pad to make sure we get 2 elements
                if (numbers.length != 2) throw new IllegalArgumentException("Expected two numbers");
                return new IntRange(parseOptionalInt(numbers[0]), parseOptionalInt(numbers[1]));
            } else {
                var fromTo = parseOptionalInt(s);
                return new IntRange(fromTo, fromTo);
            }
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expected a number or range on the form [min, max], but got '" + s + "'", e);
        }
    }

    private static OptionalInt parseOptionalInt(String s) {
        try {
            s = s.trim();
            if (s.isEmpty()) return OptionalInt.empty();
            return OptionalInt.of(Integer.parseInt(s));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + s + "' is not an integer");
        }
    }

}
