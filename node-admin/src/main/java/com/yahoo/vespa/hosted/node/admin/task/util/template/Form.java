// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.List;
import java.util.Map;

/**
 * A form is an instance of a template to be filled, e.g. values set for variable sections, etc.
 *
 * @see Template
 * @author hakonhall
 */
public class Form extends Section {
    private final List<Section> sections;

    private final Map<String, VariableSection> variables;
    private final Map<String, ListSection> lists;

    Form(CursorRange range, List<Section> sections, Map<String, VariableSection> variables, Map<String, ListSection> lists) {
        super(range);
        this.sections = List.copyOf(sections);
        this.variables = Map.copyOf(variables);
        this.lists = Map.copyOf(lists);
    }

    /** Set the value of a variable expression, e.g. %{=color}. */
    public Form set(String name, String value) {
        var section = variables.get(name);
        if (section == null) {
            throw new NoSuchNameTemplateException(this, name);
        }
        section.set(value);
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

    /**
     * Append a list element form.  A list section is declared as follows:
     *
     * <pre>
     *     %{list name}...
     *     %{end}
     * </pre>
     *
     * <p>The body between %{list name} and %{end} is instantiated as a form, and appended after
     * any previously added forms.</p>
     *
     * @return the added form
     */
    public Form add(String name) {
        var section = lists.get(name);
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
}
