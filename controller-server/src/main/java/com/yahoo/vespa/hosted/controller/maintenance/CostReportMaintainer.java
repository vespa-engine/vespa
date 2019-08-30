// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.inject.Inject;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostCalculator;
import com.yahoo.vespa.hosted.controller.restapi.cost.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

/**
 * Periodically calculate and store cost allocation for properties.
 *
 * @author ldalves
 * @author andreer
 */
public class CostReportMaintainer extends Maintainer {

    private final CostReportConsumer consumer;
    private final NodeRepository nodeRepository;
    private final Clock clock;
    private final SelfHostedCostConfig selfHostedCostConfig;

    @Inject
    @SuppressWarnings("WeakerAccess")
    public CostReportMaintainer(Controller controller, Duration interval,
                                CostReportConsumer consumer,
                                JobControl jobControl,
                                SelfHostedCostConfig selfHostedCostConfig) {
        super(controller, interval, jobControl, "CostReportMaintainer", EnumSet.of(SystemName.main));
        this.consumer = consumer;
        this.nodeRepository = controller.configServer().nodeRepository();
        this.clock = controller.clock();
        this.selfHostedCostConfig = selfHostedCostConfig;
    }

    @Override
    protected void maintain() {
        consumer.Consume(CostCalculator.resourceShareByPropertyToCsv(nodeRepository, controller(), clock, selfHostedCostConfig, CloudName.from("yahoo")));
    }

}
