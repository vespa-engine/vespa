// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingReporter;

import java.time.Duration;
import java.util.Set;

public class BillingReportMaintainer extends ControllerMaintainer {

    private final BillingReporter reporter;

    public BillingReportMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, Set.of(SystemName.PublicCd));
        this.reporter = controller.serviceRegistry().billingReporter();
    }

    @Override
    protected double maintain() {
        return this.reporter.maintain();
    }
}
