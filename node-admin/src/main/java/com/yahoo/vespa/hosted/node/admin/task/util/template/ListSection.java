// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a template list section
 *
 * @see Template
 * @author hakonhall
 */
class ListSection extends Section {
    private final Template body;
    private final String name;
    private final List<Form> elements = new ArrayList<>();

    ListSection(CursorRange range, String name, Template body) {
        super(range);
        this.name = name;
        this.body = body;
    }

    String name() { return name; }

    Form add() {
        var form = body.instantiate();
        elements.add(form);
        return form;
    }

    @Override
    void appendTo(StringBuilder buffer) {
        elements.forEach(form -> form.appendTo(buffer));
    }
}
