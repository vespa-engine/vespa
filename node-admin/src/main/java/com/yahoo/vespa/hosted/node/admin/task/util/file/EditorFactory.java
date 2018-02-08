// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.file.Path;

/**
 * @author hakonhall
 */
public class EditorFactory {
    public Editor create(Path path, LineEditor editor) {
        return new Editor(path, editor);
    }
}
