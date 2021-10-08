// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import java.util.concurrent.ThreadLocalRandom;

/**
 * When the gateways says it can not handle more load, we should send less load. That is the responsibility
 * of this component
 *
 * @author dybis
 */
public class GatewayThrottler {

    private long backOffTimeMs = 0;
    private final long maxSleepTimeMs;

    public GatewayThrottler(long maxSleepTimeMs) {
        this.maxSleepTimeMs = maxSleepTimeMs;
    }

    public void handleCall(int transientErrors) {
        if (transientErrors > 0) {
            backOffTimeMs = Math.min(maxSleepTimeMs, backOffTimeMs + distribute(100));
        } else {
            backOffTimeMs = Math.max(0, backOffTimeMs - distribute(10));
        }
        sleepMs(backOffTimeMs);
    }

    protected void sleepMs(long sleepTime) {
        try {
            if (backOffTimeMs > 0L) {
                Thread.sleep(backOffTimeMs);
            }
        } catch (InterruptedException e) {
            // Do nothing
        }
    }

    public int distribute(int expected) {
        double factor = 0.5 + ThreadLocalRandom.current().nextDouble();
        Double result = expected * factor;
        return result.intValue();
    }

}
