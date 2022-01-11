// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.List;

/**
 * A mutable list of sections at the same level that can be used to build a form, e.g. the if-body.
 *
 * @author hakonhall
 */
class SectionList {
    private final Cursor start;
    private final Cursor end;
    private final FormBuilder formBuilder;

    private final List<Section> sections = new ArrayList<>();

    SectionList(Cursor start, FormBuilder formBuilder) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
        this.formBuilder = formBuilder;
    }

    CursorRange range() { return new CursorRange(start, end); }
    FormBuilder formBuilder() { return formBuilder; }
    List<Section> sections() { return List.copyOf(sections); }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new LiteralSection(range);
        formBuilder.addLiteralSection(section);
        sections.add(section);
    }

    VariableSection appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new VariableSection(range, name, nameOffset);
        formBuilder.addVariableSection(section);
        sections.add(section);
        return section;
    }

    void appendIfSection(boolean negated, String name, Cursor nameOffset, Cursor end,
                                SectionList ifSections) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new IfSection(range, negated, name, nameOffset, ifSections);
        formBuilder.addIfSection(section);
        sections.add(section);
    }

    void appendListSection(String name, Cursor nameOffset, Cursor end, Form body) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new ListSection(range, name, nameOffset, body);
        formBuilder.addListSection(section);
        sections.add(section);
    }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }
}
