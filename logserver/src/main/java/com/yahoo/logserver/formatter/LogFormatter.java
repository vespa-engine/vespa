// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.formatter;

import com.yahoo.log.LogMessage;

/**
 * This interface is analogous to the java.util.logging.Formatter
 * interface.  Classes implementing this interface should be
 * <b>stateless/immutable if possible so formatters can be
 * shared</b>.  If it does have state it must not prevent
 * concurrent use.
 *
 * @author Bjorn Borud
 */
public interface LogFormatter {
    /**
     * Format log message as a string.
     *
     * @param msg The log message
     */
    String format(LogMessage msg);

    /**
     * Returns a textual description of the formatter
     */
    String description();
}
