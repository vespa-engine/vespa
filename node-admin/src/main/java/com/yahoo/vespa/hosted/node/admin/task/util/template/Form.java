// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import java.util.Locale;

/**
 * Public methods common to both Template and ListElement.
 *
 * @author hakonhall
 */
public interface Form {
    /** Set the value of a variable, e.g. %{=color}. */
    Template set(String name, String value);

    /** Set the value of a variable and/or if-condition. */
    default Template set(String name, boolean value) { return set(name, Boolean.toString(value)); }

    default Template set(String name, int value) { return set(name, Integer.toString(value)); }
    default Template set(String name, long value) { return set(name, Long.toString(value)); }

    default Template set(String name, String format, Object first, Object... rest) {
        var args = new Object[1 + rest.length];
        args[0] = first;
        System.arraycopy(rest, 0, args, 1, rest.length);
        var value = String.format(Locale.US, format, args);

        return set(name, value);
    }

    /** Add an instance of a list section after any previously added (for the given name)  */
    ListElement add(String name);
}
