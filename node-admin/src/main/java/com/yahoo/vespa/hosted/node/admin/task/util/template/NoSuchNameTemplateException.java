// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

/**
 * @author hakonhall
 */
public class NoSuchNameTemplateException extends TemplateException {
    public NoSuchNameTemplateException(Section section, String name) {
        super("No such element '" + name + "' in the " + describeSection(section.range()));
    }
}
