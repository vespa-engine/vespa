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
