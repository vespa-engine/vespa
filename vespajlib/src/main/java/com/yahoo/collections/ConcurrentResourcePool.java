// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created with IntelliJ IDEA.
 * User: balder
 * Date: 13.11.12
 * Time: 20:57
 * To change this template use File | Settings | File Templates.
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
