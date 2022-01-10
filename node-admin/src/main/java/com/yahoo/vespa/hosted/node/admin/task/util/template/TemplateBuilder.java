// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author hakonhall
 */
class TemplateBuilder {
    private final Cursor start;
    private final Cursor end;

    private final List<Consumer<FormBuilder>> sections = new ArrayList<>();

    /** The value contains the location of the name of a sample variable section (with that name). */
    private final Map<String, Cursor> names = new HashMap<>();

    TemplateBuilder(Cursor start) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
    }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add((FormBuilder builder) -> builder.addLiteralSection(range));
    }

    void appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add(formBuilder -> formBuilder.addVariableSection(range, name, nameOffset));
    }

    void appendSubformSection(String name, Cursor nameCursor, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);
        verifyNewName(name, nameCursor);
        sections.add(formBuilder -> formBuilder.addSubformSection(range, name, body));
    }

    Template build() {
        var range = new CursorRange(start, end);
        return new Template(range, sections, names);
    }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }

    private void verifyNewName(String name, Cursor cursor) {
        Cursor alreadyDefinedNameCursor = names.put(name, cursor);
        if (alreadyDefinedNameCursor != null) {
            throw new NameAlreadyExistsTemplateException(name, alreadyDefinedNameCursor, cursor);
        }
    }
}
