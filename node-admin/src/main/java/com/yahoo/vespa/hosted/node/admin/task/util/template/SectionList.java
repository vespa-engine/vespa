// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A mutable list of sections at the same level that can be used to build a form, e.g. the if-body.
 *
 * @author hakonhall
 */
class SectionList {
    private TemplateBuilder templateBuilder;

    private final Cursor start;
    private final Cursor end;

    private final List<Consumer<FormBuilder>> sections = new ArrayList<>();

    SectionList(Cursor start) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
    }

    /** Must be invoked once before any other method. */
    void setTemplateBuilder(TemplateBuilder templateBuilder) {
        this.templateBuilder = templateBuilder;
    }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add((FormBuilder builder) -> builder.addLiteralSection(range));
    }

    void appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        templateBuilder.addVariable(name, nameOffset);
        sections.add(formBuilder -> formBuilder.addVariableSection(range, name, nameOffset));
    }

    void appendSubformSection(String name, Cursor nameCursor, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);
        templateBuilder.addSubform(name, nameCursor);
        sections.add(formBuilder -> formBuilder.addSubformSection(range, name, body));
    }

    CursorRange range() { return new CursorRange(start, end); }
    List<Consumer<FormBuilder>> sections() { return List.copyOf(sections); }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }
}
