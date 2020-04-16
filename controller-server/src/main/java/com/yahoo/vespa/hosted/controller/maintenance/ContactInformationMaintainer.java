// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    private final ContactRetriever contactRetriever;

    public ContactInformationMaintainer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.contactRetriever = controller.serviceRegistry().contactRetriever();
    }

    @Override
    protected void maintain() {
        TenantController tenants = controller().tenants();
        for (Tenant tenant : tenants.asList()) {
            try {
                switch (tenant.type()) {
                    case athenz: tenants.lockIfPresent(tenant.name(), LockedTenant.Athenz.class, lockedTenant ->
                            tenants.store(lockedTenant.with(contactRetriever.getContact(lockedTenant.get().propertyId()))));
                        return;
                    case cloud: return;
                    default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
                }
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to update contact information for " + tenant + ": " +
                                          Exceptions.toMessageString(e) + ". Retrying in " +
                                          maintenanceInterval());
            }
        }
    }

}
