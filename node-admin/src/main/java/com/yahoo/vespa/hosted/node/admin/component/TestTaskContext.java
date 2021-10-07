// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestTaskContext implements TaskContext {
    private final List<String> systemModifications = new ArrayList<>();

    @Override
    public void recordSystemModification(Logger logger, String description) {
        systemModifications.add(description);
    }

    @Override
    public void log(Logger logger, Level level, String message) {
        logger.log(level, message);
    }

    @Override
    public void log(Logger logger, Level level, String message, Throwable throwable) {
        logger.log(level, message, throwable);
    }

    public List<String> getSystemModificationLog() {
        return systemModifications;
    }

    public void clearSystemModificationLog() {
        systemModifications.clear();
    }
}
