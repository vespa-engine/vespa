// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * A pool of a resource. This create new instances of the resource on request until enough are created
 * to deliver a unique one to all threads needing one concurrently and then reuse those instances
 * in subsequent requests.
 *
 * @author baldersheim
 */
public class ConcurrentResourcePool<T> implements Iterable<T> {

    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;


    public ConcurrentResourcePool(Supplier<T> factory) {
        this.factory = factory;
    }

    public void preallocate(int instances) {
        for (int i = 0; i < instances; i++) {
            pool.offer(factory.get());
        }
    }

    /**
     * Allocates an instance of the resource to the requestor.
     * The resource will be allocated exclusively to the requestor until it calls free(instance).
     *
     * @return a reused or newly created instance of the resource
     */
    public final T alloc() {
        T e = pool.poll();
        return e != null ? e : factory.get();
    }

    /** Frees an instance previously acquired bty alloc */
    public final void free(T e) {
        pool.offer(e);
    }

    @Override
    public Iterator<T> iterator() {
        return pool.iterator();
    }

}
