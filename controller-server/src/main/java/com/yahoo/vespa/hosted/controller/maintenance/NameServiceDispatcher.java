// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.collections.Iterables;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.dns.NameServiceRequest;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
            if (queue.requests().isEmpty() || requestCount == 0) return 1.0;

            var instant = clock.instant();
            var remaining = queue.dispatchTo(nameService, requestCount);
            var dispatched = queue.requests().stream()
                                  .filter(new HashSet<>(remaining.requests())::remove)
                                  .toList();

            if (!dispatched.isEmpty()) {
                Level logLevel = controller().system().isCd() ? Level.INFO : Level.FINE;
                log.log(logLevel, () -> "Dispatched name service request(s) in " +
                                        Duration.between(instant, clock.instant()) +
                                        ": " + dispatched);
            }
            // TODO: release lock while performing, verify queue is prefix of new queue when writing (locked)
            db.writeNameServiceQueue(remaining);
            return dispatched.size() / (double) Math.min(requestCount, queue.requests().size());
        }
    }

    /** The true interval at which this runs in this cluster */
    private int trueIntervalInSeconds() {
        return (int) interval().dividedBy(db.cluster().size()).getSeconds();
    }

}
