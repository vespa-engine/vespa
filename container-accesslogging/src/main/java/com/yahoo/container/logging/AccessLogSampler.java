// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Samples entries from access log. It first samples every query until it have some data, and then sub-samples
 * much less frequently to reduce CPU usage and latency impact. It only samples successful requests and requests
 * that starts with /search.
 *
 * @author dybis
 */
public class AccessLogSampler implements AccessLogInterface {

    private final AtomicLong accessLineCounter = new AtomicLong(0);
    private final CircularArrayAccessLogKeeper circularArrayAccessLogKeeper;

    public AccessLogSampler(CircularArrayAccessLogKeeper circularArrayAccessLogKeeper) {
        this.circularArrayAccessLogKeeper = circularArrayAccessLogKeeper;
    }

    @Override
    public void log(AccessLogEntry accessLogEntry) {
        if (accessLogEntry.getStatusCode() != 200) {
            return;
        }
	String uriString = accessLogEntry.getRawPath();
	if (! uriString.startsWith("/search")) {
            return;
	}
        final long count = accessLineCounter.incrementAndGet();
        if (count >= CircularArrayAccessLogKeeper.SIZE && count % CircularArrayAccessLogKeeper.SIZE  != 0) {
            return;
        }
        circularArrayAccessLogKeeper.addUri(uriString);
    }
}
