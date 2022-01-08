// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

/**
 * A section of a template text.
 *
 * @see Template
 * @author hakonhall
 */
abstract class Section {
    private final CursorRange range;
    private Form form;

    protected Section(CursorRange range) {
        this.range = range;
    }

    void setForm(Form form) { this.form = form; }

    /** Guaranteed to return non-null after FormBuilder::build() returns. */
    protected Form form() { return form; }

    protected CursorRange range() { return range; }

    abstract void appendTo(StringBuilder buffer);
}
