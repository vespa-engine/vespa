// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * <p>This implements a simple stack based resource pool. If you are out of resources new are allocated from the
 * factory.</p>
 *
 * @author baldersheim
 * @since 5.2
 */
public final class ResourcePool<T> implements Iterable<T> {

    private final Deque<T> pool = new ArrayDeque<>();
    private final ResourceFactory<T> factory;

    public ResourcePool(ResourceFactory<T> factory) {
        this.factory = factory;
    }

    public final T alloc() {
        return pool.isEmpty() ? factory.create() : pool.pop();
    }

    public final void free(T e) {
        pool.push(e);
    }

    @Override
    public Iterator<T> iterator() {
        return pool.iterator();
    }
}
