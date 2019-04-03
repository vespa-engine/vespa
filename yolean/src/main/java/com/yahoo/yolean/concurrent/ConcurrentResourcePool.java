// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author baldersheim
 */
public class ConcurrentResourcePool<T> implements Iterable<T> {

    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final ResourceFactory<T> factory;

    public ConcurrentResourcePool(ResourceFactory<T> factory) {
        this.factory = factory;
    }

    public final T alloc() {
        final T e = pool.poll();
        return e != null ? e : factory.create();
    }

    public final void free(T e) {
        pool.offer(e);
    }

    @Override
    public Iterator<T> iterator() {
        return pool.iterator();
    }
}
