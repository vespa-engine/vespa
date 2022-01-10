// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A form is an instance of a template to be filled, e.g. values set for variable sections, etc.
 *
 * @see Template
 * @author hakonhall
 */
public class Form {
    private Form parent = null;
    private final CursorRange range;
    private final List<Section> sections;

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, SubformSection> subforms;

    Form(CursorRange range, List<Section> sections, Map<String, SubformSection> subforms) {
        this.range = new CursorRange(range);
        this.sections = List.copyOf(sections);
        this.subforms = Map.copyOf(subforms);
    }

    void setParent(Form parent) { this.parent = parent; }

    /** Set the value of a variable expression, e.g. %{=color}. */
    public Form set(String name, String value) {
        values.put(name, value);
        return this;
    }

    public Form set(String name, boolean value) { return set(name, Boolean.toString(value)); }
    public Form set(String name, int value) { return set(name, Integer.toString(value)); }
    public Form set(String name, long value) { return set(name, Long.toString(value)); }

    public Form set(String name, String format, String first, String... rest) {
        var args = new Object[1 + rest.length];
        args[0] = first;
        System.arraycopy(rest, 0, args, 1, rest.length);
        var value = String.format(format, args);

        return set(name, value);
    }

    /** Add an instance of a subform section after any previously added (for the given name)  */
    public Form add(String name) {
        var section = subforms.get(name);
        if (section == null) {
            throw new NoSuchNameTemplateException(range, name);
        }
        return section.add();
    }

    public String render() {
        var buffer = new StringBuilder((int) (range.length() * 1.2 + 128));
        appendTo(buffer);
        return buffer.toString();
    }

    public void appendTo(StringBuilder buffer) {
        sections.forEach(section -> section.appendTo(buffer));
    }

    /** Returns a deep copy of this. No changes to this affects the returned form, and vice versa. */
    Form copy() {
        var builder = new FormBuilder(range.start());
        sections.forEach(section -> section.appendCopyTo(builder.topLevelSectionList()));
        return builder.build();
    }

    Optional<String> getVariableValue(String name) {
        String value = values.get(name);
        if (value != null) return Optional.of(value);
        if (parent != null) {
            return parent.getVariableValue(name);
        }
        return Optional.empty();
    }
}
