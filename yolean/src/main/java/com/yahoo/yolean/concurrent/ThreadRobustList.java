// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <p>This class implements a thread-safe, lock-free list of Objects that supports multiple readers and a single writer.
 * Because there are no locks or other memory barriers involved, there exists no <i>happens-before</i> relationship
 * among calls to either methods of the <code>ThreadRobustList</code>. This means that there are no guarantees as to when
 * (or even if) an item {@link #add(Object)}ed becomes visible through {@link #iterator()}. If visibility is required,
 * either use explicit synchronization between reader and writer thread, or move to a different concurrent collection
 * (e.g. <code>CopyOnWriteArrayList</code>).</p>
 * <p>Because it is lock-free, the <code>ThreadRobustList</code> has minimal overhead to both reading and writing. The
 * iterator offered by this class always observes the list in a consistent state, and it never throws a
 * <code>ConcurrentModificationException</code>.</p>
 * <p>The <code>ThreadRobustList</code> does not permit adding <code>null</code> items.</p>
 * <p>The usage of <code>ThreadRobustList</code> has no memory consistency effects. </p>
 *
 * @author Steinar Knutsen
 * @author bratseth
 * @since 5.1.15
 */
public class ThreadRobustList<T> implements Iterable<T> {

    private Object[] items;
    private int next = 0;

    /**
     * <p>Constructs a new instance of this class with an initial capacity of <code>10</code>.</p>
     */
    public ThreadRobustList() {
        this(10);
    }

    /**
     * <p>Constructs a new instance of this class with a given initial capacity.</p>
     *
     * @param initialCapacity the initial capacity of this list
     */
    public ThreadRobustList(int initialCapacity) {
        items = new Object[initialCapacity];
    }

    /**
     * <p>Returns whether or not this list is empty.</p>
     *
     * @return <code>true</code> if this list has zero items
     */
    public boolean isEmpty() {
        return next == 0;
    }

    /**
     * <p>Adds an item to this list. As opposed to <code>CopyOnWriteArrayList</code>, items added to this list may become
     * visible to iterators created <em>before</em> a call to this method.</p>
     *
     * @param item the item to add
     * @throws NullPointerException if <code>item</code> is <code>null</code>
     */
    public void add(T item) {
        if (item == null) {
            throw new NullPointerException();
        }
        Object[] workItems = items;
        if (next >= items.length) {
            workItems = Arrays.copyOf(workItems, 20 + items.length * 2);
            workItems[next++] = item;
            items = workItems;
        } else {
            workItems[next++] = item;
        }
    }

    /**
     * <p>Returns an iterator over the items in this list. As opposed to <code>CopyOnWriteArrayList</code>, this iterator
     * may see items added to the <code>ThreadRobustList</code> even if they occur <em>after</em> a call to this method.</p>
     * <p>The returned iterator does not support <code>remove()</code>.</p>
     *
     * @return an iterator over this list
     */
    @Override
    public Iterator<T> iterator() {
        return new ThreadRobustIterator<>(items);
    }

    private static class ThreadRobustIterator<T> implements Iterator<T> {

        final Object[] items;
        int nextIndex = 0;

        ThreadRobustIterator(Object[] items) {
            this.items = items;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return (T)items[nextIndex++];
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
}
