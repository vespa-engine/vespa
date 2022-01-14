// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

/**
 * @author hakonhall
 */
public class NameAlreadyExistsTemplateException extends TemplateException {
    public NameAlreadyExistsTemplateException(String name, Section first, Section second) {
        super("The name '" + name + "' of the " + second.type() + " section at " +
              second.range().start().calculateLocation().lineAndColumnText() +
              " is in conflict with the identically named " + first.type() + " section at " +
              first.range().start().calculateLocation().lineAndColumnText());
    }
}
