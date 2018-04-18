// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.REPLACE;
import static com.yahoo.vespa.hosted.node.admin.task.util.file.LineEdit.Type.NONE;

/**
 * @author hakonhall
 */
@Immutable
public class LineEdit {
    enum Type { NONE, REPLACE }

    public static LineEdit none() { return insert(Collections.emptyList(), Collections.emptyList()); }
    public static LineEdit remove() { return replaceWith(Collections.emptyList()); }

    public static LineEdit insertBefore(String... prepend) { return insertBefore(Arrays.asList(prepend)); }
    public static LineEdit insertBefore(List<String> prepend) { return insert(prepend, Collections.emptyList()); }
    public static LineEdit insertAfter(String... append) { return insertAfter(Arrays.asList(append)); }
    public static LineEdit insertAfter(List<String> append) { return insert(Collections.emptyList(), append); }
    public static LineEdit insert(List<String> prepend, List<String> append) { return new LineEdit(NONE, prepend, append); }

    public static LineEdit replaceWith(String... lines) { return replaceWith(Arrays.asList(lines)); }
    public static LineEdit replaceWith(List<String> insertLines) { return new LineEdit(REPLACE, Collections.emptyList(), insertLines); }

    private final Type type;
    private final List<String> prependLines;
    private final List<String> appendLines;

    private LineEdit(Type type, List<String> prependLines, List<String> appendLines) {
        this.type = type;
        this.prependLines = ImmutableList.copyOf(prependLines);
        this.appendLines = ImmutableList.copyOf(appendLines);
    }

    public Type getType() { return type; }
    public List<String> prependLines() { return prependLines; }
    public List<String> appendLines() { return appendLines; }
}
