// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.util.EnumSet;

/**
 * @author olaa
 */
public class BillingDatabaseMaintainer extends ControllerMaintainer {

    public BillingDatabaseMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, EnumSet.of(SystemName.PublicCd));
    }

    @Override
    protected double maintain() {
        controller().serviceRegistry().billingDatabase().maintain();
        return 1;
    }
}
