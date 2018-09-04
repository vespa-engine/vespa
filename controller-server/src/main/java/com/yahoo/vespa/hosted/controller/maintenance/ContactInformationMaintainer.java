// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    public ContactInformationMaintainer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        for (Tenant t : controller().tenants().asList()) {
            if (!(t instanceof AthenzTenant)) continue; // No contact information for non-Athenz tenants
            AthenzTenant tenant = (AthenzTenant) t;
            if (!tenant.propertyId().isPresent()) continue; // Can only update contact information if property ID is known
            try {
                controller().tenants().findContact(tenant).ifPresent(contact -> {
                    controller().tenants().lockIfPresent(t.name(), lockedTenant -> controller().tenants().store(lockedTenant.with(contact)));
                });
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to update contact information for " + tenant + ": " +
                                          Exceptions.toMessageString(e) + ". Retrying in " +
                                          maintenanceInterval());
            }
        }
    }

}
