// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.ChainedComponent;

public abstract class TaskComponent extends ChainedComponent implements IdempotentTask {
    protected TaskComponent(ComponentId id) {
        super(id);
    }

    public String name() {
        return getIdString();
    }

    /**
     * Execute an administrative task to converge the system towards some ideal state.
     *
     * converge() must be idempotent: it may be retried any number of times, or
     * interrupted at any time e.g. by `kill -9`. However, it must not be called
     * concurrently with itself or other methods of this instance.
     *
     * @return false if the system was already converged, i.e. converge() was a no-op.
     * @throws RuntimeException (or a subclass) if the task is unable to converge.
     */
    public abstract boolean converge(TaskContext context);
}
