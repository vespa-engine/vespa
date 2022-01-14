// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

/**
 * Represents a template variable section
 *
 * @see Template
 * @author hakonhall
 */
class VariableSection extends Section {
    private final String name;
    private final Cursor nameOffset;

    VariableSection(CursorRange range, String name, Cursor nameOffset) {
        super("variable", range);
        this.name = name;
        this.nameOffset = nameOffset;
    }

    String name() { return name; }
    Cursor nameOffset() { return new Cursor(nameOffset); }

    @Override
    void appendTo(StringBuilder buffer) {
        String value = template().getVariableValue(name)
                                 .orElseThrow(() -> new TemplateNameNotSetException(name, nameOffset));
        buffer.append(value);
    }

    @Override
    void appendCopyTo(SectionList sectionList) {
        sectionList.appendVariableSection(name, nameOffset, range().end());
    }
}
