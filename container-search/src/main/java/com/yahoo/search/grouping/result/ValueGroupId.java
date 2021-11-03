// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This abstract class is used in {@link Group} instances where the identifying expression evaluated to a singe value.
 *
 * @author Simon Thoresen Hult
 */
public abstract class ValueGroupId<T> extends GroupId {

    private final T value;

    /**
     * Constructs a new instance of this class.
     *
     * @param type  the type of this id's value
     * @param value the identifying value
     */
    public ValueGroupId(String type, T value) {
        this(type, value, String.valueOf(value.toString()));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param type       the type of this id's value
     * @param value      the identifying value
     * @param valueImage the String representation of the <code>value</code> argument
     */
    public ValueGroupId(String type, T value, String valueImage) {
        super(type, valueImage);
        this.value = value;
    }

    /** Returns the identifying value. */
    public T getValue() {
        return value;
    }

}
