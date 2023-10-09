// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogMessage;

/**
 * Filter which always returns false.
 *
 * @author Bjorn Borud
 */
public class MuteFilter implements LogFilter {
    private static final MuteFilter instance = new MuteFilter();

    /**
     * Singleton, private constructor.
     */
    private MuteFilter() {}

    public static MuteFilter getInstance() {
        return instance;
    }

    public boolean isLoggable(LogMessage msg) {
        return false;
    }

    public String description() {
        return "Matches no messages.  Mute.";
    }
}
