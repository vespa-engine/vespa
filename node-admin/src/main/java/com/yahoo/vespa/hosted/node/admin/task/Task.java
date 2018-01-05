// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task;

import com.yahoo.vespa.hosted.node.admin.io.FileSystem;

import java.util.regex.Pattern;

public interface Task {
    /**
     * If the task supports different variants, this method should be overridden
     * to return something distinctive for this task instance. For instance a
     * "WriteFileTask" that supports writing files at a 'path' could return
     * the stem of the filename as the variantName(). variantName() must match
     * NAME_PATTERN to ensure name is valid as key in various contexts.
     */
    default String variantName() { return ""; }

    default void validate() { validateVariant(variantName()); }
    static void validateVariant(String variant) {
        Pattern variantPattern = Pattern.compile("^[a-zA-Z0-9_-]*$");
        if (!variantPattern.matcher(variant).matches()) {
            throw new IllegalArgumentException("variantName '" + variant + "' is not a valid");
        }
    }

    interface TaskContext {
        FileSystem getFileSystem();
        boolean executeSubtask(Task task);
    }

    /**
     * @return Returns false if task was a no-op. Used for informational purposes only.
     * @throws RuntimeException if task could not be completed.
     */
    boolean execute(TaskContext context);
}
