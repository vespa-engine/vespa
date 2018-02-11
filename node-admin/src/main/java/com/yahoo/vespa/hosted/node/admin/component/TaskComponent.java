// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.component;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.ChainedComponent;

public abstract class TaskComponent extends ChainedComponent implements IdempotentTask<TaskContext> {
    protected TaskComponent(ComponentId id) {
        super(id);
    }

    public String name() {
        return getIdString();
    }

    public abstract boolean converge(TaskContext context);
}
