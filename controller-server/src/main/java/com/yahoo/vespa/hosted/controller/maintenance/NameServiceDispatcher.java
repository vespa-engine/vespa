// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.dns.NameServiceQueue;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;

/**
 * This consumes requests from the {@link NameServiceQueue} by submitting them to a {@link NameService}. Successfully
 * consumed requests are removed from the queue.
 *
 * @author mpolden
 */
public class NameServiceDispatcher extends Maintainer {

    private static final int defaultRequestCount = 1;

    private final CuratorDb db;
    private final NameService nameService;
    private final int requestCount;

    public NameServiceDispatcher(Controller controller, Duration interval, JobControl jobControl,
                                 NameService nameService) {
        this(controller, interval, jobControl, nameService, defaultRequestCount);
    }

    public NameServiceDispatcher(Controller controller, Duration interval, JobControl jobControl,
                                 NameService nameService, int requestCount) {
        super(controller, interval, jobControl);
        this.db = controller.curator();
        this.nameService = nameService;
        this.requestCount = requestCount;
    }

    @Override
    protected void maintain() {
        try (Lock lock = db.lockNameServiceQueue()) {
            NameServiceQueue queue = db.readNameServiceQueue();
            NameServiceQueue remaining = queue.dispatchTo(nameService, requestCount);
            db.writeNameServiceQueue(remaining);
        }
    }

}
