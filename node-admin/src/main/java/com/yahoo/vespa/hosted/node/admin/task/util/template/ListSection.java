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
    private final Form body;
    private final List<Form> elements = new ArrayList<>();

    ListSection(CursorRange range, String name, Cursor nameOffset, Form body) {
        super(range);
        this.name = name;
        this.nameOffset = new Cursor(nameOffset);
        this.body = body;
    }

    String name() { return name; }
    Cursor nameOffset() { return new Cursor(nameOffset); }

    @Override
    void setForm(Form form) {
        super.setForm(form);
        body.setParent(form);
    }

    Form add() {
        Form element = body.copy();
        element.setParent(form());
        elements.add(element);
        return element;
    }

    @Override
    void appendTo(StringBuilder buffer) {
        elements.forEach(form -> form.appendTo(buffer));
    }

    @Override
    void appendCopyTo(SectionList sectionList) {
        // avoid copying elements for now
        // Optimization: Reuse body in copy, since it is only used for copying.

        sectionList.appendListSection(name, nameOffset, range().end(), body);
    }
}
