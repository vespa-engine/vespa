// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.List;

/**
 * @author hakonhall
 */
class ListSection extends Section {
    private final String name;
    private final Cursor nameOffset;
    private final Template body;
    private final List<Template> elements = new ArrayList<>();

    ListSection(CursorRange range, String name, Cursor nameOffset, Template body) {
        super("list", range);
        this.name = name;
        this.nameOffset = new Cursor(nameOffset);
        this.body = body;
    }

    String name() { return name; }
    Cursor nameOffset() { return new Cursor(nameOffset); }

    @Override
    void setTemplate(Template template) {
        super.setTemplate(template);
        body.setParent(template);
    }

    Template add() {
        Template element = body.snapshot();
        element.setParent(template());
        elements.add(element);
        return element;
    }

    @Override
    void appendTo(StringBuilder buffer) {
        elements.forEach(template -> template.appendTo(buffer));
    }

    @Override
    void appendCopyTo(SectionList sectionList) {
        // Optimization: Reuse body in copy, since it is only used for copying.

        ListSection newSection = sectionList.appendListSection(name, nameOffset, range().end(), body);

        elements.stream()
                .map(template -> {
                    Template templateCopy = template.snapshot();
                    templateCopy.setParent(template());
                    return templateCopy;
                })
                .forEach(newSection.elements::add);
    }
}
