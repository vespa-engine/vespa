// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import javax.annotation.concurrent.Immutable;
import java.util.Optional;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.REMOVE;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.REPLACE;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.NONE;

/**
 * @author hakonhall
 */
@Immutable
public class LineEdit {
    enum Type { NONE, REPLACE, REMOVE}
    private final Type type;
    private final Optional<String> line;

    public static LineEdit none() { return new LineEdit(NONE, Optional.empty()); }
    public static LineEdit remove() { return new LineEdit(REMOVE, Optional.empty()); }
    public static LineEdit replaceWith(String line) { return new LineEdit(REPLACE, Optional.of(line)); }

    private LineEdit(Type type, Optional<String> newLine) {
        this.type = type;
        this.line = newLine;
    }

    public Type getType() { return type; }
    public String replacementLine() { return line.get(); }
}
