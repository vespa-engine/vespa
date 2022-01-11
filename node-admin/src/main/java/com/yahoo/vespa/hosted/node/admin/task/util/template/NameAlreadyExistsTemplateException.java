// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.Cursor;
import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

/**
 * @author hakonhall
 */
public class NameAlreadyExistsTemplateException extends TemplateException {
    public NameAlreadyExistsTemplateException(String name, CursorRange range) {
        super("Name '" + name + "' already exists in the " + describeSection(range));
    }

    public NameAlreadyExistsTemplateException(String name, Cursor firstNameLocation,
                                              Cursor secondNameLocation) {
        super("Section named '" + name + "' at " +
              firstNameLocation.calculateLocation().lineAndColumnText() +
              " conflicts with earlier section with the same name at " +
              secondNameLocation.calculateLocation().lineAndColumnText());
    }
}
