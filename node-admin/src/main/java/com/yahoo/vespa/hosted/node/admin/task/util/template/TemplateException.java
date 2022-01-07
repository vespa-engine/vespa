// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.text.CursorRange;

/**
 * @author hakonhall
 */
public class TemplateException extends RuntimeException {
    public TemplateException(String message) { super(message); }

    protected static String describeSection(CursorRange range) {
        var startLocation = range.start().calculateLocation();
        var endLocation = range.end().calculateLocation();
        return "template section starting at line " + startLocation.line() + " and column " + startLocation.column() +
               ", and ending at line " + endLocation.line() + " and column " + endLocation.column();
    }
}
