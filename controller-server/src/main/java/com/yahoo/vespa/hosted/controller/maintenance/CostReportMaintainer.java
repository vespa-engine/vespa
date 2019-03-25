// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.inject.Inject;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostCalculator;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

/**
 * Periodically calculate and store cost allocation for properties.
 *
 * @author ldalves
 * @author andreer
 */
public class CostReportMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(CostReportMaintainer.class.getName());

    private final CostReportConsumer consumer;
    private final NodeRepositoryClientInterface nodeRepository;
    private final Clock clock;
    private final SelfHostedCostConfig selfHostedCostConfig;

    @Inject
    @SuppressWarnings("WeakerAccess")
    public CostReportMaintainer(Controller controller, Duration interval,
                                CostReportConsumer consumer,
                                JobControl jobControl,
                                NodeRepositoryClientInterface nodeRepository,
                                Clock clock,
                                SelfHostedCostConfig selfHostedCostConfig) {
        super(controller, interval, jobControl, "CostReportMaintainer", EnumSet.of(SystemName.main));
        this.consumer = consumer;
        this.nodeRepository = Objects.requireNonNull(nodeRepository, "node repository must be non-null");
        this.clock = clock;
        this.selfHostedCostConfig = selfHostedCostConfig;
    }

    @Override
    protected void maintain() {
        consumer.Consume(CostCalculator.resourceShareByPropertyToCsv(nodeRepository, controller(), clock, selfHostedCostConfig));
    }
}
