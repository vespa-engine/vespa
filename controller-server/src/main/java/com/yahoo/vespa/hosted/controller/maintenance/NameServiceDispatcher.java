// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
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

    public NameServiceDispatcher(Controller controller, Duration interval) {
        super(controller, interval);
        this.clock = controller.clock();
        this.db = controller.curator();
        this.nameService = controller.serviceRegistry().nameService();
    }

    @Override
    protected double maintain() {
        // Dispatch 1 request per second on average. Note that this is not entirely accurate because a NameService
        // implementation may need to perform multiple API-specific requests to execute a single NameServiceRequest
        int requestCount = trueIntervalInSeconds();
        try (var lock = db.lockNameServiceQueue()) {
            var queue = db.readNameServiceQueue();
            var instant = clock.instant();
            var remaining = queue.dispatchTo(nameService, requestCount);
            if (queue.equals(remaining)) return 1.0; // Queue unchanged

            var dispatched = queue.first(requestCount);
            if (!dispatched.requests().isEmpty()) {
                Level logLevel = controller().system().isCd() ? Level.INFO : Level.FINE;
                log.log(logLevel, "Dispatched name service request(s) in " +
                                  Duration.between(instant, clock.instant()) +
                                  ": " + dispatched.requests());
            }
            db.writeNameServiceQueue(remaining);
        }
        return 1.0;
    }

    /** The true interval at which this runs in this cluster */
    private int trueIntervalInSeconds() {
        return (int) interval().dividedBy(db.cluster().size()).getSeconds();
    }

}
