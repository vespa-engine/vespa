// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.serialization.SpanNodeReader;

import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * This class represents a range of characters from a string.&nbsp;This is the leaf node
 * in a Span tree. Its boundaries are defined as inclusive-from and exclusive-to.
 *
 * @author baldersheim
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Span extends SpanNode {
    public static final byte ID = 1;
    private int from;
    private int length;

    /**
     * This will construct a valid span or throw {@link IllegalArgumentException}
     * if the span is invalid.
     *
     * @param from   Start of the span. Must be &gt;= 0.
     * @param length of the span. Must be &gt;= 0.
     * @throws IllegalArgumentException if illegal span
     */
    public Span(int from, int length) {
        setFrom(from);
        setLength(length);
    }

    /**
     * Creates an empty Span, used mainly for deserialization.
     *
     * @param reader the reader that must populate this Span instance
     */
    public Span(SpanNodeReader reader) {
        reader.read(this);
    }

    /**
     * WARNING!&nbsp;Only to be used by deserializers!&nbsp;Creates an empty Span instance.
     */
    public Span() {
    }

    /**
     * Copies the given Span into a new Span instance.
     *
     * @param spanToCopy the Span to copy.
     */
    public Span(Span spanToCopy) {
        this(spanToCopy.getFrom(), spanToCopy.getLength());
    }

    @Override
    public final int getFrom() {
        return from;
    }

    /**
     * NOTE: DO NOT USE. Should only be used by {@link com.yahoo.document.serialization.SpanNodeReader}.
     * @param from the from value to set
     */
    public void setFrom(int from) {
        if (from < 0) {
            throw new IllegalArgumentException("From cannot be < 0. (Was " + from + ").");
        }
        this.from = from;
    }

    @Override
    public final int getTo() {
        return from + length;
    }

    @Override
    public final int getLength() {
        return length;
    }

    /**
     * NOTE: DO NOT USE. Should only be used by {@link com.yahoo.document.serialization.SpanNodeReader}.
     * @param length the length value to set
     */
    public void setLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be < 0. (Was " + length + ").");
        }
        this.length = length;
    }

    public String toString() {
        return new StringBuilder("span [").append(from).append(',').append(getTo()).append('>').toString();
    }

    @Override
    public final CharSequence getText(CharSequence text) {
        return text.subSequence(from, getTo());
    }

    /**
     * Always returns true.
     *
     * @return always true.
     */
    @Override
    public boolean isLeafNode() {
        return true;
    }

    /**
     * Returns a ListIterator that iterates over absolutely nothing.
     *
     * @return a ListIterator that iterates over absolutely nothing.
     */
    @Override
    public ListIterator<SpanNode> childIterator() {
        return new EmptyIterator();
    }

    /**
     * Returns a ListIterator that iterates over absolutely nothing.
     *
     * @return a ListIterator that iterates over absolutely nothing.
     */
    @Override
    public ListIterator<SpanNode> childIteratorRecursive() {
        return childIterator();
    }

    private class EmptyIterator implements ListIterator<SpanNode> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public SpanNode next() {
            throw new NoSuchElementException("A Span has no children");
        }

        @Override
        public boolean hasPrevious() {
            return false;
        }

        @Override
        public SpanNode previous() {
            throw new NoSuchElementException("A Span has no children");
        }

        @Override
        public int nextIndex() {
            return 0;
        }

        @Override
        public int previousIndex() {
            return 0;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("A Span has no children");
        }

        @Override
        public void set(SpanNode spanNode) {
            throw new UnsupportedOperationException("A Span has no children");
        }

        @Override
        public void add(SpanNode spanNode) {
            throw new UnsupportedOperationException("A Span has no children");
        }
    }
}
