// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.FeedException;
import ai.vespa.feed.client.ShutdownHandle;
import ai.vespa.feed.client.Result;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Admission and shutdown state. No callbacks or transport effects execute under this monitor. */
final class ShutdownLifecycle implements ShutdownHandle {
    private final BooleanSupplier strategyProgressThread;

    ShutdownLifecycle(BooleanSupplier strategyProgressThread) {
        this.strategyProgressThread = strategyProgressThread;
    }

    private Phase phase = Phase.OPEN;
    private Throwable failure;
    private int pendingAdmissions;
    private Thread cleanupThread;
    private final ThreadLocal<Integer> publicationDepth = ThreadLocal.withInitial(() -> 0);

    private final Set<Operation> operations = new HashSet<>();

    final class Operation {
        final CompletableFuture<Result> result = new CompletableFuture<>();
        private boolean resolved;
        private boolean registered;

        void publish(Result value, Throwable failure) {
            synchronized (ShutdownLifecycle.this) { resolved = true; ShutdownLifecycle.this.notifyAll(); }
            int depth = publicationDepth.get();
            publicationDepth.set(depth + 1);
            try {
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            } finally {
                if (depth == 0) publicationDepth.remove(); else publicationDepth.set(depth);
                synchronized (ShutdownLifecycle.this) { prune(); ShutdownLifecycle.this.notifyAll(); }
            }
        }
    }

    private void prune() {
        operations.removeIf(operation -> operation.registered && operation.resolved && operation.result.isDone());
    }

    synchronized Operation admit() {
        if (phase != Phase.OPEN) throw new IllegalStateException("Client is closed");
        pendingAdmissions++;
        Operation operation = new Operation();
        operations.add(operation);
        return operation;
    }

    synchronized void registered(Operation operation) {
        operation.registered = true;
        pendingAdmissions--;
        prune();
        notifyAll();
    }

    synchronized void awaitOperations(BooleanSupplier keepWaiting) throws InterruptedException {
        prune();
        while (keepWaiting.getAsBoolean() && !operations.isEmpty()) {
            // CompletableFuture publishes its value before running inline user callbacks.
            // Such callbacks may prevent publish() from returning and notifying us again.
            wait(10);
            prune();
        }
    }

    /** Returns whether this caller owns starting cleanup. */
    synchronized boolean request(boolean graceful) {
        boolean first = phase == Phase.OPEN;
        if (first || (phase == Phase.DRAINING && !graceful)) {
            phase = graceful ? Phase.DRAINING : Phase.ABORTING;
            notifyAll();
        }
        return first;
    }

    synchronized boolean draining() { return phase == Phase.DRAINING; }

    synchronized boolean awaitAdmissions() throws InterruptedException {
        while (phase == Phase.DRAINING && pendingAdmissions > 0) wait();
        return phase == Phase.DRAINING;
    }

    synchronized void cleanupStarted() { cleanupThread = Thread.currentThread(); }

    synchronized void finish(Throwable failure) {
        cleanupThread = null;
        this.failure = failure;
        phase = failure == null ? Phase.TERMINATED : Phase.FAILED;
        notifyAll();
    }

    @Override public synchronized Snapshot snapshot() {
        prune();
        return new Snapshot(phase, failure, pendingAdmissions, operations.size());
    }

    private boolean terminal() { return phase == Phase.TERMINATED || phase == Phase.FAILED; }

    private void reportFailure() {
        if (failure != null) throw new FeedException("Feed client shutdown failed", failure);
    }

    private void checkWait() {
        if (!terminal() && (publicationDepth.get() > 0 || Thread.currentThread() == cleanupThread || strategyProgressThread.getAsBoolean()))
            throw new IllegalStateException("Shutdown continues; use closeAsync from a progress-owning thread instead of waiting");
    }

    @Override public synchronized void await() throws InterruptedException {
        checkWait();
        while (!terminal()) wait();
        reportFailure();
    }

    @Override public synchronized void await(Duration timeout) throws InterruptedException, TimeoutException {
        checkWait();
        long remaining = timeout.toNanos();
        if (remaining < 0) throw new IllegalArgumentException("Negative shutdown wait timeout");
        long start = System.nanoTime();
        while (!terminal()) {
            if (remaining <= 0) throw new TimeoutException("Feed client shutdown has not completed");
            long millis = remaining / 1_000_000;
            int nanos = (int) (remaining % 1_000_000);
            wait(millis, nanos);
            remaining = timeout.toNanos() - (System.nanoTime() - start);
        }
        reportFailure();
    }
}
