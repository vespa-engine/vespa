package com.yahoo.search.dispatch;

/**
 * Computes next timeout
 *
 * @author baldersheim
 */
public interface TimeoutHandler {
    long nextTimeout(int answeredNodes);
    int reason();
}
