// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public abstract class LazySet<E> implements Set<E> {

    private Set<E> delegate = newEmpty();

    @Override
    public final int size() {
        return delegate.size();
    }

    @Override
    public final boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public final boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public final Iterator<E> iterator() {
        return delegate.iterator();
    }

    @Override
    public final Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public final <T> T[] toArray(T[] a) {
        // noinspection SuspiciousToArrayCall
        return delegate.toArray(a);
    }

    @Override
    public final boolean add(E e) {
        return delegate.add(e);
    }

    @Override
    public final boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public final boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public final boolean addAll(Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public final boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public final boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public final void clear() {
        delegate.clear();
    }

    @Override
    public final int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return obj == this || (obj instanceof Set && delegate.equals(obj));
    }

    private Set<E> newEmpty() {
        return new EmptySet();
    }

    private Set<E> newSingleton(E e) {
        return new SingletonSet(e);
    }

    protected abstract Set<E> newDelegate();

    final Set<E> getDelegate() {
        return delegate;
    }

    class EmptySet extends AbstractSet<E> {

        @Override
        public Iterator<E> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean add(E e) {
            delegate = newSingleton(e);
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            switch (c.size()) {
            case 0:
                return false;
            case 1:
                add(c.iterator().next());
                return true;
            default:
                delegate = newDelegate();
                delegate.addAll(c);
                return true;
            }
        }
    }

    class SingletonSet extends AbstractSet<E> {

        final E element;

        SingletonSet(E e) {
            this.element = e;
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {

                boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public E next() {
                    if (hasNext) {
                        hasNext = false;
                        return element;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove() {
                    if (hasNext) {
                        throw new IllegalStateException();
                    } else {
                        delegate = newEmpty();
                    }
                }
            };
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean add(E e) {
            if (contains(e)) {
                return false;
            } else {
                delegate = newDelegate();
                delegate.add(element);
                delegate.add(e);
                return true;
            }
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            switch (c.size()) {
            case 0:
                return false;
            case 1:
                return add(c.iterator().next());
            default:
                delegate = newDelegate();
                delegate.add(element);
                delegate.addAll(c);
                return true;
            }
        }
    }

    public static <E> LazySet<E> newHashSet() {
        return new LazySet<E>() {

            @Override
            protected Set<E> newDelegate() {
                return new HashSet<>();
            }
        };
    }
}
