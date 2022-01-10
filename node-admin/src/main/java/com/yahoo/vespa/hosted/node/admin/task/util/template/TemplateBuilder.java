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

    /** Example location (value) of a variable name (key). */
    private final Map<String, Cursor> variables = new HashMap<>();

    /** Location of the name of each subform. */
    private final Map<String, Cursor> subforms = new HashMap<>();

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
        Cursor existing = subforms.get(name);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameOffset);
        variables.put(name, new Cursor(nameOffset));
        sections.add(formBuilder -> formBuilder.addVariableSection(range, name, nameOffset));
    }

    void appendSubformSection(String name, Cursor nameCursor, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);

        Cursor existing = variables.get(name);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameCursor);

        existing = subforms.put(name, nameCursor);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameCursor);

        sections.add(formBuilder -> formBuilder.addSubformSection(range, name, body));
    }

    Template build() {
        var range = new CursorRange(start, end);
        return new Template(range, sections, variables, subforms);
    }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }
}
