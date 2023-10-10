// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogMessage;

/**
 * This interface is analogous to the java.util.logging.Filter
 * interface.  Classes implementing this interface should be
 * <b>stateless/immutable if possible so filters can be
 * shared</b>.
 *
 * @author Bjorn Borud
 */
public interface LogFilter {
    /**
     * Determine if this log message is loggable.
     *
     * @param msg The log message
     */
    public boolean isLoggable(LogMessage msg);

    public String description();
}
