// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This abstract class is used in {@link Group} instances where the identifying expression evaluated to a {@link
 * com.yahoo.search.grouping.request.BucketValue}. The range is inclusive-from and exclusive-to.
 *
 * @author Simon Thoresen Hult
 */
public abstract class BucketGroupId<T> extends GroupId {

    private final T from;
    private final T to;

    /**
     * Constructs a new instance of this class.
     *
     * @param type The type of this id's value.
     * @param from The inclusive-from of the range.
     * @param to   The exclusive-to of the range.
     */
    public BucketGroupId(String type, T from, T to) {
        this(type, from, String.valueOf(from), to, String.valueOf(to));
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param type      The type of this id's value.
     * @param from      The inclusive-from of the range.
     * @param fromImage The String representation of the <code>from</code> argument.
     * @param to        The exclusive-to of the range.
     * @param toImage   The String representation of the <code>to</code> argument.
     */
    public BucketGroupId(String type, T from, String fromImage, T to, String toImage) {
        super(type, fromImage, toImage);
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the inclusive-from of the value range.
     *
     * @return The from-value.
     */
    public T getFrom() {
        return from;
    }

    /**
     * Returns the exclusive-to of the value range.
     *
     * @return The to-value.
     */
    public T getTo() {
        return to;
    }

}
