// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Contact;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    private final ContactRetriever contactRetriever;

    public ContactInformationMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.contactRetriever = controller.serviceRegistry().contactRetriever();
    }

    @Override
    protected boolean maintain() {
        TenantController tenants = controller().tenants();
        boolean success = true;
        for (Tenant tenant : tenants.asList()) {
            log.log(FINE, "Updating contact information for " + tenant);
            try {
                switch (tenant.type()) {
                    case athenz:
                        tenants.lockIfPresent(tenant.name(), LockedTenant.Athenz.class, lockedTenant -> {
                            Contact contact = contactRetriever.getContact(lockedTenant.get().propertyId());
                            log.log(FINE, "Contact found for " + tenant + " was " +
                                          (Optional.of(contact).equals(tenant.contact()) ? "un" : "") + "changed");
                            tenants.store(lockedTenant.with(contact));
                        });
                        break;
                    case cloud:
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
                }
            } catch (Exception e) {
                success = false;
                log.log(Level.WARNING, "Failed to update contact information for " + tenant + ": " +
                                       Exceptions.toMessageString(e) + ". Retrying in " +
                                       interval());
            }
        }
        return success;
    }

}
