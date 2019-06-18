// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Billing;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

import java.time.Duration;
import java.util.EnumSet;

/**
 * @author olaa
 */
public class BillingMaintainer extends Maintainer {

    private final Billing billing;

    public BillingMaintainer(Controller controller, Duration interval, JobControl jobControl, Billing billing) {
        super(controller, interval, jobControl, BillingMaintainer.class.getSimpleName(), EnumSet.of(SystemName.cd));
        this.billing = billing;
    }

    @Override
    public void maintain() {
        controller().tenants().asList()
                .stream()
                .filter(tenant -> tenant instanceof CloudTenant)
                .map(tenant -> (CloudTenant) tenant)
                .forEach(cloudTenant -> controller().applications().asList(cloudTenant.name())
                        .stream()
                        .forEach( application -> {
                            billing.handleBilling(application.id(), cloudTenant.billingInfo().customerId());
                        })
                );
    }
}


