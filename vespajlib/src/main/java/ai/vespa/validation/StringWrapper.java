// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.validation;

import static java.util.Objects.requireNonNull;

/**
 * Abstract wrapper for glorified strings, to ease adding new such wrappers.
 * <p>
 * <br>
 * What's in a name?<br>
 * That which we call a String<br>
 * by any other name would smell as foul.<br>
 * No? 'Tis not sooth?<br>
 * No ... I see it now!<br>
 * Baptiz'd a-new, the String—<br>
 * no more a String,<br>
 * no less a {@link #value}—<br>
 * it bringeth counsel<br>
 * and is proof against their enmity.<br>
 * </p>
 * @param <T> child type
 *
 * @author jonmv
 */
public abstract class StringWrapper<T extends StringWrapper<T>> implements Comparable<T> {

    private final String value;

    protected StringWrapper(String value) {
        this.value = requireNonNull(value);
    }

    public final String value() { return value; }

    @Override
    public int compareTo(T other) {
        return value().compareTo(other.value());
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof StringWrapper<?>)) return false;
        return value.equals(((StringWrapper<?>) o).value);
    }

    @Override
    public final int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

}
