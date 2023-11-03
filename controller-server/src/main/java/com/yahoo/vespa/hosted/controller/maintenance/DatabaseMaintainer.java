// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;

import java.time.Duration;

/**
 * @author olaa
 */
public class DatabaseMaintainer extends ControllerMaintainer {

    private final ResourceDatabaseClient resourceDatabaseClient;
    private final BillingDatabaseClient billingDatabaseClient;

    public DatabaseMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(SystemName::isPublic));
        this.resourceDatabaseClient = controller.serviceRegistry().resourceDatabase();
        this.billingDatabaseClient = controller.serviceRegistry().billingDatabase();
    }

    @Override
    protected double maintain() {
        resourceDatabaseClient.refreshMaterializedView();
        if (controller().system() == SystemName.PublicCd)
            billingDatabaseClient.maintain();
        return 0.0;
    }
}
