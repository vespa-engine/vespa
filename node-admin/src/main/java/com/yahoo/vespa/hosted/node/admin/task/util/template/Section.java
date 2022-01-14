// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.Objects;

/**
 * A section of a template text.
 *
 * @see Template
 * @author hakonhall
 */
abstract class Section {
    private final String type;
    private final CursorRange range;
    private Template template;

    protected Section(String type, CursorRange range) {
        this.type = type;
        this.range = range;
    }

    void setTemplate(Template template) { this.template = template; }

    /** Guaranteed to return non-null after TemplateBuilder::build() returns. */
    protected Template template() { return Objects.requireNonNull(template); }

    protected String type() { return type; }
    protected CursorRange range() { return range; }

    abstract void appendTo(StringBuilder buffer);

    abstract void appendCopyTo(SectionList sectionList);
}
