// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

/**
 * This dispatches requests from {@link NameServiceQueue} to a {@link NameService}. Successfully dispatched requests are
 * removed from the queue.
 *
 * @author mpolden
 */
public class NameServiceDispatcher extends ControllerMaintainer {

    private final Clock clock;
    private final CuratorDb db;
    private final NameService nameService;

    NameServiceDispatcher(Controller controller, NameService nameService, Duration interval) {
        super(controller, interval);
        this.clock = controller.clock();
        this.db = controller.curator();
        this.nameService = nameService;
    }

    public NameServiceDispatcher(Controller controller, Duration interval) {
        this(controller, controller.serviceRegistry().nameService(), interval);
    }

    @Override
    protected double maintain() {
        // Dispatch 1 request per second on average. Note that this is not entirely accurate because a NameService
        // implementation may need to perform multiple API-specific requests to execute a single NameServiceRequest
        int requestCount = trueIntervalInSeconds();
        final NameServiceQueue initial;
        try (var lock = db.lockNameServiceQueue()) {
            initial = db.readNameServiceQueue();
        }
        if (initial.requests().isEmpty() || requestCount == 0) return 1.0;

        Instant instant = clock.instant();
        NameServiceQueue remaining = initial.dispatchTo(nameService, requestCount);
        NameServiceQueue dispatched = initial.without(remaining);

        if (!dispatched.requests().isEmpty()) {
            Level logLevel = controller().system().isCd() ? Level.INFO : Level.FINE;
            log.log(logLevel, () -> "Dispatched name service request(s) in " +
                                    Duration.between(instant, clock.instant()) +
                                    ": " + dispatched);
        }

        try (var lock = db.lockNameServiceQueue()) {
            db.writeNameServiceQueue(db.readNameServiceQueue().replace(initial, remaining));
        }
        return dispatched.requests().size() / (double) Math.min(requestCount, initial.requests().size());
    }

    /** The true interval at which this runs in this cluster */
    private int trueIntervalInSeconds() {
        return (int) interval().dividedBy(db.cluster().size()).getSeconds();
    }

}
