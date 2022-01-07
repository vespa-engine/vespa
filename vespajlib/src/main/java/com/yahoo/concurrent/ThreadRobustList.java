// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A list which tolerates concurrent adds from one other thread while it is
 * read. More precisely: <i>This list is guaranteed to provide a self-consistent
 * read view regardless of the internal order in which the primitive mutating
 * operations on it are observed from the reading thread.</i>
 * <p>
 * This is useful for traced information as there may be timed out threads
 * working on the structure after it is returned upwards for consumption.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
public class ThreadRobustList<T> implements Iterable<T> {

    private Object[] items;

    /** Index of the next item */
    private int next = 0;

    public ThreadRobustList() {
        this(10);
    }

    public ThreadRobustList(final int initialCapacity) {
        items = new Object[initialCapacity];
    }

    public void add(final T item) {
        Object[] workItems = items;
        if (next >= items.length) {
            final int newLength = 20 + items.length * 2;
            workItems = Arrays.copyOf(workItems, newLength);
            workItems[next++] = item;
            items = workItems;
        } else {
            workItems[next++] = item;
        }
    }

    /**
     * Returns an iterator over the elements of this. This iterator does not
     * support remove.
     */
    @Override
    public Iterator<T> iterator() {
        return new ThreadRobustIterator(items);
    }

    /**
     * Returns an iterator over the elements of this, starting at the last
     * element and working backwards. This iterator does not support remove.
     */
    public Iterator<T> reverseIterator() {
        return new ThreadRobustReverseIterator(items);
    }

    public boolean isEmpty() {
        return next == 0;
    }

    private class ThreadRobustIterator implements Iterator<T> {

        private final Object[] items;

        private int nextIndex = 0;

        public ThreadRobustIterator(final Object[] items) {
            this.items = items;
        }

        public @Override
        void remove() {
            throw new UnsupportedOperationException(
                    "remove() is not supported on thread robust list iterators");
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements");
            }

            return (T) items[nextIndex++];
        }

        @Override
        public boolean hasNext() {
            if (nextIndex >= items.length) {
                return false;
            }
            if (items[nextIndex] == null) {
                return false;
            }
            return true;
        }

    }

    private class ThreadRobustReverseIterator implements Iterator<T> {

        private final Object[] items;

        private int nextIndex;

        public ThreadRobustReverseIterator(final Object[] items) {
            this.items = items;
            nextIndex = findLastAssignedIndex(items);
        }

        private int findLastAssignedIndex(final Object[] items) {
            for (int i = items.length - 1; i >= 0; i--) {
                if (items[i] != null) {
                    return i;
                }
            }
            return -1;
        }

        public @Override
        void remove() {
            throw new UnsupportedOperationException(
                    "remove() is not supported on thread robust list iterators");
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements");
            }

            return (T) items[nextIndex--];
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

    }

}
