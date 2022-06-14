// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.REPLACE;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.NONE;

/**
 * @author hakonhall
 */
public class LineEdit {
    enum Type { NONE, REPLACE }

    public static LineEdit none() { return insert(List.of(), List.of()); }
    public static LineEdit remove() { return replaceWith(List.of()); }

    public static LineEdit insertBefore(String... prepend) { return insertBefore(List.of(prepend)); }
    public static LineEdit insertBefore(List<String> prepend) { return insert(prepend, List.of()); }
    public static LineEdit insertAfter(String... append) { return insertAfter(List.of(append)); }
    public static LineEdit insertAfter(List<String> append) { return insert(List.of(), append); }
    public static LineEdit insert(List<String> prepend, List<String> append) { return new LineEdit(NONE, prepend, append); }

    public static LineEdit replaceWith(String... lines) { return replaceWith(List.of(lines)); }
    public static LineEdit replaceWith(List<String> insertLines) { return new LineEdit(REPLACE, List.of(), insertLines); }

    private final Type type;
    private final List<String> prependLines;
    private final List<String> appendLines;

    private LineEdit(Type type, List<String> prependLines, List<String> appendLines) {
        this.type = type;
        this.prependLines = List.copyOf(prependLines);
        this.appendLines = List.copyOf(appendLines);
    }

    public Type getType() { return type; }
    public List<String> prependLines() { return prependLines; }
    public List<String> appendLines() { return appendLines; }
}
