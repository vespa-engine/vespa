// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.file.FileWriter;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Java representation of a template text.
 *
 * <p>A template is a sequence of literal text and dynamic sections defined by %{...} directives:</p>
 *
 * <pre>
 *     template: section*
 *     section: literal | variable | list
 *     literal: plain text not containing %{
 *     variable: %{=id}
 *     if: %{if [!]id}template[%{else}template]%{end}
 *     list: %{list id}template%{end}
 *     id: a valid Java identifier
 * </pre>
 *
 * <p>Other directive delimiters than "%{" and "}" may be used, see {@link TemplateDescriptor}.</p>
 *
 * <p>Fill the template with variable values ({@link #set(String, String) set()}, set if conditions
 * ({@link #set(String, boolean)}), add list elements ({@link #add(String) add()}, etc, and finally
 * render it as a String ({@link #render()}).</p>
 *
 * <p>To reuse a template, create the template and work on snapshots of that ({@link #snapshot()}).</p>
 *
 * @see TemplateDescriptor
 * @author hakonhall
 */
public class Template implements Form {
    private Template parent = null;
    private final CursorRange range;
    private final List<Section> sections;

    private final Map<String, String> values = new HashMap<>();
    private final Map<String, ListSection> lists;

    public static Template at(Path path) { return at(path, new TemplateDescriptor()); }
    public static Template at(Path path, TemplateDescriptor descriptor) {
        String content = new UnixPath(path).readUtf8File();
        return Template.from(content, descriptor);
    }

    public static Template from(String text) { return from(text, new TemplateDescriptor()); }
    public static Template from(String text, TemplateDescriptor descriptor) {
        return TemplateParser.parse(text, descriptor).template();
    }

    Template(CursorRange range, List<Section> sections, Map<String, ListSection> lists) {
        this.range = new CursorRange(range);
        this.sections = List.copyOf(sections);
        this.lists = Map.copyOf(lists);
    }

    /** Set the value of a variable, e.g. %{=color}. */
    @Override
    public Template set(String name, String value) {
        values.put(name, value);
        return this;
    }

    @Override
    public ListElement add(String name) { return new ListElement(addElement(name)); }

    public String render() {
        var buffer = new StringBuilder((int) (range.length() * 1.2 + 128));
        appendTo(buffer);
        return buffer.toString();
    }

    public void appendTo(StringBuilder buffer) { sections.forEach(section -> section.appendTo(buffer)); }

    /** Returns a deep copy of this. No changes to this affects the returned template, and vice versa. */
    public Template snapshot() {
        var builder = new TemplateBuilder(range.start());
        sections.forEach(section -> section.appendCopyTo(builder.topLevelSectionList()));
        Template template = builder.build();
        values.forEach(template::set);
        return template;
    }

    public FileWriter getFileWriterTo(Path path) {
        String content = render();
        return new FileWriter(path, () -> content);
    }

    /** Must be called (if there is a parent) before any other method. */
    void setParent(Template parent) { this.parent = parent; }

    Template addElement(String name) {
        var section = lists.get(name);
        if (section == null) {
            throw new NoSuchNameTemplateException(range, name);
        }
        return section.add();
    }

    Optional<String> getVariableValue(String name) {
        String value = values.get(name);
        if (value != null) return Optional.of(value);
        if (parent != null) return parent.getVariableValue(name);
        return Optional.empty();
    }
}
