// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.api.identifiers.PropertyId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    private final ContactRetriever contactRetriever;

    public ContactInformationMaintainer(Controller controller, Duration interval, JobControl jobControl, ContactRetriever contactRetriever) {
        super(controller, interval, jobControl, null, EnumSet.of(SystemName.cd, SystemName.main));
        this.contactRetriever = Objects.requireNonNull(contactRetriever, "organization must be non-null");
    }

    @Override
    protected void maintain() {
        for (Tenant tenant : controller().tenants().asList()) {
            try {
                Optional<PropertyId> tenantPropertyId = Optional.empty();
                if (tenant instanceof AthenzTenant) {
                    tenantPropertyId = ((AthenzTenant) tenant).propertyId();
                }
                Contact contact = contactRetriever.getContact(tenantPropertyId);
                controller().tenants().lockIfPresent(tenant.name(), lockedTenant -> controller().tenants().store(lockedTenant.with(contact)));
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to update contact information for " + tenant + ": " +
                                          Exceptions.toMessageString(e) + ". Retrying in " +
                                          maintenanceInterval());
            }
        }
    }


}
