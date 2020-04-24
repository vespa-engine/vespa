// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import java.util.logging.Level;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;

/**
 * This consumes requests from the {@link NameServiceQueue} by submitting them to a {@link NameService}. Successfully
 * consumed requests are removed from the queue.
 *
 * @author mpolden
 */
public class NameServiceDispatcher extends Maintainer {

    private static final int defaultRequestCount = 1;

    private final Clock clock;
    private final CuratorDb db;
    private final NameService nameService;
    private final int requestCount;

    public NameServiceDispatcher(Controller controller, Duration interval, JobControl jobControl) {
        this(controller, interval, jobControl, defaultRequestCount);
    }

    public NameServiceDispatcher(Controller controller, Duration interval, JobControl jobControl, int requestCount) {
        super(controller, interval, jobControl);
        this.clock = controller.clock();
        this.db = controller.curator();
        this.nameService = controller.serviceRegistry().nameService();
        this.requestCount = requestCount;
    }

    @Override
    protected void maintain() {
        try (var lock = db.lockNameServiceQueue()) {
            var queue = db.readNameServiceQueue();
            var instant = clock.instant();
            var remaining = queue.dispatchTo(nameService, requestCount);
            if (queue == remaining) return; // Queue unchanged

            var dispatched = queue.last(requestCount);
            if (!dispatched.requests().isEmpty()) {
                log.log(LogLevel.INFO, "Dispatched name service request(s) in " +
                                       Duration.between(instant, clock.instant()) +
                                       ": " + dispatched.requests());
            }
            db.writeNameServiceQueue(remaining);
        }
    }

}
