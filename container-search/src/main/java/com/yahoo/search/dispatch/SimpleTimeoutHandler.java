package com.yahoo.search.dispatch;

import com.yahoo.search.Query;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_TIMEOUT;

/**
 * Computes the timeout based solely on the query timeout
 *
 * @author baldersheim
 */
public class SimpleTimeoutHandler implements TimeoutHandler {
    private final Query query;
    SimpleTimeoutHandler(Query query) {
        this.query = query;
    }
    @Override
    public long nextTimeoutMS(int answeredNodes) {
            return query.getTimeLeft();
    }

    @Override
    public int reason() {
        return DEGRADED_BY_TIMEOUT;
    }
}
