// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Organization;
import com.yahoo.vespa.hosted.controller.api.integration.organization.User;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.Contact;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically fetch and store contact information for tenants.
 *
 * @author mpolden
 */
public class ContactInformationMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ContactInformationMaintainer.class.getName());

    private final Organization organization;

    public ContactInformationMaintainer(Controller controller, Duration interval, JobControl jobControl, Organization organization) {
        super(controller, interval, jobControl, null, EnumSet.of(SystemName.cd, SystemName.main));
        this.organization = Objects.requireNonNull(organization, "organization must be non-null");
    }

    @Override
    protected void maintain() {
        for (Tenant t : controller().tenants().asList()) {
            if (!(t instanceof AthenzTenant)) continue; // No contact information for non-Athenz tenants
            AthenzTenant tenant = (AthenzTenant) t;
            if (!tenant.propertyId().isPresent()) continue; // Can only update contact information if property ID is known
            try {
                findContact(tenant).ifPresent(contact -> {
                    controller().tenants().lockIfPresent(t.name(), lockedTenant -> controller().tenants().store(lockedTenant.with(contact)));
                });
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Failed to update contact information for " + tenant + ": " +
                                          Exceptions.toMessageString(e) + ". Retrying in " +
                                          maintenanceInterval());
            }
        }
    }

    /** Find contact information for given tenant */
    private Optional<Contact> findContact(AthenzTenant tenant) {
        if (!tenant.propertyId().isPresent()) {
            return Optional.empty();
        }
        List<List<String>> persons = organization.contactsFor(tenant.propertyId().get())
                                                 .stream()
                                                 .map(personList -> personList.stream()
                                                                              .map(User::displayName)
                                                                              .collect(Collectors.toList()))
                                                 .collect(Collectors.toList());
        return Optional.of(new Contact(organization.contactsUri(tenant.propertyId().get()),
                                       organization.propertyUri(tenant.propertyId().get()),
                                       organization.issueCreationUri(tenant.propertyId().get()),
                                       persons));
    }

}
