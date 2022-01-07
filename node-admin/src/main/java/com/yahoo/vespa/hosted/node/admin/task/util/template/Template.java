// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Java representation of a template text.
 *
 * <p>A template is a sequence of literal text and dynamic sections defined by %{...} directives:</p>
 *
 * <pre>
 *     template: section*
 *     section: literal | variable | list
 *     literal: # plain text not containing %{
 *     variable: %{=identifier}
 *     list: %{list identifier}template%{end}
 *     identifier: # a valid Java identifier
 * </pre>
 *
 * <p>Any newline (\n) following a non-variable directive is removed.</p>
 *
 * <p><b>Instantiate</b> the template to get a form ({@link #instantiate()}), fill it
 * (e.g. {@link Form#set(String, String) Form.set()}), and render the String ({@link Form#render()}).</p>
 *
 * @see Form
 * @see TemplateParser
 * @see TemplateFile
 * @author hakonhall
 */
public class Template {
    private final Cursor start;
    private final Cursor end;

    private final List<Consumer<FormBuilder>> sections = new ArrayList<>();
    private final HashSet<String> names = new HashSet<>();

    Template(Cursor start) {
        this.start = new Cursor(start);
        this.end = new Cursor(start);
    }

    public Form instantiate() {
        return FormBuilder.build(range(), sections);
    }

    void appendLiteralSection(Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        sections.add((FormBuilder builder) -> builder.addLiteralSection(range));
    }

    void appendVariableSection(String name, Cursor nameOffset, Cursor end) {
        CursorRange range = verifyAndUpdateEnd(end);
        verifyAndAddNewName(name, nameOffset);
        sections.add(formBuilder -> formBuilder.addVariableSection(range, name, nameOffset));
    }

    void appendListSection(String name, Cursor nameCursor, Cursor end, Template body) {
        CursorRange range = verifyAndUpdateEnd(end);
        verifyAndAddNewName(name, nameCursor);
        sections.add(formBuilder -> formBuilder.addListSection(range, name, body));
    }

    private CursorRange range() { return new CursorRange(start, end); }

    private CursorRange verifyAndUpdateEnd(Cursor newEnd) {
        var range = new CursorRange(this.end, newEnd);
        this.end.set(newEnd);
        return range;
    }

    private String verifyAndAddNewName(String name, Cursor nameOffset) {
        if (!names.add(name)) {
            throw new IllegalArgumentException("'" + name + "' at " +
                                               nameOffset.calculateLocation().lineAndColumnText() +
                                               " has already been defined");
        }
        return name;
    }
}
