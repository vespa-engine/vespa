// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

/**
 * <p>This class is thread unsafe: All method calls MUST be exclusive and serialized.</p>
 *
 * <dl>
 *   <dt>In a specialized environment it is possible to provide a richer context than TaskContext:</dt>
 *   <dd>- Define a subclass T of TaskContext with the additional functionality.</dd>
 *   <dd>- Define task classes that implement IdempotentTask&lt;T&gt;.</dd>
 * </dl>
 */
public interface IdempotentTask<T extends TaskContext> {
    /**
     *  <p>A short id of the task to e.g. identify the task in the log.</p>
     *
     *  <p>Prefer PascalCase and without white-space.</p>
     *
     *  <p>Example: "EnableDocker"</p>
     */
    default String name() { return getClass().getSimpleName(); }

    /**
     * <p>Execute an administrative task to converge towards some ideal state, whether it is
     * system state or in-memory Java state.</p>
     *
     * <p>converge() must be idempotent: it may be called any number of times, or
     * interrupted at any time e.g. by `kill -9`.</p>
     *
     * <p>converge() is not thread safe: The caller must ensure there is at most one invocation
     * of converge() at any given time.</p>
     *
     * @return false if already converged, i.e. was a no-op. A typical sequence of converge()
     *         calls on a IdempotentTask will consist of:
     *          - Any number of calls that throws an exception due to some issues. Assuming
     *            no exceptions were thrown, or the issue eventually resolved itself...
     *            (convergence failure)
     *          - Returns true once (converged just now)
     *          - Returns false for all further calls (already converged)
     * @throws RuntimeException (or a subclass) if the task is unable to converge.
     */
    boolean converge(T context);

    /**
     * <p>Converge the task towards some state where it can be suspended. The
     * TaskContext should provide enough to determine what kind of suspend is wanted, e.g.
     * suspension of only the task, or the task and the resources/processes it manages.</p>
     *
     * <p>convergeSuspend() must be idempotent: it may be called any number of times, or
     * interrupted at any time e.g. by `kill -9`.</p>
     *
     * <p>convergeSuspend() is not thread safe: The caller must ensure there is at most one
     * invocation of convergeSuspend() at any given time.</p>
     *
     * @return false if already converged, i.e. was a no-op
     * @throws RuntimeException (or a subclass) if the task is unable to suspend.
     */
    default boolean convergeSuspend(T context) {
        return false;
    }
}
