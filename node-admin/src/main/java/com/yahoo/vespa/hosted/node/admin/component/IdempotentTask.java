// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

/**
 * This class is thread unsafe: All method calls MUST be exclusive and serialized.
 *
 * In a specialized environment it is possible to provide a richer context than TaskContext:
 *  - Define a subclass T of TaskContext with the additional functionality.
 *  - Define task classes that implement IdempotentTask&lt;T&gt;.
 */
public interface IdempotentTask<T extends TaskContext> {
    String name();

    /**
     * Execute an administrative task to converge the system towards some ideal state.
     *
     * converge() must be idempotent: it may be called any number of times, or
     * interrupted at any time e.g. by `kill -9`. The caller must ensure there is at
     * most one invocation of converge() on this instance at any given time.
     *
     * @return false if the system was already converged, i.e. converge() was a no-op.
     * @throws RuntimeException (or a subclass) if the task is unable to converge.
     */
    boolean converge(T context);
}
