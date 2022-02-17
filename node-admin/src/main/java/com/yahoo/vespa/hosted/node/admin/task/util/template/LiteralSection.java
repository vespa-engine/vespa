// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

/**
 * Represents a template literal section
 *
 * @see Template
 * @author hakonhall
 */
class LiteralSection extends Section {
    LiteralSection(CursorRange range) {
        super("literal", range);
    }

    @Override
    void appendTo(StringBuilder buffer) {
        range().appendTo(buffer);
    }

    @Override
    void appendCopyTo(SectionList sectionList) {
        sectionList.appendLiteralSection(range().end());
    }
}
