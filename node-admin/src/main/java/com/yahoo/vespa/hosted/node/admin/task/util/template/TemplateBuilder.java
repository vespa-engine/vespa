// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hakonhall
 */
class TemplateBuilder {
    private final SectionList sections;

    /** Example location (value) of a variable name (key). */
    private final Map<String, Cursor> variables = new HashMap<>();

    /** Location of the name of each subform. */
    private final Map<String, Cursor> subforms = new HashMap<>();

    TemplateBuilder(Cursor start) {
        sections = new SectionList(start);
        sections.setTemplateBuilder(this);
    }

    SectionList sectionList() { return sections; }

    void addVariable(String name, Cursor nameOffset) {
        Cursor existing = subforms.get(name);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameOffset);
        variables.put(name, new Cursor(nameOffset));
    }

    void addSubform(String name, Cursor nameCursor) {
        Cursor existing = variables.get(name);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameCursor);

        existing = subforms.put(name, nameCursor);
        if (existing != null)
            throw new NameAlreadyExistsTemplateException(name, existing, nameCursor);
    }

    Template build() {
        return new Template(sections.range(), sections.sections(), variables, subforms);
    }
}
