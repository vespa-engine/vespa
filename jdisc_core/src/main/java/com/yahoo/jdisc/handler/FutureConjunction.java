// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>This class implements a Future&lt;Boolean&gt; that is conjunction of zero or more other Future&lt;Boolean&gt;s,
 * i.e. it evaluates to <tt>true</tt> if, and only if, all its operands evaluate to <tt>true</tt>. To use this class,
 * simply create an instance of it and add operands to it using the {@link #addOperand(ListenableFuture)} method.</p>
 * TODO: consider rewriting usage of FutureConjunction to use CompletableFuture instead.
 *
 * @author Simon Thoresen Hult
 */
public final class FutureConjunction implements ListenableFuture<Boolean> {

    private final List<ListenableFuture<Boolean>> operands = new LinkedList<>();

    /**
     * <p>Adds a ListenableFuture&lt;Boolean&gt; to this conjunction. This can be called at any time, even after having called
     * {@link #get()} previously.</p>
     *
     * @param operand The operand to add to this conjunction.
     */
    public void addOperand(ListenableFuture<Boolean> operand) {
        operands.add(operand);
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        Futures.allAsList(operands).addListener(listener, executor);
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        boolean ret = true;
        for (Future<Boolean> op : operands) {
            if (!op.cancel(mayInterruptIfRunning)) {
                ret = false;
            }
        }
        return ret;
    }

    @Override
    public final boolean isCancelled() {
        for (Future<Boolean> op : operands) {
            if (!op.isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean isDone() {
        for (Future<Boolean> op : operands) {
            if (!op.isDone()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final Boolean get() throws InterruptedException, ExecutionException {
        Boolean ret = Boolean.TRUE;
        for (Future<Boolean> op : operands) {
            if (!op.get()) {
                ret = Boolean.FALSE;
            }
        }
        return ret;
    }

    @Override
    public final Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                                                                 TimeoutException {
        Boolean ret = Boolean.TRUE;
        long nanos = unit.toNanos(timeout);
        long lastTime = System.nanoTime();
        for (Future<Boolean> op : operands) {
            if (!op.get(nanos, TimeUnit.NANOSECONDS)) {
                ret = Boolean.FALSE;
            }
            long now = System.nanoTime();
            nanos -= now - lastTime;
            lastTime = now;
        }
        return ret;
    }

}
