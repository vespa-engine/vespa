// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TestTaskContext implements TaskContext {
    private final List<String> logs = new ArrayList<>();

    @Override
    public void recordSystemModification(Logger logger, String description) {
        logs.add(description);
    }

    public List<String> getSystemModificationLog() {
        return logs;
    }

    public void clearSystemModificationLog() {
        logs.clear();
    }

    @Override
    public boolean executeSubtask(IdempotentTask task) {
        throw new UnsupportedOperationException();
    }
}
