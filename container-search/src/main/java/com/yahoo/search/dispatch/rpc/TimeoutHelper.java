// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.search.Query;

/**
 * @author bjorncs
 */
class TimeoutHelper {

    private TimeoutHelper() {}

    static Timeout calculateTimeout(Query q) {
        long timeLeftMillis = q.getTimeLeft();
        if (timeLeftMillis <= 2) return new Timeout(0d, 0d);
        // The old timeout logic subtracted 3ms to timeout for client timeout.
        // 3ms equalled to 0.6% of the default query timeout (500ms).
        // This accounted for cost of network and container post-processing.
        // New logic subtracts 1% for client and 2% for content node (request).
        double clientTimeout = Math.max(timeLeftMillis * 0.99d, 2d) / 1000d;
        double requestTimeout = Math.max(timeLeftMillis * 0.98d, 1d) / 1000d;
        return new Timeout(requestTimeout, clientTimeout);
    }

    record Timeout(double request, double client) {
        public boolean timedOut() { return request == 0 && client == 0; }
    }
}
