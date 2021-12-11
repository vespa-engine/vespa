// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.concurrent.CompletableFutures;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>This class implements a Future&lt;Boolean&gt; that is conjunction of zero or more other Future&lt;Boolean&gt;s,
 * i.e. it evaluates to <code>true</code> if, and only if, all its operands evaluate to <code>true</code>. To use this class,
 * simply create an instance of it and add operands to it using the {@link #addOperand(CompletableFuture)} method.</p>
 *
 * @author Simon Thoresen Hult
 */
final class FutureConjunction implements Future<Boolean> {

    private final List<CompletableFuture<Boolean>> operands = new LinkedList<>();

    /**
     * <p>Adds a {@link CompletableFuture} to this conjunction. This can be called at any time, even after having called
     * {@link #get()} previously.</p>
     *
     * @param operand The operand to add to this conjunction.
     */
    public void addOperand(CompletableFuture<Boolean> operand) {
        operands.add(operand);
    }

    public void addListener(Runnable listener, Executor executor) {
        CompletableFutures.allOf(operands)
                .whenCompleteAsync((__, ___) -> listener.run(), executor);
    }

    CompletableFuture<Boolean> completableFuture() {
        return CompletableFutures.allOf(operands)
                .thenApply(ops -> ops.stream().allMatch(bool -> bool));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean ret = true;
        for (Future<Boolean> op : operands) {
            if (!op.cancel(mayInterruptIfRunning)) {
                ret = false;
            }
        }
        return ret;
    }

    @Override
    public boolean isCancelled() {
        for (Future<Boolean> op : operands) {
            if (!op.isCancelled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isDone() {
        for (Future<Boolean> op : operands) {
            if (!op.isDone()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Boolean get() throws InterruptedException, ExecutionException {
        boolean ret = Boolean.TRUE;
        for (Future<Boolean> op : operands) {
            if (!op.get()) {
                ret = Boolean.FALSE;
            }
        }
        return ret;
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                                                                 TimeoutException {
        boolean ret = Boolean.TRUE;
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
