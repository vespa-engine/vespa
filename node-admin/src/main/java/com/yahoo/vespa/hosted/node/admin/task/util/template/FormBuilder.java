// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author hakonhall
 */
class FormBuilder {
    private final List<Section> sections = new ArrayList<>();
    private final Map<String, String> variables = new HashMap<>();
    private final Map<String, SubformSection> subforms = new HashMap<>();
    private final Form parent;
    private final CursorRange range;

    static Form build(Form parent, CursorRange range, List<Consumer<FormBuilder>> sections) {
        var builder = new FormBuilder(parent, range);
        sections.forEach(section -> section.accept(builder));
        return builder.build();
    }

    private FormBuilder(Form parent, CursorRange range) {
        this.parent = parent;
        this.range = new CursorRange(range);
    }

    FormBuilder addLiteralSection(CursorRange range) {
        sections.add(new LiteralSection(range));
        return this;
    }

    FormBuilder addVariableSection(CursorRange range, String name, Cursor nameOffset) {
        var section = new VariableSection(range, name, nameOffset);
        sections.add(section);
        return this;
    }

    FormBuilder addSubformSection(CursorRange range, String name, Template body) {
        checkNameIsAvailable(name, range);
        var section = new SubformSection(range, name, body);
        sections.add(section);
        subforms.put(section.name(), section);
        return this;
    }

    private Form build() {
        var form = new Form(parent, range, sections, variables, subforms);
        sections.forEach(section -> section.setForm(form));
        return form;
    }

    private void checkNameIsAvailable(String name, CursorRange range) {
        if (nameIsDefined(name)) {
            throw new NameAlreadyExistsTemplateException(name, range);
        }
    }

    private boolean nameIsDefined(String name) {
        return variables.containsKey(name) || subforms.containsKey(name);
    }
}
