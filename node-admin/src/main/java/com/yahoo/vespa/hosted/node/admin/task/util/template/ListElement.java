// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

/**
 * @author hakonhall
 */
public class ListElement implements Form {
    private final Template template;

    ListElement(Template template) { this.template = template; }

    @Override
    public Template set(String name, String value) { return template.set(name, value); }

    @Override
    public ListElement add(String name) { return new ListElement(template.addElement(name)); }
}
