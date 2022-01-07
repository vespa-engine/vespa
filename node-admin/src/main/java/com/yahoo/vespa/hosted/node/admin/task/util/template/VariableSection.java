// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Objects;

/**
 * Represents a template variable section
 *
 * @see Template
 * @author hakonhall
 */
class VariableSection extends Section {
    private final String name;
    private final Cursor nameOffset;

    private String value = null;

    VariableSection(CursorRange range, String name, Cursor nameOffset) {
        super(range);
        this.name = name;
        this.nameOffset = nameOffset;
    }

    String name() { return name; }

    void set(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    void appendTo(StringBuilder buffer) {
        if (value == null) {
            throw new TemplateNameNotSetException(this, name, nameOffset);
        }

        buffer.append(value);
    }
}
