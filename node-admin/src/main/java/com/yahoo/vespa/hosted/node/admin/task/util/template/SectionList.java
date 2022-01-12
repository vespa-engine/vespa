// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A mutable list of sections at the same level that can be used to build a template, e.g. the if-body.
 *
 * @author hakonhall
 */
class SectionList {
    private final Cursor start;
    private final Cursor end;
    private final TemplateBuilder templateBuilder;

    private final List<Section> sections = new ArrayList<>();

    SectionList(Cursor start, TemplateBuilder templateBuilder) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
        this.templateBuilder = templateBuilder;
    }

    CursorRange range() { return new CursorRange(start, end); }
    TemplateBuilder templateBuilder() { return templateBuilder; }
    List<Section> sections() { return List.copyOf(sections); }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new LiteralSection(range);
        templateBuilder.addLiteralSection(section);
        sections.add(section);
    }

    VariableSection appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new VariableSection(range, name, nameOffset);
        templateBuilder.addVariableSection(section);
        sections.add(section);
        return section;
    }

    void appendIfSection(boolean negated, String name, Cursor nameOffset, Cursor end,
                         SectionList ifSections, Optional<SectionList> elseSections) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new IfSection(range, negated, name, nameOffset, ifSections, elseSections);
        templateBuilder.addIfSection(section);
        sections.add(section);
    }

    ListSection appendListSection(String name, Cursor nameOffset, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);
        var section = new ListSection(range, name, nameOffset, body);
        templateBuilder.addListSection(section);
        sections.add(section);
        return section;
    }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }
}
