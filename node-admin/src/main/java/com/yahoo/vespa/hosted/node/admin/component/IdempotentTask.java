// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

/**
 * This class is thread unsafe: All method calls MUST be exclusive and serialized.
 */
public interface IdempotentTask {
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
    boolean converge(TaskContext context);
}
