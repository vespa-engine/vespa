// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;

/**
 * @author hakonhall
 */
public class TemplateNameNotSetException extends TemplateException {
    public TemplateNameNotSetException(Section section, String name, Cursor nameOffset) {
        super(name + " at " + nameOffset.calculateLocation().lineAndColumnText() + " has not been set");
    }
}
