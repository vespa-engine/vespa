// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

/**
 * Computes next timeout in milliseconds
 *
 * @author baldersheim
 */
public interface TimeoutHandler {
    long nextTimeoutMS(int answeredNodes);

    /**
     * Return a bitmask from com.yahoo.container.handler.Coverage.DEGRADED.... set
     */
    int reason();
}
