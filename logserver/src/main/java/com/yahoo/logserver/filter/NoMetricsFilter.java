// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogMessage;

/**
 * This filter is the complement of MetricsFilter
 *
 * @author  Bjorn Borud
 */
public class NoMetricsFilter implements LogFilter {
    private final MetricsFilter filter = new MetricsFilter();

    public boolean isLoggable (LogMessage msg) {
        return (! filter.isLoggable(msg));
    }

    public String description () {
        return "Matches all log messages except Count and Value events";
    }
}
