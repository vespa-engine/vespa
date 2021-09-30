// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A Set implementation which only considers object identity. It should only be
 * used for small number of objects, as it is implemented as scanning an
 * ArrayList for identity matches. In other words: Performance will only be
 * acceptable for <i>small</i> sets.
 *
 * <p>
 * The rationale for this class is the high cost of the object identifier used
 * in IdentityHashMap, where the key set is often used as an identity set.
 * </p>
 *
 * @author Steinar Knutsen
 * @since 5.1.4
 * @see java.util.IdentityHashMap
 *
 * @param <E>
 *            the type contained in the Set
 */
public final class TinyIdentitySet<E> implements Set<E> {
    private class ArrayIterator<T> implements Iterator<E> {
        private int i = -1;
        private boolean removed = false;

        @Override
        public boolean hasNext() {
            return i + 1 < size;
        }

        @SuppressWarnings("unchecked")
        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements available");
            }
            removed = false;
            return (E) entries[++i];
        }

        @Override
        public void remove() {
            if (removed) {
                throw new IllegalStateException(
                        "Trying to remove same element twice.");
            }
            if (i == -1) {
                throw new IllegalStateException(
                        "Trying to remove before entering iterator.");
            }
            delete(i--);
            removed = true;
        }

    }

    private Object[] entries;
    private int size = 0;

    /**
     * Create a set with an initial capacity of initSize. The internal array
     * will grow automatically with a linear growth rate if more elements than
     * initSize are added.
     *
     * @param initSize
     *            initial size of internal element array
     */
    public TinyIdentitySet(final int initSize) {
        entries = new Object[initSize];
    }

    /**
     * Expose the index in the internal array of a given object. -1 is returned
     * if the object is not present in the internal array.
     *
     * @param e
     *            an object to check whether exists in this set
     * @return the index of the argument e in the internal array, or -1 if the
     *         object is not present
     */
    public int indexOf(final Object e) {
        for (int i = 0; i < size; ++i) {
            if (e == entries[i]) {
                return i;
            }
        }
        return -1;
    }

    private void clean() {
        int offset = 0;
        for (int i = 0; i < size; ++i) {
            if (entries[i] == null) {
                ++offset;
            } else {
                entries[i - offset] = entries[i];
            }
        }
        size -= offset;
    }

    private void grow() {
        // linear growth, as we should always be working on small sets
        entries = Arrays.copyOf(entries, entries.length + 10);
    }

    private void append(final Object arg) {
        if (size == entries.length) {
            grow();
        }
        entries[size++] = arg;
    }

    @Override
    public boolean add(final E arg) {
        final int i = indexOf(arg);
        if (i >= 0) {
            return false;
        }
        append(arg);
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> arg) {
        boolean changed = false;
        for (final E entry : arg) {
            changed |= add(entry);
        }
        return changed;
    }

    @Override
    public void clear() {
        size = 0;
    }

    @Override
    public boolean contains(final Object arg) {
        return indexOf(arg) >= 0;
    }

    /**
     * This is an extremely expensive implementation of
     * {@link Set#containsAll(Collection)}. It is implemented as O(n**2).
     */
    @Override
    public boolean containsAll(final Collection<?> arg) {
        for (final Object entry : arg) {
            if (indexOf(entry) < 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Iterator<E> iterator() {
        return new ArrayIterator<E>();
    }

    private void delete(int i) {
        if (i < 0 || i >= size) {
            return;
        }
        --size;
        while (i < size) {
            entries[i] = entries[i + 1];
            ++i;
        }
        entries[i] = null;
    }

    @Override
    public boolean remove(final Object arg) {
        final int i = indexOf(arg);
        if (i < 0) {
            return false;
        }
        delete(i);
        return true;
    }

    /**
     * This is an extremely expensive implementation of
     * {@link Set#removeAll(Collection)}. It is implemented as O(n**2).
     */
    @Override
    public boolean removeAll(final Collection<?> arg) {
        boolean changed = false;
        for (final Object entry : arg) {
            final int i = indexOf(entry);
            if (i >= 0) {
                entries[i] = null;
                changed = true;
            }
        }
        if (changed) {
            clean();
        }
        return changed;
    }

    /**
     * This is an extremely expensive implementation of
     * {@link Set#retainAll(Collection)}. It is implemented as O(n**2).
     */
    @Override
    public boolean retainAll(final Collection<?> arg) {
        boolean changed = false;
        for (int i = 0; i < size; ++i) {
            final Object entry = entries[i];
            boolean exists = false;
            // cannot use Collection.contains(), as we want identity
            for (final Object v : arg) {
                if (v == entry) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                entries[i] = null;
                changed = true;
            }
        }
        if (changed) {
            clean();
        }
        return changed;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(entries, size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(final T[] arg) {
        return Arrays.copyOf(entries, size, (Class<T[]>) arg.getClass());
    }

}
