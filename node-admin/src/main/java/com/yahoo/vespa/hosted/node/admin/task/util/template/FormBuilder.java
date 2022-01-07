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
    private final Map<String, VariableSection> variables = new HashMap<>();
    private final Map<String, ListSection> lists = new HashMap<>();
    private final CursorRange range;

    static Form build(CursorRange range, List<Consumer<FormBuilder>> sections) {
        var builder = new FormBuilder(range);
        sections.forEach(section -> section.accept(builder));
        return builder.build();
    }

    private FormBuilder(CursorRange range) {
        this.range = new CursorRange(range);
    }

    FormBuilder addLiteralSection(CursorRange range) {
        sections.add(new LiteralSection(range));
        return this;
    }

    FormBuilder addVariableSection(CursorRange range, String name, Cursor nameOffset) {
        checkNameIsAvailable(name, range);
        var section = new VariableSection(range, name, nameOffset);
        sections.add(section);
        variables.put(section.name(), section);
        return this;
    }

    FormBuilder addListSection(CursorRange range, String name, Template body) {
        checkNameIsAvailable(name, range);
        var section = new ListSection(range, name, body);
        sections.add(section);
        lists.put(section.name(), section);
        return this;
    }

    private Form build() {
        return new Form(range, sections, variables, lists);
    }

    private void checkNameIsAvailable(String name, CursorRange range) {
        if (variables.containsKey(name) || lists.containsKey(name)) {
            throw new NameAlreadyExistsTemplateException(name, range);
        }
    }
}
