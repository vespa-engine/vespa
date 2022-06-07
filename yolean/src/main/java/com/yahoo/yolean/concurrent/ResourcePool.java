// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * <p>This implements a simple stack based resource pool. If you are out of resources new are allocated from the
 * factory.</p>
 *
 * @author baldersheim
 * @since 5.2
 */
public final class ResourcePool<T> implements Iterable<T> {

    private final Deque<T> pool = new ArrayDeque<>();
    private final Supplier<T> factory;


    public ResourcePool(Supplier<T> factory) {
        this.factory = factory;
    }

    public T alloc() {
        return pool.isEmpty() ? factory.get() : pool.pop();
    }

    public void free(T e) {
        pool.push(e);
    }

    @Override
    public Iterator<T> iterator() {
        return pool.iterator();
    }
}
