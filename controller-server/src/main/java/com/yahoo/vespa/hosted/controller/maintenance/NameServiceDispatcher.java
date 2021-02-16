// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.util.logging.Level;

/**
 * This consumes requests from the {@link NameServiceQueue} by submitting them to a {@link NameService}. Successfully
 * consumed requests are removed from the queue.
 *
 * @author mpolden
 */
public class NameServiceDispatcher extends ControllerMaintainer {

    private final Clock clock;
    private final CuratorDb db;
    private final NameService nameService;
    private final int requestCount;

    public NameServiceDispatcher(Controller controller, Duration interval) {
        this(controller, interval, requestCount(controller.system()));
    }

    public NameServiceDispatcher(Controller controller, Duration interval, int requestCount) {
        super(controller, interval);
        this.clock = controller.clock();
        this.db = controller.curator();
        this.nameService = controller.serviceRegistry().nameService();
        this.requestCount = requestCount;
    }

    @Override
    protected boolean maintain() {
        boolean success = true;
        try (var lock = db.lockNameServiceQueue()) {
            var queue = db.readNameServiceQueue();
            var instant = clock.instant();
            var remaining = queue.dispatchTo(nameService, requestCount);
            if (queue == remaining) return success; // Queue unchanged

            var dispatched = queue.first(requestCount);
            if (!dispatched.requests().isEmpty()) {
                Level logLevel = controller().system().isCd() ? Level.INFO : Level.FINE;
                log.log(logLevel, "Dispatched name service request(s) in " +
                                  Duration.between(instant, clock.instant()) +
                                  ": " + dispatched.requests());
            }
            db.writeNameServiceQueue(remaining);
        }
        return success;
    }

    private static int requestCount(SystemName system) {
        // Dispatch more requests at a time in CD as integration tests expect reasonably quick DNS changes
        return system.isCd() ? 3 : 1;
    }

}
