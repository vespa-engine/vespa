// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.protect;

/**
 * Helper class to account for measuring how many queries times outs.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @deprecated this is not in use and will be removed in the next major release
 */
// TODO: Remove on Vespa 7
@Deprecated
public final class TimeoutRate {

    private int timeouts = 0;
    private int total = 0;

    public void addQuery(Boolean timeout) {
        if (timeout) {
            timeouts += 1;
        }
        total += 1;
    }

    public void merge(TimeoutRate other) {
        timeouts += other.timeouts;
        total += other.total;
    }

    public double timeoutFraction() {
        if (total == 0) {
            return 0.0d;
        } else {
            return ((double) timeouts) / ((double) total);
        }
    }

    public int getTotal() {
        return total;
    }

}
