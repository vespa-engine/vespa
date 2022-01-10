// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A form is an instance of a template to be filled, e.g. values set for variable sections, etc.
 *
 * @see Template
 * @author hakonhall
 */
public class Form extends Section {
    private final Form parent;
    private final List<Section> sections;

    private final Map<String, String> variables;
    private final Map<String, SubformSection> subforms;

    Form(Form parent, CursorRange range, List<Section> sections, Map<String, String> variables,
         Map<String, SubformSection> subforms) {
        super(range);
        this.parent = parent;
        this.sections = List.copyOf(sections);
        this.variables = variables; // Mutable and referenced by the variable sections
        this.subforms = Map.copyOf(subforms);
    }

    /** Set the value of a variable expression, e.g. %{=color}. */
    public Form set(String name, String value) {
        variables.put(name, value);
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
            throw new NoSuchNameTemplateException(this, name);
        }
        return section.add();
    }

    public String render() {
        var buffer = new StringBuilder((int) (range().length() * 1.2 + 128));
        appendTo(buffer);
        return buffer.toString();
    }

    @Override
    public void appendTo(StringBuilder buffer) {
        sections.forEach(section -> section.appendTo(buffer));
    }

    Optional<String> getVariableValue(String name) {
        String value = variables.get(name);
        if (value != null) return Optional.of(value);
        if (parent != null) {
            return parent.getVariableValue(name);
        }
        return Optional.empty();
    }
}
