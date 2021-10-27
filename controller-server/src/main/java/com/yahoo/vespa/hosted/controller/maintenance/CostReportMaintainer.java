// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.metric.CostCalculator;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

/**
 * Periodically calculate and store cost allocation for properties.
 *
 * @author ldalves
 * @author andreer
 */
public class CostReportMaintainer extends ControllerMaintainer {

    private final CostReportConsumer consumer;
    private final NodeRepository nodeRepository;
    private final Clock clock;

    public CostReportMaintainer(Controller controller, Duration interval, CostReportConsumer costReportConsumer) {
        super(controller, interval, null, EnumSet.of(SystemName.main));
        this.consumer = costReportConsumer;
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.clock = controller.clock();
    }

    @Override
    protected double maintain() {
        var csv = CostCalculator.resourceShareByPropertyToCsv(nodeRepository, controller(), clock, consumer.fixedAllocations());
        consumer.consume(csv);
        return 1.0;
    }

}
