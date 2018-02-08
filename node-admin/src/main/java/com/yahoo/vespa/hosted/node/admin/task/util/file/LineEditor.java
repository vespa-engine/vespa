// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.List;

/**
 * @author hakonhall
 */
public interface LineEditor {
    /**
     * @param line The line of a file.
     * @return The edited line, or empty if the line should be removed.
     */
    LineEdit edit(String line);

    /**
     * Called after edit() has been called on all lines in the file.
     * @return Lines to append to the file.
     */
    List<String> onComplete();
}
