// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.DynamicThrottlePolicy;
import com.yahoo.messagebus.Message;
import com.yahoo.vespa.http.server.MetricNames;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * An instrumented {@link DynamicThrottlePolicy} which provides observability through metrics.
 *
 * @author bjorncs
 * @see MetricNames#MBUS_WINDOW_SIZE
 * @see DynamicThrottlePolicy
 */
class InstrumentedThrottlePolicy extends DynamicThrottlePolicy {

    private final static Logger log = Logger.getLogger(InstrumentedThrottlePolicy.class.getName());
    private final AtomicInteger previousMaxPending = new AtomicInteger(Integer.MIN_VALUE);
    private final Metric metric;

    @Inject
    InstrumentedThrottlePolicy(Metric metric) {
        setResizeRate(2); // Increase the window size update rate by lowering resize rate...  ¯\_(ツ)_/¯
        this.metric = metric;
    }

    @Override
    public boolean canSend(Message message, int pendingCount) {
        // Invokes super.canSend() first as it updates the max pending count internally
        var canSend = super.canSend(message, pendingCount);
        var currentValue = getMaxPendingCount();
        var previousValue = this.previousMaxPending.getAndSet(currentValue);
        if (previousValue != currentValue) {
            metric.set(MetricNames.MBUS_WINDOW_SIZE, currentValue, null);
            log.fine(() -> "Max pending count updated from " + previousValue + " to " + currentValue);
        }
        return canSend;
    }
}
